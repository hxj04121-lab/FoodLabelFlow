# M5 integration-test support

This Sprint 1 slice provides reusable MySQL Testcontainers support and a
verified full-stack formula lifecycle from the browser to the database.

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
- `FormulaLifecycleEndToEndTest` sends real HTTP requests through the M4
  identity/RBAC adapter to create and release a formula, reads its history and
  trace, and verifies the product pointer, immutable versions, items, and audit
  events in MySQL.
- `formula-lifecycle-fullstack.spec.ts` drives the production frontend in
  Docker to create a draft, publish it, and display the history returned by the
  backend.

The M1 catalog integration now delegates authentication and authorization to
M4 using the request identity headers and writes catalog audit events in the
same transaction as each mutation. The verified lifecycle is:

`frontend -> catalog REST API -> identity/RBAC -> MySQL -> history response`

## Run

Docker must be available because Testcontainers starts a real MySQL instance.

```bash
mvn -B -ntp -f backend/pom.xml verify
```

The same command is used by the GitHub Actions backend job.

To verify the production containers and browser workflow:

```bash
docker compose up -d --build
cd frontend
npx playwright install chromium
PLAYWRIGHT_BASE_URL=http://127.0.0.1:5173 npm run test:e2e
```

Verified locally with MySQL 8.4.11: 27 backend tests and 13 Playwright tests
passed. The browser lifecycle also produced released formula history, updated
`product.current_formula_version_id`, and matching `FORMULA_CREATED` and
`FORMULA_RELEASED` audit events.
