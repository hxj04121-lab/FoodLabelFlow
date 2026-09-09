# M5 integration-test support

This Sprint 1 slice provides a reusable MySQL Testcontainers fixture for
backend integration and API tests.

## Reusable fixture

Integration tests that require the canonical database extend
`MySqlIntegrationTestSupport`. The fixture starts MySQL 8.4.11 and supplies
the container JDBC properties to Spring Boot. Application startup then runs
the repository's canonical Flyway V1-V3 migrations.

The fixture intentionally uses MySQL rather than an in-memory fallback. New
tests must not replace the canonical database behavior with H2 or mocked SQL.

## Current coverage

- `FlywayIntegrationTest` verifies all three migrations, the 60-product seed,
  the empty impact baseline, and database connectivity.
- `HealthApiIntegrationTest` starts the real HTTP server on a random port and
  verifies `/api/health` reports both the application and migrated database as
  healthy.

As M1 and M4 endpoints are merged, their API integration tests can extend the
same fixture. End-to-end traceability remains dependent on those business
interfaces and is not claimed by this initial support slice.

## Run

Docker must be available because Testcontainers starts a real MySQL instance.

```bash
mvn -B -ntp -f backend/pom.xml verify
```

The same command is used by the GitHub Actions backend job.
