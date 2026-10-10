# Team A Render deployment checklist

Use this checklist before enabling Team A → Team C delivery. This project has
no persistent queue, callback outbox, shared idempotency store, or distributed
slot lock configured.

## Before deployment

- Confirm the Team A commit to deploy and that the full Java 17 Maven test suite
  passes.
- Set `TEAM_A_SERVICE_TOKEN` in Render's secret environment settings. Without
  it, `/internal/v1/**` now returns HTTP 503; `/health` and `/ready` stay public.
  Keep the token out of source control, terminal transcripts, and shared logs.
- Do not change production environment settings as part of this code change.
  Treat callback delivery as disabled unless `TEAM_C_CALLBACK_ENABLED=true` is
  explicitly set.
- Before enabling callback retry/redelivery, have Team C confirm that the
  deployed callback route accepts `Idempotency-Key` and atomically deduplicates
  updates by that value (Team A sends the `decision_id`). The available Team C
  source snapshot does not implement or document this callback.
- Have Team C confirm the deployed route, body fields, auth behavior, accepted
  `failed_rules` representation, and whether only eligible results with a lease
  should be persisted.

## Callback environment configuration

| Variable | Required condition | Default / behavior |
|---|---|---|
| `TEAM_C_CALLBACK_ENABLED` | Explicitly enable only after contract confirmation | `false` |
| `TEAM_C_BASE_URL` | Required when callback is enabled | HTTPS origin only; no path suffix |
| `TEAM_C_CALLBACK_TOKEN` | Only if Team C requires bearer authentication | Optional; omitted when blank |
| `TEAM_C_CONNECT_TIMEOUT` | Optional tuning | `2s` |
| `TEAM_C_READ_TIMEOUT` | Optional tuning | `5s` |
| `TEAM_C_CALLBACK_MAX_ATTEMPTS` | Optional tuning | `3`, maximum `10` |
| `TEAM_C_CALLBACK_RETRY_BACKOFF` | Optional tuning | `250ms` |
| `TEAM_C_CALLBACK_IDEMPOTENCY_CONFIRMED` | Required for retries or operator redelivery | `false`; do not set true without receiver confirmation |
| `TEAM_C_CALLBACK_MAX_REDELIVERIES` | Optional bounded recovery budget | `1`, maximum `10` |

Do not copy secret values into this checklist. If callback delivery is enabled
without a valid HTTPS `TEAM_C_BASE_URL`, startup fails. The callback token is
never logged by Team A.

## After deployment

1. Check `GET /health` and verify `data.build_version` matches the intended
   commit. Check `GET /ready`.
2. Confirm a request to an internal route without bearer auth is rejected. If
   `TEAM_A_SERVICE_TOKEN` is configured, verify a wrong token returns 401 and
   the correct bearer token succeeds. Do not include the actual token in output.
3. In a Team C-approved test environment, create/register a test application
   and slot through the teams' approved workflows, then submit one eligible
   request with a real slot ID. Verify the exact Team C application record and
   decision/lease IDs after Team C returns 2xx.
4. Verify a 4xx callback response remains a permanent failure; verify 5xx and
   timeout retries only when receiver idempotency is confirmed. Test the
   operator retry endpoint only after confirming receiver-side deduplication.
5. Verify ineligible, conditional, missing-slot, and unavailable-slot cases do
   not allocate a lease or send a callback. Ask Team C to confirm whether these
   results should instead be persisted through a separately agreed no-lease
   contract.
6. Review sanitized logs and callback status without exposing student data or
   service tokens. A restart loses local callback status; an operator retry
   cannot recover a delivery from a previous process.

## Deployment limitations

- Callback delivery state is in memory and is not a durable outbox. A restart
  can lose pending/retryable deliveries.
- Queue, idempotency, decisions, slots, and leases are process-local. Render
  multi-instance deployment does not provide cross-instance coordination.
- Reliable cross-instance slot exclusivity and crash-safe redelivery require a
  shared durable queue/outbox and lock/idempotency infrastructure. Select and
  configure that infrastructure explicitly before claiming those guarantees.
- Do not manually redeliver ambiguous failures until Team C confirms atomic
  idempotency for the `Idempotency-Key` derived from `decision_id`.
