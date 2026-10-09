# Team A → Team C eligibility callback

Team A can optionally POST a completed decision to
`{TEAM_C_BASE_URL}/internal/v1/applications/{applicationId}/eligibility`.
The callback route previously returned HTTP 404 and must be reconfirmed by Team
C before use. The callback remains disabled by default. Do not enable it against
an unconfirmed or unavailable route.

## Payload and lease requirement

The JSON body uses snake_case:

```json
{
  "request_id": "REQ-example",
  "decision_id": "DEC-example",
  "result": "ELIGIBLE",
  "rule_set_version": "v1",
  "failed_rules": [],
  "lease_id": "LEASE-example"
}
```

Team C's documented callback contract requires `lease_id`. Team A only sends a
callback when a real, nonblank lease ID exists. If a completed decision has no
lease, Team A does not send a malformed callback or invent a lease. It records
the in-memory status `BLOCKED_MISSING_LEASE` and logs the sanitized reason. The
eligibility result is not changed and no lease is acquired to satisfy the
callback. Team C and Team A still need an agreed handling path for no-lease
decisions before enabling delivery.

`failed_rules` is sent unchanged as an array of human-readable rule failure
messages. Team C has agreed to accept those messages; Team A does not transform
them into IDs. The supported `result` values are `ELIGIBLE`, `CONDITIONAL`, and
`NOT_ELIGIBLE`. No student snapshot is sent.

## Authentication

When enabled, Team A sends `Content-Type: application/json` and includes the
originating `X-Correlation-ID` when present. Team C accepts direct unauthenticated
requests, so `TEAM_C_CALLBACK_TOKEN` is optional. If it is nonblank, Team A also
sends `Authorization: Bearer <TEAM_C_CALLBACK_TOKEN>`; if blank or missing, Team A
omits the `Authorization` header. Keep any configured token in a secret store,
never in source control.

## Delivery and reliability

The client serializes each decision once and reuses the identical payload for
retries. It retries timeouts/transport errors and HTTP 5xx up to the configured
attempt limit. It does not retry HTTP 4xx. Duplicate decision IDs are suppressed
within the current process; reuse of a decision ID with changed serialized
payload is rejected.

Delivery tracking is in memory only. Delivery is best effort, is not crash-safe,
and is neither exactly-once nor durable: a Team A restart can lose pending
callbacks, and exhausted failures are not retried later automatically.

## Configuration required before enabling

- `TEAM_C_CALLBACK_ENABLED=false` by default; keep false until Team C confirms the route and no-lease handling.
- `TEAM_C_BASE_URL` — confirmed Team C HTTPS base URL, without the callback path.
- `TEAM_C_CALLBACK_TOKEN` — optional bearer secret; if supplied, share securely and never commit it.
- `TEAM_C_CONNECT_TIMEOUT=2s` by default.
- `TEAM_C_READ_TIMEOUT=5s` by default.
- `TEAM_C_CALLBACK_MAX_ATTEMPTS=3` by default; maximum `10`.
- `TEAM_C_CALLBACK_RETRY_BACKOFF=250ms` by default.

If delivery is enabled, the base URL is required. The token is optional.
Non-HTTPS URLs are rejected except localhost URLs used by tests.

## Safe testing sequence

1. Team C confirms the deployed callback route, bearer-auth behavior, required
   payload fields, and how no-lease decisions should be handled.
2. Team C provides an approved test application that has a genuine lease and
   confirms how to inspect the persisted callback result.
3. Configure Team A against the confirmed non-production/test endpoint and a
   securely provided test token, keeping production delivery disabled.
4. Send one approved test decision, verify Team C's response and persisted
   record, then verify duplicate suppression and retry behavior in the local
   tests. Do not use a fabricated lease or an unapproved production application.
5. Enable production delivery only after the test and contract are approved.

## Team A slot lifecycle and local smoke request

`slot_id` remains optional on `POST /api/v1/eligibility/requests` for existing
callers. For an eligible request that needs a Team C callback, the caller must
register a slot in the same running Team A process and include that exact
`slot_id`. Slot registration and leases are in memory, so a restart clears
them. The lock manager atomically rejects an unknown or unavailable slot during
acquisition. Team A does not fabricate a lease or send the callback when that
acquisition fails. Ineligible and conditional results do not allocate slots;
their callbacks are skipped because Team C's current callback contract requires
`lease_id`.

Register a test slot first (include the Authorization header only when
`TEAM_A_SERVICE_TOKEN` is configured):

```sh
curl -i -X POST "http://localhost:8000/internal/v1/slots" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: corr-slot-smoke-1" \
  -d '{"slot_id":"SLOT-SMOKE-1","drive_id":"DRIVE-SMOKE-1","date":"2026-10-10","start_time":"10:00","end_time":"10:30","capacity":1,"state":"AVAILABLE"}'
```

Then submit an eligible request using the registered slot (replace the
idempotency key for each new request):

```sh
curl -i -X POST "http://localhost:8000/api/v1/eligibility/requests" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: smoke-eligible-slot-001" \
  -H "X-Correlation-ID: corr-eligibility-smoke-1" \
  -d '{"application_id":"APP-SMOKE-1","student_id":"STU-SMOKE-1","drive_id":"DRIVE-SMOKE-1","rule_set_version":"v1","priority":1,"student":{"student_id":"STU-SMOKE-1","cgpa":8.5,"backlogs":0,"attendance_pct":90,"branch":"CSE","skills":["Java"]},"rule_set":{"version":"v1","rules":[{"rule_id":"R1","rule_type":"min_cgpa","threshold":7.5},{"rule_id":"R2","rule_type":"max_backlogs","threshold":0}]},"chaining_strategy":"sequential_and","slot_id":"SLOT-SMOKE-1","slot_lease_ttl_seconds":300}'
```

If service authentication is enabled, add `-H "Authorization: Bearer $TEAM_A_SERVICE_TOKEN"`
to both commands; do not paste the token into shared logs or source files. To
confirm which build is running, inspect `data.build_version` in `GET /health`.
On Render, this uses the non-secret `RENDER_GIT_COMMIT` value and falls back to
`unknown` when unavailable.
