# SCRUM-85 — Demo-runnable Spec V2 adoption and impact read model

Owner: Huang Xiangjia / M1. Jira: [SCRUM-85](https://hxj04121.atlassian.net/browse/SCRUM-85).
Date: 2026-10-21. Branch `codex/scrum-85-batch-adoption-impact`, based on main `168d404`
(after M2's contract freeze, PR #90).

The M1 part of the closed all-module candidate PR #89 was taken over by its owner,
reviewed, and reduced to M1 scope. M3 UI, M4 identity/history and M5 reset-script
changes from that candidate are not included here; they belong to SCRUM-87/88/89.

## What changed

| Area | Change |
| --- | --- |
| Batch adoption | `POST /api/catalog/specifications/{id}/formula-adoptions` (`BatchSpecificationAdoptionService`). It requires `DATA.MAINTAIN` and `FORMULA.RELEASE` before any product is read, then selects products by their *current released* formula. Each product runs M2's unchanged `CatalogService.adoptSpecification` in its own `REQUIRES_NEW` transaction, together with a replay marker. Each product's result is `ADOPTED`, `SKIPPED` or `FAILED`. A failing product is rolled back alone and logged server-side. A concurrent duplicate is resolved from the committed marker. |
| Schema | `V11__idempotent_batch_formula_adoption.sql`: `formula_adoption_batch_item`, primary key `(product_id, target_specification_version_id)`, with foreign keys to the product, the source and result formulas, and the target specification. |
| Impact read model | Each finding carries `productDisplayName`, the current catalog `product_description`, resolved through `RelevantProductLookupPort.findProductDisplayNames` when the response is built. It is not persisted, so stored run evidence is unchanged. A missing name is a server fault. The PR #89 fallback that reused the product ID as the name was removed. |
| Review-required filter | Served by the existing `outcome` discriminator and `reviewRequiredCount`, as M2's frozen impact contract 1.1.0 specifies. No new query parameter. |
| Demo path | [`scripts/s4-m1-spec-v2-demo.sh`](../../scripts/s4-m1-spec-v2-demo.sh): `reset` (separate Compose project `spectrace-s4-demo`, volume dropped), `spec-v2` (the only seeded input), `adopt`, `change`, `impact`, `all`. API only, no SQL. |
| A07 | [Adopt a Released Specification for Current Products](S4-M1-A07-adopt-specification-for-current-products.md): use case, analysis/design class, design sequence, and the per-product Unit of Work + idempotency-key decision. The [final-report index](S4-final-report-index-20261010.md) M1 row now links it. |

## Acceptance criteria

| Criterion | Evidence | Result |
| --- | --- | --- |
| Fresh database: Data Admin and Change Manager run adoption, ChangeRequest creation and impact analysis without SQL; result matches the M2 golden (40 relevant, 20 `NO_ACTION`, 20 `REVIEW_REQUIRED`, 20 controls absent) | `S4SpecV2DemoPathMySqlTest` uses its own MySQL 8.4.11 container and no SQL fixture. The Data Admin creates and releases Spec V2, then batch-adopts it. The Change Manager creates the CR and runs impact. The result is compared product by product with `s3-m2-soy-spec-v2-impact-v1.csv`. It also checks that each N+1 is current and RELEASED, N stays RELEASED, controls keep their formula, the replay creates nothing, and display names equal the catalog. | **PASS**, 3 tests |
| Same path live, on a packaged backend | Fresh MySQL 8.4.11 container plus `spectrace-backend-0.1.0-SNAPSHOT.jar` (demo profile, loopback only), driven by `scripts/s4-m1-spec-v2-demo.sh all`: Spec V2 released → `candidateCount=40 adoptedCount=40 skippedCount=0 failedCount=0` → `relevantProductCount=40 noActionCount=20 reviewRequiredCount=20`. Repeating `adopt` returned 40 `ADOPTED`, all 40 `replayed`. `GET` impact: every finding named (for example `prod_usda_1118468` → `ORGANIC COCOA POWDER`), 20 review-required by `outcome`. A Change Manager calling adoption got **403**. | **PASS** |
| MySQL Testcontainers tests for batch adoption: replay, product whose current formula changed, rollback of a failing product | `BatchSpecificationAdoptionMySqlTest`: 40 candidates on the V3 seed. One product's current formula is switched after discovery and is adopted from the new current formula. One product's marker insert fails: that product is rolled back (no N+1, current pointer unchanged) while 39 commit. A retry adopts it and replays the other 39. | **PASS** |
| Contract/error-matrix changes reviewed by M2; existing impact contract tests stay green | The implementation matches M2's frozen `s4-catalog-batch-adoption-api-v1.yaml` and impact 1.1.0 (merged in PR #90) exactly. No contract file is changed here. `S4M2ContractAlignmentTest`, `S3ImpactApiContractTest`, `OpenApiContractTest`, `ImpactAnalysisApiMySqlTest` (now asserting `productDisplayName`) and `SoyGoldenImpactRunMySqlTest` are all green. | **PASS.** M1 producer confirmation recorded here. M2's review of this PR is requested. |
| A07 sections for the M1 use case updated | S4 A07 above; index row updated. | **Done** |

## Test runs

| Run | Result |
| --- | --- |
| Full backend suite, `mvn -o test`, JDK 25.0.4, Testcontainers on Colima Docker 29.2.1 | **686 tests, 0 failures/errors/skips. BUILD SUCCESS.** |
| Focused batch/impact/contract/architecture group | 65 tests, 0 failures. |
| Migration-count assertions | Updated from 10 to 11 for V11 (`FlywayIntegrationTest`, `Scrum29MySqlIntegrationTest`, `ReviewTaskV7SameSecondDecisionHistoryMySqlTest`). `ValidationCurrentRunMigrationUpgradeMySqlTest` now pins its upgrade to V10, the migration it verifies. |

Local Testcontainers note: Colima needs
`DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

## Demo operator steps

```bash
scripts/s4-m1-spec-v2-demo.sh reset      # fresh demo stack on :8080 / :5173
scripts/s4-m1-spec-v2-demo.sh spec-v2    # prints the released Spec V2 id; the only seeded input
```

Then, live, as the Data Admin and Change Manager, either in the browser (M3 entry points,
M4 identity options) or with `adopt <spec>`, `change <spec>` and `impact <cr>`. The script
uses the development header identities and must only target a loopback demo stack.

## Open dependencies (not M1 scope)

- SCRUM-87 (M3): browser entry point for batch adoption, names, and the outcome filter on the impact page.
- SCRUM-88 (M4): Change Manager / Data Admin entries in the demo identity switch.
- SCRUM-89 (M5): the full reset/regression receipt, and an independent rehearsal on the merged SHA.
- CI on the PR for this branch is the authoritative gate before SCRUM-85 is moved to Done.
