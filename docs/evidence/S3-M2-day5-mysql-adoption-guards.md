# S3 M2 Day 5: MySQL adoption guards and golden integration

Owner: Cai Runchen / M2. Jira: SCRUM-56 under SCRUM-48.
Updated: 2026-10-05 (Asia/Shanghai).
Local branch: `scrum-56-adoption-guards`, based on Day4 `d6ca803`;
main baseline: `e7512c6`. No merge or deployment is part of this delivery.

## Result and scope

The actual Catalog HTTP adoption action now drives the existing PR56 golden,
PR57 discovery/lookup, and PR59 ingredient-specification strategy integration.
No test constructs a successful N+1 or updates its current pointer by fixture SQL.
SQL setup creates only clearly identified released/effective specification prerequisites.

The production change makes both formula creation and specification adoption lock
their distinct material rows in sorted ID order before locking specifications.
The product/source serialization and existing specification ordering remain.
This aligns item foreign-key writes with specification creation's material-first
order. Formula release needs no new material lock and remains unchanged.

## Reproduced defect

On unchanged Day4 source `d6ca803`, two controlled real HTTP/MySQL races failed:
- Specification creation versus adoption: creator 500, adoption 201.
- Specification creation versus ordinary formula creation: creator 500, formula 201.

Both logs report `MySQLTransactionRollbackException: Deadlock found` while the
creator waits for the latest specification's `FOR UPDATE` lock. Formula item
insertion holds the specification lock and needs a material foreign-key lock;
the creator already holds that material. The regression coordinates actual
product/material locks with bounded latches. Its optional write-stage observation
always releases the creator even when the fixed formula operation waits earlier,
so the test does not introduce an artificial lock cycle.

Both cases pass after the material-before-specification adjustment, with two
201 responses, exact version allocation and expected audit rows.

## Executable acceptance

| Test | What it proves |
| --- | --- |
| `FormulaSpecificationAdoptionMySqlTest` (8 cases) | Exact snapshots and field copies; identity/permission and invalid-target rejection; original-source replay 409 CURRENT_FORMULA_CHANGED; current-source/same-target 409 SPECIFICATION_ALREADY_ADOPTED; no additional rows; two simultaneous independent MySQL transactions for same and competing targets, exactly one 201/one 409; audit insert failure rollback and successful same-body retry; MAX+1 above an existing version-7 draft without changing the draft. |
| `FormulaAdoptionMaterialLockMySqlTest` (2 cases) | The two cross-operation material/specification races described above, with real HTTP, locks, writes and audits. |
| `IngredientSpecImpactStrategyMySqlTest` (4 cases) | Registry/pre-adoption guards; 40 actual 201 adoptions, fresh discovery item IDs and exact 20 NO_ACTION / 20 REVIEW_REQUIRED / 20 excluded controls; old label N/current versus adopted N+1/proposed; missing published label gives 422 before impact writes. |
| Existing unit/HTTP adoption tests | Business rules, error shape and unchanged framework behavior remain covered. |

Concurrent adoption captures two distinct MySQL CONNECTION_ID values from actual
Spring transactions with autocommit disabled. Both requests are in flight while
the winner holds the product lock. There are only the winner's copied items,
current formula and three formula audit events. Gates, client workers and server
transaction completion are bounded and awaited before fixture cleanup.

Historical formula content, release actors/timestamps and item rows remain
field-equivalent. Only the old current-selection flag and its generated selection
key may change. Complete published label/declaration rows remain equal and pinned
to N. All 20 excluded controls remain equal. Audit payloads identify source, target
specification and new formula. Failed audit writes leave no formula, item, pointer
or audit residue before retry.

Golden cleanup restores the original seed pointers/flags and verifies all 60 seed
snapshots again. Cleanup reconstructs fixture ownership from committed database
rows referencing this test's UUID target and excludes all original formula IDs.
It never relies on a response ID alone. The versioned golden CSV is unchanged.

## Verification

Focused Maven run passed **51 tests, zero failures/errors/skips** (before the final
cleanup ownership hardening). Full verification of source commit
`93ee8173e6c023cfd4ba6a1ffa962f00808f01a7` passed: **480 tests in 74 suites,
zero failures/errors/skips**. Maven verify completed JAR packaging, Spring Boot
repackage and JaCoCo reporting. Reports are generated under
`backend/target/surefire-reports` and `backend/target/site/jacoco`.
Later changes in this delivery update documentation only.

Run from the repository root with Java 21 or newer and the existing supported
Docker-compatible runtime:

```powershell
mvn -B -ntp -f backend/pom.xml '-Dtest=FormulaSpecificationAdoptionMySqlTest,FormulaAdoptionMaterialLockMySqlTest,IngredientSpecImpactStrategyMySqlTest,CatalogSpecificationAdoptionTest,CatalogSpecificationAdoptionHttpTest' test
mvn -B -ntp -f backend/pom.xml verify
```

The unchanged shared harness uses MySQL 8.4.11 and the existing Flyway seed.
The local runtime is existing rootless Podman; only per-process DOCKER_HOST and
TESTCONTAINERS_RYUK_DISABLED are set. There is no H2, reused/native database,
skip/fallback path, new CI harness, migration or security configuration change.
The branch name matches the existing `scrum-**` push workflow.

## Remaining review and acceptance

The RELEASED V2 rows here are test prerequisites. They do not establish actual
business V2 release or approval. This verifies Catalog adoption through the
existing discovery and strategy components; it does not execute the full impact
run API, persist findings/review tasks, approve labels or publish replacement labels.
No teammate's production discovery/classification/publication code was changed.

Day5 depends on Day4. A draft PR can target the Day4 branch while it is unmerged.
Existing push CI then validates backend/frontend/containers/security as configured.
The existing Sonar job runs for main/pull requests targeting main, so Sonar and
final main-base acceptance must be checked when the dependent PR is retargeted.
Current-head remote CI and M1/M2 review are still required. SCRUM-48 remains
In Progress; no Jira Done or full scenario acceptance is claimed.
