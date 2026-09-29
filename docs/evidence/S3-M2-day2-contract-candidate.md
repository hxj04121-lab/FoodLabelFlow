# Sprint 3 M2 Day 2 — impact and handoff contract candidate

**Observed:** 2026-09-29, Asia/Shanghai
**Owner:** Cai Runchen / M2
**Jira:** SCRUM-48 / SCRUM-53
**State:** executable candidate; cross-module acceptance and contract freeze remain pending.

## Live baseline and ownership

- Re-fetched `origin/main` at `0745fcd7b3c2c18292c78e9cb4f35674dcdf8ef9`. The Day 2 branch is based on this commit.
- Live Jira reads show SCRUM-48 and SCRUM-53 assigned to RunChen Cai and In Progress.
- Day 1 PR [#46](https://github.com/hxj04121-lab/FoodLabelFlow/pull/46) is merged. M1 PR [#47](https://github.com/hxj04121-lab/FoodLabelFlow/pull/47) is open; its description gives implementation proposals, not acceptance of PR #48.
- Main CI run [36366295460](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36366295460) failed in OWASP Dependency-Check after Maven Central returned HTTP 429. Backend, frontend, containers and SonarQube jobs passed on that run.
- Scope in this PR is limited to M2-owned contract/schema artifacts and their contract test. No M1 impact engine, M3 UI, M4 workflow/publication behavior, or M5 persistence was implemented.

## M1 review and contract decisions

M1 reviewer `hxj04121-lab` submitted a real `COMMENTED` review on PR [#48](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48) at 2026-09-29 02:01:02 UTC (10:01:02 Asia/Shanghai). The review explicitly withholds approval pending the two blocking findings. Its 10 unresolved threads request:

1. Add required `missingAllergenCodes` to both finding variants (empty for `NO_ACTION`, non-empty for `REVIEW_REQUIRED`, unique and sorted); replace the mismatched optional `explanationCode` with required caller-safe `explanation` matching the non-null database column.
2. Make all three `currentLabelVersionId` references required and non-null. Return `PUBLISHED_LABEL_MISSING` (422) for a relevant product without a published label, before any writes.
3. Keep `proposedFormulaVersionId` required and return `FORMULA_ADOPTION_PENDING` (422) when target-spec N+1 has not been adopted.
4. Replay same change request plus same RuleSet as 200 with the existing analysis; same change request plus a different RuleSet is 409 `DATA_CONFLICT`; duplicate change-request material/version tuple is 409.
5. Add required `description` (1–1000 characters) to create request and response.
6. Reserve 404 for missing path resources; classify missing body references, inactive RuleSet and unchanged spec version as 422.
7. Remove inapplicable `CURRENT_FORMULA_CHANGED`.
8. Remove 503 `CATALOG_INTEGRATION_UNAVAILABLE`; an integration or audit failure is 500 `INTERNAL_ERROR` with full transaction rollback.
9. Name `CHANGE_REQUEST.CREATE` and `IMPACT.RUN` on the corresponding POST operations. GET permissions remain for M4 to decide.
10. Remove personal scheduled-runner and logical-day control details from course evidence.

This revision addresses the requested M2 contract/test/evidence changes only. It does not claim that M1 has re-reviewed or accepted them; M1 re-review remains pending. M3, M4 and M5 acceptance also remains pending.

## Candidate artifacts

- `docs/contracts/s3-impact-review-publication-api-v1.yaml`: OpenAPI 3.1.0, version 1.0.0, marked `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`. It defines the change-request create/read and impact-analysis trigger/query boundaries, outcome variants, ReviewTask linkage, publication handoff identifiers, replay behavior and domain preconditions.
- `docs/contracts/s3-impact-api-error-matrix-v1.md`: negative-path meanings mapped to the exact Sprint 2 four-field `ApiError` schema. 404 is limited to path resources; body references and impact preconditions use 422; server/integration/audit failures use 500.
- `backend/src/test/java/com/spectrace/S3ImpactApiContractTest.java`: executable checks for route/response shape, permission names, required description and finding fields, non-null label references, allergen constraints, S2 compatibility, error-code matrix alignment, replay/precondition semantics and local/external `$ref` resolution.

The candidate incorporates the field proposals from M1 PR #47: explicit `ruleSetVersionId`, current and proposed FormulaVersion references, `draftLabelVersionId`, and proposed ChangeRequest status values. The M4-owned required ReviewTask assignee rule remains unresolved and is recorded for review. These are candidate contract choices, not accepted cross-module decisions.

## Verification

| Check | Result |
| --- | --- |
| Existing PR-head CI [36370616509](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36370616509), head `bef975d` | PASS before this review-fix revision — backend 301 tests with 0 failures/errors/skips; frontend, security, SonarQube and containers all passed. |
| Local focused Maven run | Environment-blocked: installed JDK is 17; the project requires Java 21 (`release version 21 not supported`). |
| Review-fix CI [36535824308](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36535824308), head `d5cde88` | Failed test compilation because AssertJ could not accept a value through `List<?>.contains`; corrected by asserting the standard `List.contains` result. Frontend and security jobs passed; backend-dependent jobs were skipped. |
| Review-fix CI [36536050973](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36536050973), head `7ec3750` | Backend compiled and ran 301 tests; one contract test retained the old 503 expectation on the impact-analysis GET route. Corrected the duplicate assertion to expect the new response set. Frontend and security passed; backend-dependent jobs were skipped. |
| Review-fix CI [36536456162](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36536456162), code head `1febcb9` | PASS — backend 301 tests with 0 failures/errors/skips; frontend, security, containers and SonarQube all passed. |

## Remaining acceptance gate

PR [#48](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48) remains an open candidate. The M1 review is `COMMENTED`, not approved; re-review is pending this revision. M3/M4/M5 review and acceptance remain pending. A review request, an open PR, or a green CI run is not reviewer acceptance. SCRUM-53 and SCRUM-48 remain In Progress until their respective acceptance requirements are met.
