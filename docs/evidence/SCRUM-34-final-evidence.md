# SCRUM-34 — S2-M5.8 CI, Sonar/staging and evidence visibility

Jira: SCRUM-34
Scope: S2-M5.8
Observed: 2026-09-21 (Asia/Singapore)

## Verification binding

- Verified commit SHA: `7993da01a749ddd2e02dba3d843257c7d10aadd9`
- Base SHA: `7993da01a749ddd2e02dba3d843257c7d10aadd9` (`origin/main` at audit)
- Local verification used the task worktree after rebasing onto `origin/main`.
- No remote push or current-HEAD GitHub Actions run was performed in this
  local-only pass; no historical run is presented as current evidence.

## Dependency status

- SCRUM-29 / M5.6: READY — its merge commit `8aecc731a91929607ad8451412120fb85274ead0` is an ancestor of the verified main snapshot.
- SCRUM-33 / M5.5: SUPERSEDED ON MAIN — the attempted `493ff2f` API commit was not replayed because main already contains the later SCRUM-45 API integration (`5b6b172`); this avoids duplicate controllers and DTOs.
- SCRUM-35 / M5.7: BLOCKED — no SCRUM-35 completion commit is present in the verified main history. Existing architecture tests were executed; SCRUM-34 did not recreate the missing M5.7 implementation.

## Existing CI and changes

The main workflow already had backend, frontend, and containers jobs. The
backend job runs the canonical `mvn -B -ntp -f backend/pom.xml verify` command;
the frontend job runs `npm ci && npm run build`; the containers job runs
`docker compose config --quiet` and `docker compose build`. None uses
`continue-on-error: true`.

SCRUM-34 adds an `always()` `backend-test-reports` artifact upload covering
Surefire and Failsafe report directories, while ignoring absent directories
without hiding the verify exit status.

## Backend

Command:

```text
DOCKER_HOST=unix:///Users/shj/.docker/run/docker.sock mvn -B -ntp -f backend/pom.xml verify
```

Result: PASS — `Tests run: 218, Failures: 0, Errors: 0, Skipped: 0`,
`BUILD SUCCESS`.

The run compiled the current 46 test sources and executed validation domain,
golden, API, transaction/persistence, MySQL integration, OpenAPI, shared API
error, and ArchUnit suites through Surefire. There is no Failsafe configuration
in the current `backend/pom.xml`; all current tests are therefore executed by
Surefire during `verify`.

Key Surefire report counts from that run:

| Evidence slice | Test classes / tests | Failures | Errors | Skipped |
| --- | --- | ---: | ---: | ---: |
| Validation unit/contracts | 8 classes / 42 | 0 | 0 | 0 |
| Golden fixtures | 5 classes / 21 | 0 | 0 | 0 |
| Validation API/OpenAPI/error | 4 classes / 60 | 0 | 0 | 0 |
| Persistence/transaction integration | 4 classes / 32 | 0 | 0 | 0 |
| Architecture | 3 classes / 9 | 0 | 0 | 0 |

MySQL/Testcontainers: PASS — Testcontainers `1.21.3` started real
`mysql:8.4.11` containers during the run; Flyway validated the canonical V1-V3
migrations. No H2 fallback was used.

Architecture: PASS for the architecture tests currently present and executed.
SCRUM-35-specific implementation: BLOCKED ON SCRUM-35 if that issue means
additional rules not present on this main snapshot; SCRUM-34 did not recreate
those rules.

## Frontend

- `npm ci`: PASS — 169 packages installed, 0 vulnerabilities reported.
- `npm run build`: PASS — TypeScript build and Vite production build completed.
- Playwright validation-run browser acceptance: NOT_APPLICABLE — the current
  UI exposes the label/allergen foundation and client API helpers, but no
  validation-run UI flow is present. No screenshot or browser PASS is claimed.
  Owner/next step: the validation UI owner should add the flow, then run its
  Playwright acceptance against the real API.

## Containers and local smoke

- `DOCKER_HOST=unix:///Users/shj/.docker/run/docker.sock docker compose config
  --quiet`: PASS.
- `DOCKER_HOST=unix:///Users/shj/.docker/run/docker.sock docker compose build`:
  PASS — backend and frontend images built.
- Local container smoke: PASS — using non-conflicting local ports
  `BACKEND_HOST_PORT=18080` and `FRONTEND_HOST_PORT=15173`, the MySQL, backend,
  and frontend services were healthy; `/api/health` returned
  `{"status":"ok","database":"ok"}` and the frontend served its HTML.
- This local Docker smoke is not shared staging evidence.

## Sonar

Status: NOT_CONFIGURED
Owner: repository/project maintainers
Evidence: `sonar-project.properties` has source/test mappings, but no Sonar
server, project binding, or `SONAR_TOKEN`/`SONAR_HOST_URL` configuration was
available in this task environment, and no scan was run.
Next step: configure the repository Sonar connection and run a scan for the
exact pushed commit; record its URL and quality-gate result.

## Shared staging

Status: NOT_CONFIGURED
Owner: deployment/staging owner
Evidence: no shared staging URL, credentials, or deployment workflow was
available. Local Compose is explicitly excluded from this claim.
Next step: provide the shared deployment target and run the validation smoke
against the deployed commit.

## Remote CI and artifacts

- Current HEAD GitHub Actions URL: NOT_VERIFIED — this branch was not pushed
  or remotely triggered during this pass.
- Workflow artifact intended for the next remote run: `backend-test-reports`.
- Local Maven reports observed under `backend/target/surefire-reports/`; they
  are generated output and are not committed.
- No browser screenshots/traces were created for validation UI because that UI
  is not present.

## Persistence / scope checks

- JDBC decision record: `docs/architecture/JDBC_VS_JPA_DECISION.md`.
- JPA dependency added: NO.
- Hibernate added: NO.
- V1-V3 modified: NO.
- New migration: NO.
- Unrelated persistence refactor: NO.

## Final status

SCRUM-34: PARTIAL — BLOCKED by remote CI run visibility, Sonar configuration,
shared staging configuration, and the absent validation-run UI/browser flow.
The local backend/frontend/container gates and MySQL Testcontainers evidence
are real and reproducible; the blocked items are not marked PASS.
