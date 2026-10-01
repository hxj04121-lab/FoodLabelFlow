# S3 change-request selector decision (SCRUM-48 / SCRUM-53)

**Decision date:** 2026-09-30

**Owner:** Cai Runchen / M2

**State:** M2 candidate approved by M1; backend collection implemented in merged PR #55.
M3 wiring and cross-module acceptance remain pending.

M2 selects option (a) in [M1's PR #51 thread](https://github.com/hxj04121-lab/FoodLabelFlow/pull/51#discussion_r4140282403):
add `GET /api/v1/change-requests` to the existing S3 OpenAPI candidate. M1 implemented
the route in PR #55 on top of merged PR #50; M3 connects the existing selector under SCRUM-71. Loading
the impact page must not create a new change request just to display one. The existing
POST remains available for explicit creation workflows.

## Contract and rationale

The initial decision used main `0755d061ee9a0a89ce7854a147c9e49453490f13` (PR #51
merged). The 2026-10-01 compatibility refresh uses main
`a2467c4047783cb2f6ade4f82c4345ca5f56e126` (PR #55 merged).
`frontend/src/pages/Impact.tsx` has a disabled selector, context fields and
analysis action, with no API requests. It has no create form for material, before/after
specification versions or description. A collection read fits that UI and avoids
adding a new write workflow immediately before the 2 Oct integration checkpoint.

The source of truth is
[`s3-impact-review-publication-api-v1.yaml`](s3-impact-review-publication-api-v1.yaml),
OpenAPI 3.1.0, candidate version 1.0.0. This additive operation leaves existing request,
resource, trigger and error schemas intact. The candidate retains
`CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`; neither this decision nor a merge freezes it.

| Boundary | Exact handoff |
| --- | --- |
| Route | `GET /api/v1/change-requests`; operation `listChangeRequests`; no request body. |
| Pagination | Optional integer `limit=50` (1..100), `offset=0` (>=0), following existing catalog conventions. Invalid values return 400 `INVALID_REQUEST`. |
| Selection | `INGREDIENT_SPEC` in `SUBMITTED`, `ANALYZED`, `COMPLETED`; filter before paging. Exclude `DRAFT`, `CANCELLED`, `FORMULA`, `RULE_SET`. |
| Ordering | `changeRequestId` ascending; consistent order on an unchanged collection. |
| Success | 200 direct `ChangeRequest[]`, each with the same eight fields as the single-item contract, including required `description`. No envelope or count. Empty/past-end page is `[]`. |
| Read policy | Same active-identity authentication as PR #50's single-item GET, before repository access. Reading does not require `CHANGE_REQUEST.CREATE` or `IMPACT.RUN`. M4 shared read-policy acceptance remains pending. |
| Errors | 400/401/403/500 reuse the frozen S2 four-field `ApiError`. No collection 404/409/422. Unavailable/failing reads are errors, never an empty success. |
| Effects | No creation, analysis, findings, ReviewTasks or audit writes from collection loading. |
| Page traversal | Advance by requested `limit` until a short page; a full final page needs one further request. Independent reads are not a snapshot; refresh from offset 0 after mutations and deduplicate IDs. |

## M1 implementation and compatibility

Merged PR #50 now accepts and preserves the required description on POST and
single-item GET, and returns the candidate's 422 body-reference and unchanged-version
errors. The earlier inspection at `19d699e` described pre-remediation differences;
those differences were fixed before #50 merged.

Merged PR #55 adds the paged collection, authenticates before reading, filters before
paging, orders by `changeRequestId` ascending, and resolves supplier materials in one
batch per page. List and single-item reads reuse the same eight-field response,
including the stored description. Missing referenced specifications fail the whole
read instead of silently dropping rows or returning an empty success. M1's optional
newest-first ordering suggestion remains a follow-up decision; the candidate and
implementation retain their agreed ID order.

M1 verification before integration: HTTP tests for defaults, multiple pages and stable
order, a full last page followed by `[]`, invalid/noninteger limits and offsets, no rows,
past-end offsets, mixed types/statuses filtered before paging, authentication before
lookups, a read-only actor, reference/integration failure mapped to 500, and no writes.
Confirm list items and single-item reads agree on all eight resource fields.

## M3 wiring and checkpoint acceptance

M3 can load `GET /api/v1/change-requests?limit=100&offset=0`, fetch subsequent pages,
and use `changeRequestId` as the option value. The selected resource supplies
`supplierMaterialId`, `previousSpecificationVersionId`, `targetSpecificationVersionId`,
description and status; an explicit single-item GET can refresh the selected record.
Keep loading, successful empty, partial/incomplete, authentication/authorization failure
and unavailable/error states distinct. Do not convert a failed or capped traversal to
an empty or complete list, and do not enable analysis without a valid selection and
the separate run prerequisites/permission. No POST occurs merely to populate choices.

M3 verification: multiple-page choices and selection context, honest empty/error states,
refresh after mutation, no write on initial load, and the existing analysis/review flow
once M1's real services are connected. This document does not implement SCRUM-71.

Live Jira reads on 2026-09-30 showed SCRUM-48 and SCRUM-53 assigned to RunChen Cai and
In Progress. The 2 Oct integration checkpoint still requires the M1 route and M3 wiring
to work together. M1/M3/M4/M5 acceptance, golden/MySQL checks and remaining M2 adoption
work remain pending; this clarification does not complete either Jira issue. The
8 Oct main-path E2E and 9 Oct Sprint Review/Retrospective milestones are unchanged.

## Validation of this M2 change

The 2026-10-01 refresh merged main `a2467c4`, kept the implemented ID ordering,
updated the #50/#55 compatibility notes and example identifiers, and removed
assertions on human-readable description wording. With the existing JDK 25.0.4
and offline Maven cache, `S3ImpactApiContractTest`, `OpenApiContractTest`,
`SharedApiErrorContractTest` and `ChangeRequestServiceTest` completed **28 tests,
0 failures/errors/skips; BUILD SUCCESS**. MySQL and full-stack validation are
provided by the refreshed PR CI, not this focused local run.

Local checks on 2026-09-30 used installed JDK 25.0.4 (compiler release 21) and the
existing Maven cache offline. No software was installed. The executed command was:

```text
mvn -o -B -ntp -f backend/pom.xml -Dmaven.repo.local=C:/Users/rcncai/.m2/repository -Dtest=S3ImpactApiContractTest,OpenApiContractTest,SharedApiErrorContractTest test
```

Result: **12 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** (5 S3 contract,
4 frozen S2 OpenAPI, 3 shared ApiError tests). These checks include YAML parsing,
route/response/schema boundaries, query bounds, collection examples, reused resource
and error shapes, and all local/external contract references. Strict PyYAML parsing
also passed with duplicate keys rejected; structural lint checked five unique operation
IDs, response keys, parameter bounds and local documentation links. An object comparison
against current-main `0755d061` confirmed that removing only the added GET operation,
two query parameter components and list schema reproduces the original contract exactly.
`git diff --check` passed.

A general OpenAPI 3.1 meta-schema validator is not installed, so that check was not run.
The local focused run does not execute MySQL/backend integration or frontend/browser
tests. Repository CI on the draft PR is the broader regression gate; its actual status
must be checked separately. The M1/M3 checks above are implementation handoff tests,
not tests executed or completed by this contract-only change.
