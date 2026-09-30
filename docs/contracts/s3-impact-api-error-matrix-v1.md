# Sprint 3 impact API error matrix v1.0.0 — review candidate

**State:** M2 versioned candidate; cross-module acceptance is pending. This file does not
claim that M1, M3, M4 or M5 has accepted these semantics. The envelope reuses the frozen
Sprint 2 `ApiError` schema in `allergen-validation-api-v1.yaml` without adding fields.

| HTTP | Stable code | Candidate meaning | Retry / owner note |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | Malformed body, missing required value, invalid path value, or malformed/out-of-range collection `limit`/`offset`. | Correct the request; collection `limit` is 1..100 and `offset` is nonnegative. Do not retry unchanged. |
| 401 | `AUTHENTICATION_REQUIRED` | Identity is absent, invalid, or does not map to an active actor. | M4 confirms the identity boundary and response behavior. |
| 403 | `AUTHORIZATION_DENIED` | Identity is known but lacks the permission for the operation. | `CHANGE_REQUEST.CREATE` protects change-request creation; `IMPACT.RUN` protects impact analysis. M4 confirms the shared permission contract. |
| 404 | `RESOURCE_NOT_FOUND` | A change-request or impact-analysis identifier in the URL does not exist. | 404 is for path resources only; missing request-body references use 422. |
| 409 | `DATA_CONFLICT` | A duplicate `(supplierMaterialId, previousSpecificationVersionId, targetSpecificationVersionId)` change request, or a replay of one change request with a different RuleSet. | Same change request and RuleSet replays as 200 with the existing analysis; do not create duplicate analyses, findings or ReviewTasks. |
| 422 | `CHANGE_REFERENCE_NOT_FOUND` | A supplier material or specification version referenced in the request body does not exist. | Correct the body reference. |
| 422 | `SPECIFICATION_MATERIAL_MISMATCH` | A specification version belongs to a different supplier material. | Existing M1 catalog code; correct the reference. |
| 422 | `SPECIFICATION_NOT_RELEASED` | A required specification version is not released. | Existing M1 catalog code; release it before retrying. |
| 422 | `SPECIFICATION_NOT_EFFECTIVE` | A required specification's effective date has not started. | Existing M1 catalog code; retry after the effective date. |
| 422 | `SPECIFICATION_VERSION_UNCHANGED` | The target specification version equals the previous specification version. | Supply a distinct target version. |
| 422 | `RULE_SET_NOT_ACTIVE` | The requested RuleSet version is unknown or inactive. | Select an active RuleSet. |
| 422 | `PUBLISHED_LABEL_MISSING` | An included product has no published current label version. | Correct the product publication state; the run creates no partial records. |
| 422 | `FORMULA_ADOPTION_PENDING` | The target-specification N+1 FormulaVersion has not been adopted. | Complete adoption before retrying; the run creates no partial records. |
| 500 | `INTERNAL_ERROR` | Unexpected server, integration, or audit failure. | Roll back the entire analysis transaction; caller-safe message only, with no implementation detail. |

## Shared response shape

Every error response uses exactly `code`, `message`, `traceId`, and `evidenceId`, matching
`components.schemas.ApiError` in the frozen Sprint 2 validation contract. `traceId` and
`evidenceId` are `null` unless real corresponding identifiers exist. Consumers branch on
`code`, never on `message`. Successful responses return the resource directly.

## Change-request collection read

`GET /api/v1/change-requests` returns a direct `ChangeRequest[]` page, reusing the
single-item resource, including its required `description`. It uses catalog-compatible
`limit` (default 50, range 1..100) and `offset` (default 0, nonnegative), ordered by
`changeRequestId` ascending. Filter to `INGREDIENT_SPEC` and the candidate statuses
`SUBMITTED`, `ANALYZED`, `COMPLETED` before applying pagination; exclude `DRAFT` and
`CANCELLED`. No count, wrapper, status filter or material filter is introduced.

Collection responses are 200/400/401/403/500. A valid read with no eligible records,
including an offset past the end, is **200 `[]`**, with no 404. A failed read must return
the four-field `ApiError`; it must not be replaced with `[]`. Reading has no creation or
analysis side effects. The candidate read policy matches PR #50's single-item GET:
authenticate an active identity; do not require the create or run permission to read.
M4 acceptance of this shared read policy remains pending; no new permission is seeded.

Consumers advance `offset` by `limit` until a short page (fewer than `limit` records).
An exactly full last page requires a final empty read. Pages are independent reads;
refresh from offset 0 after mutations and deduplicate by `changeRequestId`. A page limit
or interrupted read must not be presented as a complete collection or an empty state.
The selector decision and M1/M3 implementation checks are recorded in
[`s3-change-request-selector-decision.md`](s3-change-request-selector-decision.md).

## Review decisions required before acceptance

- M1: re-review the requested finding fields, non-null current label reference, published-label
  and adopted-formula preconditions, RuleSet replay behavior, duplicate change-request conflict,
  and request/response `description` field. M1's 2026-09-29 `COMMENTED` review is not approval.
- M3: confirm the direct-resource payload, collection pagination and selector loading/error
  behavior, and whether run-query pagination is needed.
- M4: confirm 401/403 behavior, operation permissions, the required ReviewTask assignee rule,
  and label/version references in the review/publication handoff.
- M5: confirm 409 behavior, transaction/idempotency semantics, and rollback of analysis,
  findings, ReviewTasks and audit records on failure.

After these decisions are recorded, accepted changes may be frozen as v1.0.0. A breaking
change after acceptance requires a versioned revision with reviewer acceptance; a review
request or a merge alone is not acceptance.
