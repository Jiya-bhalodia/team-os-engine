# Team A OS Engine

Spring Boot service for eligibility evaluation, request queues, and exclusive
slot leases.

## Integration and operations

- [Team A consolidated handoff and integration contract](TEAM_A_HANDOFF.md)
- [OpenAPI contract](openapi.yaml)

The callback is disabled by default. Internal `/internal/v1/**` endpoints fail
closed with HTTP 503 if `TEAM_A_SERVICE_TOKEN` is not configured; `/health` and
`/ready` remain public. Callback retries and operator redelivery are enabled
only after Team C confirms atomic `Idempotency-Key` deduplication by decision ID.

## Local verification

```sh
mvn clean test
```

Use Java 17. See TEAM_A_HANDOFF.md for configuration and integration details.
