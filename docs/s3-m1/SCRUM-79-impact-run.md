# SCRUM-79 — impact run orchestration, trigger/query API and ReviewTask creation

Parent: SCRUM-47 / S3-M1.5. Estimate: **4 story points**. Builds on SCRUM-77/78 (#57, #59)
and M5's atomic persistence (SCRUM-59/62).

## Endpoints (frozen M2 contract `s3-impact-review-publication-api-v1.yaml`)

| Operation | Behaviour |
| --- | --- |
| `POST /api/v1/change-requests/{id}/impact-analyses` | Requires `IMPACT.RUN`. Body must be exactly `{"ruleSetVersionId": "..."}`. First run → **201** + `Location`. Replay with the same rule set → **200**, same analysis. A different rule set → **409**. |
| `GET /api/v1/impact-analyses/{id}` | Any active identity, the same as change-request reads (M4 may add a permission). Unknown ID → **404**. |

Errors use the shared four-field `ApiError` and the codes in the error matrix:

- 400 `INVALID_REQUEST`
- 401 / 403 (from identity)
- 404 for path IDs only
- 409 `DATA_CONFLICT` for a different rule set, or a CANCELLED/DRAFT request
- 422 `RULE_SET_NOT_ACTIVE`, `PUBLISHED_LABEL_MISSING`, `FORMULA_ADOPTION_PENDING`
- 500 `INTERNAL_ERROR`, with a caller-safe message

## Flow (`ChangeImpactAnalysisService.run`)

1. Check `IMPACT.RUN`, then **lock the change_request row** so concurrent runs of one
   request queue up.
2. If the request already has a run, replay it (200), or return 409 if the rule set differs.
3. Reject a CANCELLED/DRAFT request (409) and an inactive rule set (422).
4. Resolve the material from the target spec, discover relevant products (SCRUM-77), and
   classify each one with the registered strategy (SCRUM-78). **Every 422 is raised here,
   before the first write.**
5. Build the run (`COMPLETED`, timestamps in whole UTC seconds), its findings, and one
   **OPEN ReviewTask per REVIEW_REQUIRED finding** with `draft_label_version_id = NULL`.
   Then hand them to M5's `ImpactAnalysisApplicationService.execute`, which writes the run,
   findings, tasks and audit event.
6. Move the change request **SUBMITTED → ANALYZED** (SCRUM-75 decision #5).

All of this runs in one transaction. Any failure (audit, assignee, constraint) rolls back the
run, findings, tasks, audit row and status change. If a concurrent request inserts the run
first, M5's service returns that run and the call answers 200.

Provenance: the run, findings and tasks carry the change request's `data_provenance_id`
(`prov_scenario_input` for the class scenario), so outputs trace back to their input.

## ReviewTask assignee: decision for M4

`review_task.assigned_to_user_id` is NOT NULL, and the contract still lists the assignee rule
as pending M4. This PR implements the SCRUM-75 proposal (decision #2):

- The assignee is the **active holder of `LABEL.CREATE`** (the maker who drafts the
  replacement label) **with the lowest userId**. With the seed data this is
  `user_label_officer`.
- If nobody holds `LABEL.CREATE`, the run fails and rolls back. The task is never assigned to
  a guessed user.
- To support this, identity gains an additive read: `IdentityService.activeUserIdsWithPermission`
  / `IdentityRepository.findActiveUserIdsWithPermission`.
- The rule lives in `RequestImpactIntegration`, so M4 can change it in one place.

## Verification

- `ChangeImpactAnalysisServiceTest` (14, unit, in-memory ports) covers:
  - the first run, replay, conflicting rule set, 404/409/422/400 and permission order;
  - a precondition on one product fails the whole run with nothing written;
  - a run with no products or with only NO_ACTION findings;
  - a missing assignee;
  - a REVIEW_REQUIRED finding without its task is reported, not hidden.
- `ImpactAnalysisApiMySqlTest` (15, real HTTP, MySQL 8.4.11, own container) uses the Spec V2
  adoption fixture `fixtures/s3-soy-spec-v2-adoption.sql`. It covers:
  - 201 with the exact contract shape (40 products: 20 NO_ACTION, 20 REVIEW_REQUIRED with
    tasks), and the GET reads the same body back;
  - replay returns 200 with no new rows, and a different rule set returns 409;
  - **two concurrent triggers give exactly one run (one 201, one 200)**;
  - **an audit failure rolls back everything, and the status stays SUBMITTED**;
  - 401/403, 7 malformed bodies → 400, 404, `RULE_SET_NOT_ACTIVE`,
    `FORMULA_ADOPTION_PENDING` (Wheat Flour V2 without adoption), and CANCELLED → 409.
- `IdentityRepositoryIntegrationTest` (+1): only active holders are returned, in userId order.
