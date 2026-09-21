# SCRUM-45 — validation API and negative-path integration evidence

Jira: [SCRUM-45](https://hxj04121.atlassian.net/browse/SCRUM-45),
“S2-M1.5 Complete API, negative-path and integration tests”.
The issue description was checked on 2026-09-21. It requires request/response
wiring, unknown-label, unavailable-active-rule-set, permission and old-version
negative paths, executable API/golden/architecture/integration tests, and CI/Jira
evidence aligned with the M2/M4/M5 contracts.

## Base and scope

The branch starts at `origin/main@c4c0bcf852fd71cedb3c3de363140a6755632a96`.
[PR #30](https://github.com/hxj04121-lab/FoodLabelFlow/pull/30) was merged on
2026-09-18, so SCRUM-44 is an integrated dependency, not a pending PR. Main also
contains the reviewed SCRUM-43 orchestration and PR #29's executable fixtures.

The implementation adds the three endpoints in
[the frozen 1.0.0 contract](../contracts/allergen-validation-api-v1.yaml):

| Endpoint | Behaviour |
| --- | --- |
| `GET /api/v1/allergens?jurisdictionCode=US` | Canonical, jurisdiction-scoped allergen entries from the allergen-owned application port |
| `POST /api/v1/label-versions/{labelVersionId}/validation-runs` | Strict `ruleSetVersionId` request; 201 plus Location for committed PASSED and completed FAILED runs |
| `GET /api/v1/validation-runs/{validationRunId}` | Persisted run and complete results; never re-evaluates today's input or rule set |

No Flyway migration, shared build/CI configuration, evaluator, fixture SQL,
repository signature, permission code or frozen HTTP schema is changed.
No asynchronous execution, retry/idempotency policy or frontend login flow is added.

## Boundary decisions

- `validateWithResults` owns the same run/results/synchronous-audit transaction
  as SCRUM-44. The existing `validate` signature delegates to that one flow while
  retaining its outer transaction for existing application callers. The HTTP
  controller receives the saved immutable run/results only after the Spring
  transaction proxy commits. It does not issue a fallible post-commit query to
  build the creation response. POST and GET use the same stable result-ID order.
- M5's internal actor, provenance and result persistence IDs do not become HTTP
  fields. `ranAt` is an RFC3339 UTC string; a null `summary` is omitted, preserving
  compatibility with the existing M3 parser. Input findings retain a null
  `ruleDefinitionId`. Successful resources are not wrapped in an envelope.
- Writes still authenticate and require canonical `LABEL.VALIDATE` through M4's
  existing bridge inside the application transaction. Both GETs use the same
  active-identity read policy as the existing M4 label GET. No new read permission
  is invented and removing write permission does not revoke an authenticated read.
  The existing identity advice is ordered before the API's unexpected-error
  handler so 401/403 retain M4's mapping across modules.
- Request parsing is strict and local to this endpoint: reject missing, blank,
  non-string or extra fields, duplicate keys, malformed JSON and trailing JSON.
  As with existing MVC endpoints, invalid binding can produce 400 before business
  authentication; the frozen contract does not specify precedence between
  simultaneous invalid-input and identity errors. No identity or writes are
  accepted through the body.
- Known application failures retain 400/404/409/422 codes. Unexpected exceptions
  (including configuration `IllegalArgumentException`s) are logged server-side and
  mapped to a safe 500 `INTERNAL_ERROR`, not disguised as bad client input, a 404,
  or an empty catalogue. All errors have `code`, `message`, `traceId`, `evidenceId`;
  no synthetic trace/evidence IDs are emitted.

## Executable acceptance

`ValidationApiMySqlTest` runs **49** cases and
`NegativeGoldenValidationApiMySqlTest` runs **4** cases. Each suite uses a real
random-port HTTP server and an isolated MySQL 8.4.11 Testcontainer with Flyway
V1–V2 and the unchanged M2 positive or negative SQL respectively. The suites have
no test-managed transaction; assertions after the HTTP response observe actual
commits and rollbacks, not mocked success or a preseeded validation output.

| Requirement | Verified evidence |
| --- | --- |
| API request/response contract | Direct JSON resources, exact fields and scalar types, omitted null summary, UTC time, Location, complete four-field errors and 19 invalid JSON/body cases |
| Positive golden truth | SOY, MILK, WHEAT and multi-item fixtures match exact rule IDs/codes/severity/flags/messages; all active rules execute, including exact non-derived passes |
| Negative golden truth | Missing declaration, UNMAPPED and AMBIGUOUS inputs return persisted 201 FAILED with exact golden findings and attributed input guards; the original retired rule-set returns 422 with no outputs |
| Authentication/permission | All three endpoints reject missing/unknown/inactive identities; unauthorized POST is 403 even for an unknown label; body-supplied actor fields are invalid |
| Resource/version/precondition guards | Unknown label/run 404; superseded/rejected/noncurrent-published labels and stale formula 409 even with incomplete composition; unusable/mismatched rule set, zero active definitions and incomplete current formula 422 |
| Atomic HTTP failure | Audit failure after its real insert and a real duplicate-key result batch failure return safe 500 and leave run/results/audit empty |
| Commit and read behaviour | Every created body matches committed rows and attributable audit; creation succeeds even if later repository reads fail; read failures return safe 500 rather than 404/empty data |
| Historical evidence | GET remains byte-for-JSON equivalent after label text/status/declarations, formula currentness, active rules and write permissions change; a persisted non-null summary is returned |
| Owner boundaries | New ArchUnit rule forbids validation web dependencies on infrastructure/JDBC/SQL; all prior cross-module, pure-contract, M4 identity and SCRUM-44 rollback/concurrency tests still pass |

## Verification and handoff

Verified locally on 2026-09-21 with Java 21 and the existing isolated Colima profile:

```sh
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  DOCKER_HOST=unix:///Users/huangxiangjia/.colima/scrum41-20260914/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -B -ntp -f backend/pom.xml verify
```

- Full backend and executable JAR build: **218 tests; 0 failures/errors/skips;
  BUILD SUCCESS**. This includes 53 new HTTP cases and one additional architecture
  rule over the 164-test merged baseline.
- `npm ci && npm run build` in `frontend`: passed; npm reported zero dependency
  vulnerabilities. The existing Vite large-chunk warning remains a non-failing
  build warning, not part of this backend slice.
- `git diff --check`: passed. PR CI must independently run backend, frontend,
  containers and security on the submitted head; its run link belongs in the
  PR/Jira handoff, rather than claiming a local test is CI evidence.

These are API-to-database end-to-end checks, not a claim of browser login or human
business acceptance. The current `frontend/src/api/labels.ts` shapes already match
the frozen response but do not attach M4 identity headers. M3/M4 must supply the
authenticated browser/gateway context for live UI use; this implementation does
not add a hard-coded default actor to bypass that boundary. Automated review and
green checks do not constitute owner approval, a merge, or a Jira Done transition.
