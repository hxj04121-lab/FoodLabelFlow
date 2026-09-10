# M2 S1 cross-module contract closure evidence

Evidence date: 2026-09-10 (Asia/Shanghai)

Owner: M2 Cai Runchen

Audited `origin/main`: `a3bb498bb565e4f53d8161ba837f833d6bb9167b`

## Scope and sources checked

- M2: `docs/contracts/allergen-validation-api-v1.yaml`, S1 SAD,
  analysis-to-design trace, and acceptance/test mapping.
- M1: merged supplier/material/specification/product/formula controllers, commands,
  store, error advice, API handoff, unit tests, and MySQL integration test.
- M4: merged identity authorization and workflow/maker-checker services and tests.
- M5: merged identity repository, reusable MySQL Testcontainers support, API health
  test, workflow integration test, and CI workflow.
- Shared baseline: Flyway V1/V2/V3, architecture test, frontend API consumer, and the
  GitHub PR/CI records for PRs #3, #4, #6, and #7.

## Compatibility matrix

| Concern | Observed implementation | Canonical compatibility decision |
| --- | --- | --- |
| Field naming | M1 request records use `camelCase`; its raw JDBC success rows use `snake_case`. M2 DTOs use `camelCase`. | New request and cross-module DTO fields use `camelCase`. M1 success rows remain an explicit legacy/module-specific exception because M3 consumes them; no error field uses `snake_case`. |
| ID/version naming | M1 uses resource-specific IDs and server-assigned `version_number`; M2 uses `labelVersionId`, `ruleSetVersionId`, and `validationRunId`. | Resource-specific IDs are retained. The HTTP adapter maps persistence names to DTO names; no generic `id` or client-assigned version is introduced. |
| Pagination/search | M1 lists use `limit`/`offset`, default 50, maximum 100; M2 allergen lookup is jurisdiction-bounded and has no search. | Existing endpoint-specific list semantics remain. Pagination is not invented for a bounded canonical lookup; future unbounded lists use M1's `limit`/`offset` convention unless a reviewed contract changes it. |
| Request body | M1 and M2 accept direct JSON DTOs with server-assigned IDs omitted. | Direct JSON DTO, `camelCase`, unknown/invalid input mapped to 400. No trusted actor ID in a body. |
| Success response | M1 and M2 return the resource/list directly; create is 201 and reads/releases are 200. | No generic success envelope is added. |
| Error response | M1 emitted only `code/message`; M2 required `code/message/traceId/evidenceId`. | All web adapters use exactly the canonical four-field `ApiError`; the two identifiers are present and nullable. |
| Validation errors | M1 uses 400 `INVALID_REQUEST` for request validation and 422 domain-specific specification preconditions; M2 uses 422 `VALIDATION_PRECONDITION_FAILED`. | 400 is malformed/invalid input; 422 is a syntactically valid request blocked by domain preconditions. Messages are safe but non-contractual. |
| HTTP statuses | M1 implements 400/403/404/409/422/503; M2 documented 400/403/404/409/422/500. | Added shared 401. Common 400/403/404/409/422 meanings are fixed; 500/503 do not expose internal details. |
| Authentication/authorization | M5 raises `UnknownIdentityException`; M4 raises `AuthorizationDeniedException`. Neither had a web mapping. | M5 unknown/inactive identity maps to 401 `AUTHENTICATION_REQUIRED`; an authenticated actor lacking permission maps to 403 `AUTHORIZATION_DENIED`. |
| Duplicate submission | M1 duplicate business key is 409 `DATA_CONFLICT`; re-releasing an immutable version is 409 `VERSION_IMMUTABLE`. M4 guards invalid transitions in persistence. | Duplicate/idempotency conflicts are 409 with a domain-specific code. No database exception text is returned. |
| Stale/concurrency conflict | M1 uses 409 `CURRENT_FORMULA_CHANGED`; M2 uses 409 `LABEL_VERSION_NOT_CURRENT`. | Both are compatible domain-specific 409 codes. Clients refresh the current pointer/version before retrying. |
| Timestamps/enums | Persistence uses UTC `DATETIME` and uppercase enums; M2 declared `date-time` but left validation status unconstrained. | HTTP timestamps are RFC 3339 UTC. Validation status is constrained to baseline values `PASSED`/`FAILED`; enum tokens remain uppercase `SNAKE_CASE`. |

## Inconsistencies found and corrections

1. `CatalogErrors` returned two-field maps while M2 required a four-field error object.
   It now returns the shared `ApiError` without changing M1 business codes or logic.
2. Authentication failure was absent from the M2 OpenAPI. A reusable M5 mapping now
   distinguishes 401 from M4's 403 and the OpenAPI references both responses.
3. M2 docs still described PR #3 artifacts as unintegrated and had not been reconciled
   with the merged M1/M4/M5 code. Status and checkpoints now name the audited baseline.
4. The validation-run status schema was open-ended although the accepted database
   restricts it to `PASSED`/`FAILED`. The OpenAPI now carries the same enum.

No database schema, M1/M4/M5 business rule, permission name, workflow transition,
security gate, or test expectation was weakened.

## Canonical response and error convention

- Success: direct resource or list; 201 after a committed create, otherwise the
  endpoint's documented 2xx status.
- Error: `{ "code", "message", "traceId", "evidenceId" }`, with all four keys present.
- `code` is stable, uppercase `SNAKE_CASE`, and may be domain-specific.
- `message` is caller-safe and must not be parsed for decisions.
- `traceId` and `evidenceId` are `null` unless genuine identifiers exist.
- 400 invalid request; 401 unauthenticated/unmapped identity; 403 insufficient
  permission; 404 missing resource; 409 duplicate/stale/immutable state; 422 domain
  precondition; 500 unexpected internal error; 503 unavailable required integration.

## Tests executed on the closure branch

| Check | Result |
| --- | --- |
| Unit/contract/architecture suite (`CatalogRulesTest`, health, M4 authorization/workflow, maker-checker, `SharedApiErrorContractTest`, `OpenApiContractTest`, `ArchitectureTest`) | PASS — 18 tests, 0 failures/errors |
| PyYAML parse of `allergen-validation-api-v1.yaml` plus required `ApiError` assertion | PASS |
| `docker compose config --quiet` | PASS |
| Full `mvn verify` on local JDK 25 targeting Java 21 | Environment-blocked — all non-container tests passed; 5 Testcontainers classes could not start because the local Docker engine was unavailable |
| Frontend build | Environment-blocked — `npm ci` made no progress while existing Node processes were active, leaving the dependency tree incomplete; no M3 code was changed |

The GitHub Actions backend job is the authoritative Java 21 + Docker execution for the
closure commit; frontend, security, and container jobs are also required before merge.

## Final status and blockers

The code and contract are locally aligned on the closure branch. No unresolved
cross-member business-design decision was found. The only remaining gate is integration:
the exact closure commit must have green required CI, independent human approval, and
be merged to `main`; current-main checks must then remain green.

`M2_S1_CONTRACT_COORDINATION = PENDING_CLOSURE_PR_INTEGRATION`
