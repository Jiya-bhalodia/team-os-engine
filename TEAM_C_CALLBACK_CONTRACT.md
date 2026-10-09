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

When enabled, Team A sends `Authorization: Bearer <TEAM_C_CALLBACK_TOKEN>` and
`Content-Type: application/json`; it includes the originating `X-Correlation-ID`
when present. Bearer-token support still needs confirmation from Team C's
deployed callback handler. Team A does not assume the currently deployed route
supports this authentication. Keep delivery disabled until Team C confirms the
required auth scheme and shares a token through a secure channel.

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

- `TEAM_C_CALLBACK_ENABLED=false` by default; keep false until Team C confirms the route, auth, and no-lease handling.
- `TEAM_C_BASE_URL` — confirmed Team C HTTPS base URL, without the callback path.
- `TEAM_C_CALLBACK_TOKEN` — bearer secret shared securely by Team C; never commit it.
- `TEAM_C_CONNECT_TIMEOUT=2s` by default.
- `TEAM_C_READ_TIMEOUT=5s` by default.
- `TEAM_C_CALLBACK_MAX_ATTEMPTS=3` by default; maximum `10`.
- `TEAM_C_CALLBACK_RETRY_BACKOFF=250ms` by default.

If delivery is enabled, the base URL and token are required. Non-HTTPS URLs are
rejected except localhost URLs used by tests.

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
