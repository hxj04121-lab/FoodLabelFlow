# S2-M2 contract diff and freeze candidate

Evidence captured: 2026-09-12T12:32:54+08:00 (Asia/Shanghai)  
Repository baseline: `origin/main` at `6fa40184ffeb3805e9b66bd948935de08dbbc547`  
Working branch: `codex/s2-m2-contract-diff`  
Pull request: [#13](https://github.com/hxj04121-lab/FoodLabelFlow/pull/13)  
Jira: [SCRUM-13](https://hxj04121.atlassian.net/browse/SCRUM-13), observed status `正在进行` (id `10002`), Sprint 2 `future`, Story Points `15`

This is a Day 1 candidate note. It records the contract boundary and the decisions to
take to M1, M4, and M5 for review. It does not claim that the validation endpoints,
validation DTOs, or validation persistence are implemented.

## Sources and ownership boundary

The comparison covered:

- canonical contract: `docs/contracts/allergen-validation-api-v1.yaml`;
- S1 M2 decisions: `docs/architecture/S1-M2-sad.md`,
  `docs/evidence/M2-cross-module-contract-closure.md`, and
  `docs/evidence/S1-M2-test-mapping.md`;
- current M1 catalog code: `CatalogController`, `CatalogCommands`, `CatalogFailure`,
  `CatalogStore`, and `CatalogErrors`;
- current M4/M5 identity/workflow code: `IdentityErrors`, `IdentityService`,
  `AuthorizationService`, `RequestCatalogIntegration`, workflow ports/services, and
  `JdbcIdentityRepository`;
- current shared and consumer surfaces: `ApiError`, `HealthResponse`, frontend API
  error projection, Flyway V1/V2/V3, `OpenApiContractTest`, `SharedApiErrorContractTest`,
  `ArchitectureTest`, and `.github/workflows/ci.yml`.

M2 owns the contract, future snapshot/application ports, deterministic golden fixtures,
and focused contract/fixture tests. M1 retains validation orchestration and allergen
derivation; M3 retains UI; M4 retains identity/RBAC/lifecycle; M5 retains validation
persistence, RuleSet behavior, and the full integration/CI harness. No implementation
from those modules is included in this Day 1 change.

## Version and endpoint diff

| Item | S1 artifact / stale reference | Current `origin/main` | Day 1 decision |
| --- | --- | --- | --- |
| OpenAPI version | The original S1 file was `0.1.0-draft`; `docs/evidence/M3_API_INTEGRATION_REQUEST.md` still repeats that historical value. | `openapi: 3.1.0`, `info.version: 1.0.0`; the contract description explicitly says the future validation endpoints are not asserted to be implemented. | Treat 1.0.0 as the canonical candidate. Treat 0.1.0-draft references as historical/stale documentation to correct or annotate in a later documentation-only cleanup. |
| Success envelope | S1 closure decision: return the resource/list directly. | The OpenAPI has no generic success wrapper. Existing M1 catalog responses are direct `List<Map<String,Object>>` / `Map<String,Object>` under `/api/catalog`. | Keep direct successful resources. M2 HTTP DTOs use `camelCase`; M1's existing raw JDBC `snake_case` success rows remain a documented legacy/module-specific surface and are not copied into new M2 DTOs. |
| `GET /api/v1/allergens` | S1 contract endpoint. | Required query `jurisdictionCode`; `200` returns an array of `Allergen`; negative responses are `400`, `401`, `403`. | Freeze this bounded, jurisdiction-scoped lookup; do not add search or pagination without review. |
| `POST /api/v1/label-versions/{labelVersionId}/validation-runs` | S1 contract endpoint. | Required JSON body `ValidationRunRequest`; `201` returns persisted `ValidationRun`; negative responses are `400`, `401`, `403`, `404`, `409`, `422`, `500`. | Keep the current-label guard and synchronous persisted-create semantics in the contract. M1/M5 implement the behavior; M2 supplies the types/ports and fixtures. |
| `GET /api/v1/validation-runs/{validationRunId}` | S1 contract endpoint. | `200` returns `ValidationRun`; negative responses are `401`, `403`, `404`. | Freeze read semantics and the same error envelope. |

## Current contract fields

`Allergen` requires `allergenId`, `allergenCode`, `displayName`, and
`jurisdictionCode` (all strings).

`ValidationRunRequest` is an object with `additionalProperties: false`; it requires
`ruleSetVersionId` (non-empty string). Client-supplied actor IDs, database IDs not in
the contract, and unreviewed field-level error extensions are not accepted.

`ValidationRun` requires `validationRunId`, `labelVersionId`, `ruleSetVersionId`,
`status`, `ranAt`, and `results`. `summary` is optional. `status` is constrained to
`PASSED` or `FAILED`; `ranAt` is an RFC 3339 `date-time` normalized to UTC by the
HTTP adapter.

`ValidationResult` requires `resultCode`, `severity`, `passed`, `blocking`, and
`message`; `ruleDefinitionId` is optional and nullable. Persistence uses `Y`/`N`
flags for `passed` and `blocking`, but the application/HTTP boundary exposes booleans.

There are currently no Java definitions for `LabelValidationSnapshot`, `AllergenFact`,
`ValidationFinding`, `ValidationRun`, or `ValidationResult`, and no validation
application ports. `ValidationModuleBoundary` is only an empty package marker. Those
are Day 2 outputs, not missing Day 1 work.

## Canonical four-field `ApiError` mapping

The only shared error DTO on current main is the Java record
`com.spectrace.shared.api.ApiError`:

```text
{ code, message, traceId, evidenceId }
```

The record requires non-blank `code` and `message`. `ApiError.of(...)` sets
`traceId` and `evidenceId` to `null`; adapters must not fabricate either identifier.
The OpenAPI schema requires exactly these four keys and disallows additional
properties. `code` is machine-readable and stable; `message` is caller-safe and not
an input to business branching.

| HTTP status | Canonical / current codes | Observed mapping and boundary |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | Missing/invalid request shape. Current M1 `CatalogFailure.invalid`, unreadable-body handler, and the contract's `InvalidRequest` response use this meaning. |
| `401` | `AUTHENTICATION_REQUIRED` | Missing, unknown, or inactive identity. M4/M5 identity exceptions and `RequestCatalogIntegration` preserve the distinction from authorization. |
| `403` | `AUTHORIZATION_DENIED` | Authenticated actor lacks the M4-defined permission. Current `IdentityErrors` and catalog integration map this without changing the permission name. |
| `404` | `RESOURCE_NOT_FOUND` | Requested canonical resource is absent. Current M1 `CatalogStore` uses this code; M2 applies it to a missing label version or validation run. |
| `409` | `LABEL_VERSION_NOT_CURRENT`; existing M1 `CURRENT_FORMULA_CHANGED`, `VERSION_IMMUTABLE`, `DATA_CONFLICT` | Stale/current-pointer, immutable-state, duplicate, or idempotency conflict. Keep domain-specific codes; do not collapse them into a generic conflict code. |
| `422` | `VALIDATION_PRECONDITION_FAILED`; existing M1 specification precondition codes | Syntactically valid input cannot be evaluated or a domain precondition fails. Current M1 uses specific 422 codes such as `SPECIFICATION_MATERIAL_MISMATCH`, `SPECIFICATION_NOT_RELEASED`, and `SPECIFICATION_NOT_EFFECTIVE`; M2's validation precondition code is reserved for canonical-input/rule-set completeness. |
| `500` | `INTERNAL_ERROR` | Unexpected validation failure. The OpenAPI documents it for validation-run creation; no current validation controller/handler exists yet. The adapter must not expose SQL, stack traces, or secrets. |

`503` is not a current M2 OpenAPI response. M1 has a real
`CATALOG_INTEGRATION_UNAVAILABLE` 503 path when its required identity/audit adapter
is absent. A validation 503, if ever needed, requires an explicit contract review;
it is not silently added here.

## Persistence alignment checked without taking M5 scope

The supplied Flyway baseline already defines `allergen`, `rule_set_version`,
`rule_definition`, `ingredient_allergen`, `label_version`,
`label_allergen_declaration`, `validation_run`, and `validation_result`. V2 constrains:

- rule-set lifecycle to `DRAFT`, `ACTIVE`, `RETIRED`;
- rule types to `INGREDIENT_TO_ALLERGEN` and `LABEL_DECLARATION_VALIDATION`;
- rule/result severity to `INFO`, `WARNING`, `ERROR`;
- specification matching to `MATCHED`, `UNMAPPED`, `AMBIGUOUS`;
- label lifecycle to `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `PUBLISHED`,
  `SUPERSEDED`, `REJECTED`;
- validation-run status to `PASSED`, `FAILED`;
- validation-result `passed` and `blocking` to `Y`/`N`.

These existing constraints support the current OpenAPI status enum and the planned
fixture outcomes. No migration, seed fallback, or persistence implementation is
introduced by this diff.

## Duplicate, stale, and absent definitions

1. `docs/contracts/allergen-validation-api-v1.yaml` is the canonical contract. The
   `0.1.0-draft` wording in `docs/M3_API_INTEGRATION_REQUEST.md` is stale S1 context;
   it is not a second live contract.
2. `docs/architecture/S1-M2-sad.md` and the S1 closure evidence are historical design
   and reconciliation records. They state that validation endpoints and DTOs were not
   implemented and do not override the current `origin/main` snapshot.
3. `ApiError` is unique as a server-side shared error DTO. `CatalogErrors` and
   `IdentityErrors` are adapters, not duplicate envelopes. The frontend
   `CatalogRequestError` is a client-side projection of `code`, `message`, and HTTP
   status, not a replacement server contract; it currently does not invent trace or
   evidence identifiers.
4. `HealthResponse` is a health-only DTO and is unrelated to validation responses.
   Catalog command records are M1 input DTOs and must not be reused as M2 validation
   snapshot types.
5. No current Java validation DTO/port definition was found beyond the empty
   `ValidationModuleBoundary`; Day 2 must add the cross-module boundary explicitly,
   without importing another module's repository or issuing foreign-module SQL.

## Proposed freeze decisions for M1/M4/M5 review

1. Freeze OpenAPI 3.1.0 / contract version 1.0.0 as the candidate baseline.
2. Freeze direct success resources, M2 `camelCase` fields, server-assigned IDs, and
   the exact four-field `ApiError` with nullable `traceId`/`evidenceId`.
3. Freeze status semantics: 400 invalid input; 401 unmapped authentication; 403
   denied permission; 404 missing resource; 409 stale/duplicate/immutable state;
   422 valid request blocked by domain preconditions; 500 unexpected internal error.
   Preserve domain-specific M1 codes where they already carry useful meaning.
4. Freeze `ValidationRun.status` to `PASSED`/`FAILED`, `ValidationResult` booleans at
   the application boundary, UTC RFC 3339 timestamps, and nullable
   `ruleDefinitionId`.
5. Add Day 2 snapshot/finding DTOs and application ports only after M1/M4/M5 review;
   keep formula-to-ingredient/component-to-allergen derivation behind ports and keep
   all validation persistence/RuleSet behavior in M5's ownership boundary.
6. Use explicit deterministic fixtures for positive and negative outcomes. A missing
   fixture row, absent active RuleSet, unmapped ingredient, or ambiguous ingredient
   must produce an explicit finding/blocking expectation; no silent seed fallback is
   allowed.
7. Any breaking field rename, new status/code, async response lifecycle, schema
   change, or 503 addition reopens this candidate for cross-module review.

## Review and implementation status

| Reviewer boundary | Required review question | Status at candidate creation |
| --- | --- | --- |
| M1 / Huang Xiangjia | Are snapshot/fact/finding semantics compatible with the owned derivation/orchestration boundary? | Requested on PR #13 to `hxj04121-lab`; acceptance pending. |
| M4 / Zhu Wenyu | Are 401/403 semantics, permission handoff, and lifecycle/current-version assumptions compatible with identity/workflow ownership? | Requested on PR #13 to `zhuwenyu04`; acceptance pending. |
| M5 / Sun Huajian | Are persistence field/status mappings and future fixture consumption compatible with the MySQL/Testcontainers harness? | Requested on PR #13 to `SHJ-SHJ0128`; acceptance pending. |
| M3 / Xu Feiyang | Optional compatibility feedback on direct response/error field names. | Optional; not a Day 1 freezer. |

The review requests are not approval claims. Contract freeze remains pending until
real M1/M4/M5 review evidence exists.

## Baseline verification

- `git fetch origin --prune`: completed; `origin/main` observed at `6fa4018`.
- `git status --short --branch`: clean tracked worktree on the new M2 branch before
  this documentation change; pre-existing scheduler runtime files remain untracked
  under `.project-control/sprint/S2/`.
- CI workflow inspected: backend Maven/Testcontainers verification, frontend build,
  Compose validation/image build, and conditional Trivy/OWASP security jobs.
- PR #13 is open against `main` at head `1c186d7`; GitHub reports review requests
  for `hxj04121-lab`, `zhuwenyu04`, and `SHJ-SHJ0128`, with no review approvals yet.
- Jira live read/write evidence: native Atlassian write comment id `10001`, followed by
  a live re-read showing status id `10002`; Story Points write/read verified as `15`.

This note is a freeze candidate, not a claim of review completion or Day 7 closure.
