# Team A OS Engine Handoff

## Purpose and integration boundary

Team A is the Operating Systems eligibility engine. It accepts a versioned
student snapshot and drive rule set, queues and evaluates the request, manages
an optional exclusive interview-slot lease, and exposes lifecycle state and
runtime metrics.

The current Team A code does **not** connect to or write directly into Team C's
database. It has no database driver or database repository. When enabled, its
`TeamCCallbackClient` sends an HTTPS JSON `POST` to Team C's application
eligibility route. Team C must persist the accepted result in its own database.
One production happy-path persistence result has been reported by the teams;
the Team C source snapshot available during this handoff does not contain the
callback route, so that deployed implementation and all callback cases cannot
be verified from this repository. This handoff is the consolidated Team A
reference; callback behavior below is based on Team A source and configuration.

## Team A responsibilities and implementation

### A1 — Request intake and queue management

`EligibilityController` accepts `POST /api/v1/eligibility/requests`. It requires
`Idempotency-Key`, accepts or generates `X-Correlation-ID`, validates the
request snapshot and rule set, and returns `201` for a new queued request or
`200` for an identical idempotent replay. `QueueService` holds requests and
idempotency records in process memory. Queue implementations are in
`algorithm/queue`: FIFO, circular, priority, and heap. The configured default
is heap. `GET /api/v1/eligibility/requests/{requestId}` reads lifecycle status;
`GET /api/v1/drives/{driveId}/queue` returns drive queue depth and strategy.

### A2 — Rule-chain evaluation and decision policy

`EligibilityLifecycleWorker` dequeues requests and calls `EligibilityService`.
`RuleSetValidator` validates supported rule types and required values;
`RuleHelper` evaluates rules. Strategies are `SequentialAndStrategy`,
`WeightedPriorityStrategy`, and `DecisionTreeStrategy`. Decisions include a
decision ID, result (`ELIGIBLE`, `CONDITIONAL`, or `NOT_ELIGIBLE`), rule-set
version, failed-rule messages, and evaluation metrics. The internal direct
evaluation endpoint is `POST /internal/v1/rules/evaluate`; completed lifecycle
decisions are read at `GET /api/v1/eligibility/decisions/{decisionId}`.

### A3 — Concurrency, slot allocation, locks, and leases

`SlotController` registers slots through `POST /internal/v1/slots`.
`LockController` exposes lock acquisition, release, commit, and deadlock
analysis. `LockManager` uses in-process mutexes/semaphores, tracks leases and
expiry, and uses `WaitForGraph` for deadlock demonstrations. The lifecycle
worker only attempts a slot lease for an eligible request that includes a
registered `slot_id`; it stores the genuine returned lease ID. Ineligible and
conditional results do not acquire a lease. A missing or unavailable slot does
not produce a fabricated lease.

### A4 — Runtime telemetry and monitoring

`TelemetryService` counts queueing, evaluations, outcomes, failures, lock
attempts/conflicts, slot allocations, and wait/evaluation times.
`MetricsController` exposes `GET /api/v1/metrics/eligibility` and the
Server-Sent Events stream `GET /api/v1/stream/eligibility`. `HealthController`
provides `GET /health` and `GET /ready`.

## Request, decision, and Team C flow

1. Team C (or another authorized caller) submits an application ID, student
   ID, drive ID, rule-set version, student snapshot, rule set, strategy/policy
   options, and optionally a slot ID to Team A. JSON property names are
   snake_case; legacy aliases are supported where declared by the DTOs.
2. Team A validates and enqueues the request, then the scheduled lifecycle
   worker evaluates it and records its decision in Team A's in-memory stores.
3. An ineligible result completes without slot allocation. A conditional
   result completes without slot allocation. An eligible result attempts a
   lease only if a slot ID was supplied and registered in that Team A process.
4. If callback delivery is enabled, Team A sends the completed decision to
   `POST {TEAM_C_BASE_URL}/internal/v1/applications/{encoded_application_id}/eligibility`.
   The current callback is sent only for an eligible result with a genuine
   acquired lease. It includes `request_id`, `decision_id`, `result`,
   `rule_set_version`, `failed_rules` (human-readable messages), and `lease_id`.
5. Team C's route must validate and persist the result in Team C's database.
   Team A does not execute Team C database writes and cannot make the two
   services' updates atomic. **The current callback does not send ineligible,
   conditional, missing-slot, or failed-acquisition outcomes**, so those
   outcomes are not persisted to Team C by this callback implementation.

The callback is disabled by default. The reported live happy path is evidence
for one eligible result only. The available Team C source snapshot does not
document/implement this callback, and receiver-side idempotency and persistence
of non-empty human-readable `failed_rules` remain unverified. Do not treat
these assumptions as confirmed without Team C's deployed contract and tests.

### Callback delivery contract in Team A

- Delivery runs asynchronously on a two-thread executor. Team A trims trailing
  slashes from `TEAM_C_BASE_URL` and URL-encodes `application_id` in the path.
  The body is snake_case and contains `request_id`, `decision_id`, `result`,
  `rule_set_version`, `failed_rules`, and `lease_id`. `failed_rules` is sent
  unchanged as human-readable strings. Team C's acceptance of these strings
  has been reported by the teams, but non-empty message persistence has not
  been verified against the available Team C implementation.
- Each request includes `Content-Type: application/json` and
  `Idempotency-Key: {decision_id}`; `X-Correlation-ID` is forwarded when
  present. `Authorization: Bearer <TEAM_C_CALLBACK_TOKEN>` is included only
  when the optional token is nonblank.
- A 2xx response marks the delivery successful. A 4xx is permanent. 5xx,
  timeout, and transport failures are ambiguous: unless
  `TEAM_C_CALLBACK_IDEMPOTENCY_CONFIRMED=true`, Team A makes only one attempt.
  After receiver-side idempotency is confirmed, it retries with the same body
  and key up to `TEAM_C_CALLBACK_MAX_ATTEMPTS`, with the configured backoff.
  A bounded operator redelivery also requires that confirmation.
- Team A suppresses duplicate decision IDs within the current process and
  rejects reusing an ID with a changed application or payload. This is not
  receiver-side deduplication and does not survive restart.
- `GET /internal/v1/callbacks/{decisionId}` reports current-process delivery
  status; `POST /internal/v1/callbacks/{decisionId}/retry` requests a bounded
  redelivery. Both endpoints are internal and require service authentication.
- When enabled, `TEAM_C_BASE_URL` must be a valid HTTPS origin; localhost HTTP
  is permitted for tests. Missing/invalid configuration prevents startup.
  Connect and read timeouts default to 2s and 5s.

## Setup and configuration

Prerequisites: Java 17 and Maven. Spring Boot is 3.2.3. From the repository
root:

```sh
mvn clean test
mvn spring-boot:run
```

The default local port is `8000`; `PORT` overrides it (for example, `PORT=8000`).
The application can also be packaged with `mvn clean package` and run from the
generated JAR under `target/`.

| Environment variable | Default | Purpose |
|---|---|---|
| `PORT` | `8000` | HTTP listen port |
| `RENDER_GIT_COMMIT` | `unknown` | Build identifier exposed by `/health` |
| `TEAM_A_SERVICE_TOKEN` | empty | Inbound shared Bearer credential. If set, all routes except `/health` and `/ready` require `Authorization: Bearer <token>`. When empty, `/internal/v1/**` fails closed with HTTP 503; public API routes remain unauthenticated. |
| `TEAM_C_CALLBACK_ENABLED` | `false` | Enables Team A's outbound callback |
| `TEAM_C_BASE_URL` | empty | Team C HTTPS origin, without callback path; required if callback is enabled |
| `TEAM_C_CALLBACK_TOKEN` | empty | Optional outbound Bearer token; omitted if blank |
| `TEAM_C_CONNECT_TIMEOUT` | `2s` | Outbound connection timeout |
| `TEAM_C_READ_TIMEOUT` | `5s` | Outbound request timeout |
| `TEAM_C_CALLBACK_MAX_ATTEMPTS` | `3` | Attempt limit (1–10); ambiguous retries are used only with receiver idempotency confirmed |
| `TEAM_C_CALLBACK_RETRY_BACKOFF` | `250ms` | Delay between attempts |
| `TEAM_C_CALLBACK_IDEMPOTENCY_CONFIRMED` | `false` | Must remain false until Team C confirms atomic deduplication by `Idempotency-Key` |
| `TEAM_C_CALLBACK_MAX_REDELIVERIES` | `1` | Maximum operator-triggered additional delivery rounds (0–10) |

Configure credentials in the hosting provider's secret environment settings;
never put actual token values in this document, source control, or shared logs.
Health and readiness are public and return an API response envelope. Readiness
reports the selected queue strategy; it does not establish Team C connectivity.

## API and contract references

The authoritative Team A endpoint schemas are in [openapi.yaml](openapi.yaml).
Most JSON uses the common `data` / `meta` success envelope and `error` / `meta`
error envelope. Cross-service JSON property names are snake_case. Send
`X-Correlation-ID` for trace continuity; Team A generates one when absent on
eligibility intake. The create request requires `Idempotency-Key`.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/health` | Public health and build version |
| `GET` | `/ready` | Public readiness and queue strategy |
| `POST` | `/api/v1/eligibility/requests` | Validate, enqueue, and return initial status |
| `GET` | `/api/v1/eligibility/requests/{requestId}` | Read queued/processing/completed lifecycle status |
| `GET` | `/api/v1/eligibility/decisions/{decisionId}` | Read an in-memory decision |
| `GET` | `/api/v1/drives/{driveId}/queue` | Read queue depth and strategy for a drive |
| `POST` | `/internal/v1/rules/evaluate` | Evaluate a rule set directly |
| `POST` | `/internal/v1/slots` | Register an interview slot |
| `POST` | `/internal/v1/locks/acquire` | Acquire an exclusive slot lease |
| `DELETE` | `/internal/v1/locks/{leaseId}` | Release a lease |
| `POST` | `/internal/v1/locks/{leaseId}/commit` | Commit a lease in Team A's lock manager |
| `POST` | `/internal/v1/deadlocks/analyse` | Analyze wait-for edges/deadlock cycle |
| `GET` | `/api/v1/metrics/eligibility` | Read runtime metrics |
| `GET` | `/api/v1/stream/eligibility` | Subscribe to metric updates over SSE |
| `GET` / `POST` | `/api/v1/demo/...` | Run/read batch and deadlock demonstrations |
| `GET` | `/internal/v1/callbacks/{decisionId}` | Read current-process callback status |
| `POST` | `/internal/v1/callbacks/{decisionId}/retry` | Request a bounded callback redelivery |

Internal endpoints require the configured Team A Bearer token. The callback
status/redelivery endpoints are also internal. This file is the consolidated
handoff and callback-contract reference.

## Testing and verification

Run the full test suite from the repository root:

```sh
mvn clean test
```

The tests cover API/JSON contract behavior, validation and eligibility rule
regressions, lifecycle and slot acquisition, locking/deadlocks, callback
success/failure/idempotency gates, service authentication, demos, and telemetry
benchmarks. Exact results for this handoff are recorded below only after the
command has run successfully in this task. A passing local suite does not prove
the deployed Team C route or its database behavior.

Verification on 2026-10-10: `mvn clean test` did not reach compilation or test
execution. Maven failed while cleaning the existing `target/` directory
(`Failed to delete .../target`). A follow-up `mvn test` also stopped before
compilation because Maven could not write `target/classes/application.properties`
(`Operation not permitted`). No test cases ran, so no pass/fail counts are
available for this handoff.

## Known limitations and unverified behavior

- Queue, request/idempotency records, decisions, registered slots, active
  leases, telemetry counters, and callback-delivery status are process-local.
  A restart loses them; separate instances do not coordinate them.
- Slot exclusivity is enforced only within one Team A process. This is not a
  distributed lock guarantee.
- Callback delivery is not a durable outbox. A restart can lose pending or
  retryable delivery state. The receiver must atomically deduplicate
  `Idempotency-Key` before ambiguous timeout/5xx retries or manual redelivery
  are enabled.
- The callback currently requires an eligible decision with a genuine lease.
  The code does not persist ineligible/conditional results to Team C through
  this route. Whether Team C requires those outcomes must be confirmed.
- Team C's deployed callback route, receiver deduplication, storage of
  human-readable failure messages, and failure/compensation behavior are not
  verifiable from the Team C source snapshot available during this handoff.
- No cross-service atomic transaction is implemented. Team A cannot guarantee
  that Team C's database commit and Team A's lease state change together.

## Combined repository handoff

Include Team A's `pom.xml`, `src/`, `openapi.yaml`, `Dockerfile`,
`.dockerignore`, `.gitignore`, this handoff, and `README.md`. This document is
the single consolidated Team A handoff and callback-contract reference. Keep Team A's
package namespace (`com.placement.teama`) and its service configuration
separate from Team C's database configuration. In the combined deployment,
provide `PORT` and a securely managed `TEAM_A_SERVICE_TOKEN`; only configure
the callback variables after Team C confirms the deployed endpoint and
idempotency contract. Do not copy database credentials or service tokens into
Git.

### Pre-merge checklist

- [ ] Include the complete Team A source, tests, OpenAPI spec, Dockerfile, and
      handoff/configuration documents.
- [ ] Confirm Java 17 / Maven build and run `mvn clean test` in the combined
      repository.
- [ ] Validate the OpenAPI document and verify the documented routes match
      the combined build.
- [ ] Configure `TEAM_A_SERVICE_TOKEN` securely and verify health/readiness
      plus authenticated internal access.
- [ ] Agree with Team C on the callback route, human-readable `failed_rules`,
      outcomes requiring persistence, receiver idempotency, and failure
      compensation before enabling callback retries.
- [ ] Exercise a real leased eligible case and the agreed duplicate, timeout,
      slot-unavailable, and commit-failure cases in an approved test
      environment.
- [ ] Do not claim durable queueing, cross-instance locking, or crash-safe
      callback delivery unless shared infrastructure and tests provide them.
