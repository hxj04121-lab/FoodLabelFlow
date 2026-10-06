# SCRUM-80 — SOY golden integration evidence

Owner: Huang Xiangjia / M1. Parent: SCRUM-47 / S3-M1.6. Date: 2026-10-06.
Baseline: `origin/main@f974d61` + SCRUM-79 (#65).

## What was run

`SoyGoldenImpactRunMySqlTest` drives the S3 scenario over real HTTP, against a Flyway-migrated
MySQL 8.4.11 container that contains the V3 seed:

1. **Fixture.** `fixtures/s3-soy-spec-v2-adoption.sql` releases `spec_chocolate_v2`, which is
   Chocolate Base V1 plus Soy Lecithin. It then adopts that spec into a new current RELEASED
   FormulaVersion N+1 for each of the 40 products whose current formula uses
   `mat_chocolate_base`. N keeps backing the published label. M2's real adoption
   (SCRUM-55/56, PRs #61/#62) is still a draft, so this fixture stands in for it.
2. **Change request.** The Change Manager creates it:
   `POST /api/v1/change-requests` (`spec_chocolate_v1 → spec_chocolate_v2`) → 201.
3. **Run.** The Change Manager triggers the analysis:
   `POST …/impact-analyses {"ruleSetVersionId":"ruleset_us_falcpa_demo_v1"}` → 201.
4. **Oracle.** The response and the committed rows are compared with M2's golden
   `backend/src/test/resources/golden/s3-m2-soy-spec-v2-impact-v1.csv`
   (`S3-M2-SOY-SPEC-V2-IMPACT` v1, merged in #56).

## Results

| Check | Expected (golden) | Actual |
| --- | --- | --- |
| Relevant products | 40 (the NO_ACTION and REVIEW_REQUIRED products) | 40; `relevantProductCount = 40` |
| Outcome per product | 20 `NO_ACTION`, 20 `REVIEW_REQUIRED` | Identical `productId → outcome` map |
| Missing allergens | `[SOY]` for REVIEW_REQUIRED; `[]` for NO_ACTION | Same |
| Negative controls (20 `EXCLUDED_NO_FINDING`) | No finding, no task | None in the response, `impact_finding` or `review_task` |
| ReviewTasks | One OPEN task per REVIEW_REQUIRED product, no draft yet | 20 rows: `OPEN`, `draft_label_version_id IS NULL`, assignee `user_label_officer` |
| Version references | current = formula behind the published label (N); proposed = adopted N+1 | Matches `label_version.formula_version_id` and `product.current_formula_version_id` |
| Run | One COMPLETED run with 40 findings; change request ANALYZED | Same; `GET /api/v1/impact-analyses/{id}` returns the identical body |

**Result: 5 tests, 0 failures.** The analysis matches the golden exactly, and the negative
controls produce no findings.

The full backend build on this branch passed: `mvn -B -ntp -f backend/pom.xml verify` with
JDK 25 targeting release 21 on Colima. The PR records the test count.

## Related evidence

- API behaviour: 201/200/409/422/401/403/400, concurrent triggers, and audit-failure
  rollback. See `ImpactAnalysisApiMySqlTest` and [SCRUM-79 notes](../s3-m1/SCRUM-79-impact-run.md).
- Classification on the same golden, at strategy level: `IngredientSpecImpactStrategyMySqlTest` (SCRUM-78).
- A07: [Run Change Impact Analysis](S3-M1-A07-run-change-impact-analysis.md) and
  [Impact Strategy design problem](S3-M1-A07-impact-strategy-draft.md).

## Not covered here

- **Browser E2E.** The change-to-publication flow in the browser (M3 UI, then M4
  review/publication) is owned by M3/M5. The backend half is ready: M3 can call the two
  endpoints above. The E2E on main CI by 8 Oct still needs M3's selector (SCRUM-71) and M4's
  review/publication endpoints.
- **Real adoption.** Swap the fixture for M2's real adoption when #61/#62 merge. The
  assertions don't depend on how N+1 was created.
