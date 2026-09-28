# Sprint 3 impact API error matrix v1.0.0 — review candidate

**State:** M2 versioned candidate; cross-module acceptance is pending. This file does not
claim that M1, M3, M4 or M5 has accepted these semantics. The envelope reuses the frozen
Sprint 2 `ApiError` schema in `allergen-validation-api-v1.yaml` without adding fields.

| HTTP | Stable code | Candidate meaning | Retry / owner note |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | Malformed body, missing required value, or invalid path value. | Correct the request; do not retry unchanged. |
| 401 | `AUTHENTICATION_REQUIRED` | Identity is absent, invalid, or does not map to an active actor. | M4 confirms the identity boundary and response behavior. |
| 403 | `AUTHORIZATION_DENIED` | Identity is known but lacks the permission for the operation. | M4 confirms permission names and which operations require them. |
| 404 | `RESOURCE_NOT_FOUND` | Change request, impact analysis, supplier material, or specification version is absent. | Do not infer a resource from an empty result. |
| 409 | `CURRENT_FORMULA_CHANGED` | A current formula pointer changed since the caller read it. | Preserve the existing M1 catalog code; reload current state before retrying. |
| 409 | `DATA_CONFLICT` | An immutable change request or idempotency key conflicts with existing data. | A replay with the same change request and RuleSet returns the existing impact analysis (200); M1/M5 must confirm duplicate and concurrency semantics. |
| 422 | `SPECIFICATION_MATERIAL_MISMATCH` | A specification version belongs to a different supplier material. | Existing M1 catalog code; correct the reference. |
| 422 | `SPECIFICATION_NOT_RELEASED` | A required specification version is not released. | Existing M1 catalog code; release it before retrying. |
| 422 | `SPECIFICATION_NOT_EFFECTIVE` | A required specification's effective date has not started. | Existing M1 catalog code; retry after the effective date. |
| 500 | `INTERNAL_ERROR` | Unexpected server failure. | Caller-safe message only; no implementation detail. |
| 503 | `CATALOG_INTEGRATION_UNAVAILABLE` | The existing M1 identity/audit integration is unavailable. | Preserve the current M1 code. Whether S3 should expose 503 and its retry owner remain pending M1/M5 acceptance. |

## Shared response shape

Every error response uses exactly `code`, `message`, `traceId`, and `evidenceId`, matching
`components.schemas.ApiError` in the frozen Sprint 2 validation contract. `traceId` and
`evidenceId` are `null` unless real corresponding identifiers exist. Consumers branch on
`code`, never on `message`. Successful responses return the resource directly.

## Review decisions required before acceptance

- M1: confirm explicit `ruleSetVersionId` on the trigger, current/proposed formula
  references, `draftLabelVersionId` naming, source-version errors, and the proposed
  ChangeRequest status transitions `SUBMITTED → ANALYZED → COMPLETED`.
- M3: confirm the direct-resource payload and whether run-query pagination is needed.
- M4: confirm 401/403 behavior, operation permissions, the required ReviewTask assignee
  rule, and label/version references in the review/publication handoff.
- M5: confirm 409 and 503 boundaries, transaction/idempotency behavior, and retry rules.

After these decisions are recorded, accepted changes may be frozen as v1.0.0. A breaking
change after acceptance requires a versioned revision with reviewer acceptance; a review
request or a merge alone is not acceptance.
