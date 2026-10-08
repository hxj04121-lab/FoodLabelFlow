# S3 M2 Day 4: explicit specification adoption

Owner: Cai Runchen / M2. Work item: SCRUM-55 under SCRUM-48.
Updated: 2026-10-05 (Asia/Shanghai).

## Baseline and scope

Original local implementation `f310c0f9` and delivery `3e721e5` are preserved.
This branch integrates main `e7512c638dd8661cff7e6c64aaf978a8acba3bf0`,
including [PR #56](https://github.com/hxj04121-lab/FoodLabelFlow/pull/56)
(the golden oracle), [PR #57](https://github.com/hxj04121-lab/FoodLabelFlow/pull/57)
(the catalog lookup adapter), and [PR #59](https://github.com/hxj04121-lab/FoodLabelFlow/pull/59)
(the impact strategy). The main baseline's
[CI run 37216886669](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37216886669)
passed 432 backend tests; that run does not validate this branch.

SCRUM-55 and SCRUM-48 were In Progress at the latest Jira read.
SCRUM-56 is the separate Day 5 MySQL duplicate/concurrency/golden acceptance task.

## Implementation

`POST /api/catalog/products/{id}/formula-adoptions` accepts exact source-formula
and target-specification IDs. Both DATA.MAINTAIN and FORMULA.RELEASE permissions
are required. It creates and releases a new snapshot, copies material, sequence,
quantity and unit fields, replaces only the target material's specification
references, and switches the product's current formula.

The product row serializes source validation and version allocation. The operation
rejects a stale/non-current source, invalid ownership/state, an absent target
material, unreleased/not-yet-effective specifications, repeated adoption and
specification downgrade. A replaced historical specification may be RETIRED;
specifications retained in the new snapshot must remain release-eligible.
Version allocation uses MAX+1, including existing drafts.

Previous formula content, item rows and release metadata keep their exact values.
Only the previous current-selection flag becomes N. Existing labels and declarations
remain pinned to their original formula. Creation, release, pointer update and all
three audit events share one transaction; audit failure rolls everything back.
The adoption audit payload includes the source, target specification and new formula
IDs. Runtime failures use the existing four-field ApiError without internal details.

The [API candidate](../contracts/specification-adoption-api-v1.md) documents the
additive action. There are no migration, authentication, impact classification,
review-task or publication implementation changes.

## Verification

Integrated code commit `700dc877d678916dd986ad67f1b27f56c3cef7bd` passed full backend
verify on 2026-10-05: **473 tests in 73 suites, zero failures/errors/skips**.
JAR packaging, Spring Boot repackage and JaCoCo reporting completed. Subsequent
changes in this delivery update documentation only. Reports are generated under
`backend/target/surefire-reports` and `backend/target/site/jacoco`.
The local run used Java 25.0.4 targeting release 21, Maven 3.9.16 and MySQL 8.4.11
on the existing rootless Podman machine. Commands run from the repository root
with Java 21 or newer:

```powershell
mvn -B -ntp -f backend/pom.xml verify
mvn -B -ntp -f backend/pom.xml '-Dtest=FormulaSpecificationAdoptionMySqlTest' test
```

Use the existing supported Docker-compatible runtime. For a local rootless Podman
machine, set DOCKER_HOST to that machine's API endpoint and set
TESTCONTAINERS_RYUK_DISABLED=true for the Maven process, following Testcontainers'
rootless-runtime guidance. The unchanged harness uses MySQL 8.4.11, starts a real
database, and has no skip/fallback path.

The four adoption MySQL cases cover exact historical content and label/declaration
references, copied non-contiguous sequences and nullable fields, current-pointer
and audit payloads, rejected requests without writes, and rollback after a real
audit insert throws. Unit/HTTP cases also cover source/target/permission validation
and framework ApiError behavior.

Historical October 3 evidence at `3e721e5`:
- Full backend verify: 437 tests, zero failures/errors/skips.
- Focused business/HTTP/contract checks: 62 tests; adoption MySQL: 4 tests.
- Frontend locked restore and production build passed.
- Existing browser regressions: 67 mock cases and 4 live backend cases passed.
  They do not add an adoption UI flow.

Those historical results used the earlier main and are not new-head CI evidence.
Full three-service Compose execution and the Unix-socket audit browser case were
not run locally. Remote checks and review remain separately observable in the PR.

## M1 consumption and remaining acceptance

The merged lookup adapter discovers products from their current released formula's
supplier-material IDs. Adoption preserves that membership and returns N+1.
Impact classification uses the published label's pinned formula N as the current
formula and the adopted product formula N+1 as the proposed formula. The published
label's `isCurrent()` may become false after adoption; it remains a valid published
snapshot and must retain its declarations for comparison.

The [Day5 follow-up](S3-M2-day5-mysql-adoption-guards.md) replaces the classification
integration test's hand-built N+1 with real adoption and covers duplicate requests,
synchronized concurrency, retry after failure, historical equivalence and
deterministic golden outcomes on MySQL. It also fixes the material/specification
lock cycle reproduced by concurrent specification creation and formula item writes.
A RELEASED Spec V2 created by a test is a fixture, not evidence that the business
specification has been released.

SCRUM-55 remains subject to draft-PR CI and M1 review. No merge or deployment is
part of this delivery. SCRUM-48 remains In Progress; later full orchestration and
label publication acceptance belong to their respective work items.
