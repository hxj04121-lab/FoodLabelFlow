# S3 M2 Day 4 — explicit specification adoption

Observed: 2026-10-03, Asia/Shanghai. Owner: Cai Runchen / M2.
Jira: SCRUM-55 under SCRUM-48. Status: locally implemented; review and runtime
acceptance remain pending.

## Baseline

This change starts from current main
`e53f7b0f0fb7c9e0038dbbbe4193f953edab8e35`, whose
[CI run 36822786343](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36822786343)
passed. That baseline CI does not validate today's change.

SCRUM-55 was assigned to RunChen Cai and still Idea at the live read. SCRUM-48 was
In Progress. Yesterday's SCRUM-54 was Done, but
[PR #56](https://github.com/hxj04121-lab/FoodLabelFlow/pull/56) at
`b8af450d5bddd6943879b6523130f35c5a910c54` remained open, with no human reviews.
Its green [CI run 36959350782](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36959350782)
is prior evidence and is not attributed to this adoption implementation.

## Implementation

The new Catalog action
`POST /api/catalog/products/{id}/formula-adoptions` receives exact source-formula
and target-specification IDs. Both DATA.MAINTAIN and FORMULA.RELEASE permissions
are required. It creates and releases a new immutable snapshot, copies material,
sequence, quantity and unit fields, replaces only the target material's
specification references, and switches the product's current formula.

The product row serializes source validation and version allocation. The operation
rejects a stale/non-current source, invalid ownership/state, an absent target
material, unreleased/not-yet-effective specifications, repeated adoption and
specification downgrade. A replaced historical specification may be RETIRED;
specifications retained in the new snapshot must remain release-eligible.

Previous formula content and items retain their exact values. Existing release
metadata remains unchanged; only the previous current-selection flag becomes N.
Current published labels and declarations keep their original formula reference.
The next version number uses the existing MAX+1 allocation, including already
allocated drafts. This avoids overwriting another draft.

Creation, release, pointer update and audit share one transaction. The existing
request adapter records the source, target specification and new formula IDs in
the adoption event. Runtime failures return a four-field INTERNAL_ERROR without
internal details. Existing 400/415/405 framework behavior is covered.

No migrations, authentication settings, impact classification, review or
publication implementation changed. The
[API candidate](../contracts/specification-adoption-api-v1.md) documents the
additive Catalog action; it does not freeze a new cross-module agreement.

## Validation

Local compilation targets Java 21 (`--release 21`) with installed Java 25.0.4 and
Maven 3.9.16. The system Java selection was not changed.

| Check | Actual result |
| --- | --- |
| Compile all 144 production sources | PASS. |
| Compile all 77 test sources, including the MySQL tests | PASS. |
| CatalogSpecificationAdoptionTest | PASS: 16 business-rule tests. |
| CatalogSpecificationAdoptionHttpTest | PASS: 21 HTTP/ApiError/regression cases. |
| Existing CatalogRulesTest, ArchitectureTest, ValidationArchitectureTest, SharedApiErrorContractTest, OpenApiContractTest, S3ImpactApiContractTest | PASS. Focused run total: 62 tests, zero failures/errors/skips. |
| FormulaSpecificationAdoptionMySqlTest | PASS: 4 real MySQL/HTTP tests, zero failures/errors/skips, on unchanged mysql:8.4.11 using existing rootless Podman. The initial Desktop attempt had 4 initialization errors; those historical logs are retained. |
| Full backend verify | PASS: 437 tests across 67 suites, zero failures/errors/skips; jar, Spring Boot repackage and JaCoCo report completed. |
| Frontend locked dependency restore and production build | PASS: npm ci, TypeScript and Vite. Local Node 24.14.0 differs from CI Node 22. Existing 575.62 kB chunk warning remains. |
| Existing mock API browser regressions, installed Edge | PASS: 66 cases plus 1 catalog backend-failure fixture, zero failures/skips. These do not exercise a real backend or today's adoption transaction. |
| Applicable live backend browser/API regressions | PASS: 4 cases (catalog, validation, formula lifecycle and selected RBAC); independent MySQL container, local backend and installed Edge. They do not add a new adoption UI flow. |
| git diff --check | PASS. |
| Full three-service Compose build, Unix-socket audit browser case, security/Sonar, new-head CI | NOT RUN for this change. |

Docker Desktop failed during startup while creating its `dockerInference` IPC
listener: "The file cannot be accessed by the system." A controlled launch of the
existing application in the owner's Windows session at 03:08 UTC reproduced the
same fatal error while removing
`C:\Users\rcncai\AppData\Local\Docker\run\dockerInference`. A bounded
`docker info --format {{.ServerVersion}}` probe timed out after 10 seconds. Native
WSL enumeration worked, with the docker-desktop distribution stopped. An attempt
to start the existing helper service lacked OS permission; that service is not
established as the cause and is not required for the WSL2 Linux engine.

New read-only Win32 inspection confirmed the three runtime objects are AF_UNIX
socket reparse points (tag 0x80000023). No socket, ACL, Docker settings, credentials
or existing database data was changed. Normal quit cannot clean an already
stopped application; Desktop startup was not blindly repeated.

The existing Podman WSL2 machine started normally without user interaction. It
retained its rootless mode and network configuration. Its Docker-compatible API
1.44 served the unchanged Testcontainers mysql:8.4.11 harness through a local
named pipe. Only each Maven process used DOCKER_HOST and the official rootless
TESTCONTAINERS_RYUK_DISABLED setting; no global context or privileged-container
setting changed. This restored real database verification. The four tests now
executed exact old formula/items/label preservation, copied fields, pointer/audit
payload, rejected requests and rollback after a real audit insert throws.

The first full verify passed all 437 tests, then failed in jar dependency
resolution after a transient HTTPS handshake error. A diagnostic package retry
succeeded without source, dependency, proxy or TLS changes. The final full verify
then passed all phases without skipping tests.

Live browser checks used a separate new --rm MySQL container and copied executable
JAR, with an in-memory Vite proxy on port 55174. Validation ran before the formula
lifecycle changes its seed preconditions. Four live cases and nine live screenshots
are recorded. The app, Vite and own MySQL container were stopped afterward. Exact
before/after inventories remain seven pre-existing stopped containers and four
pre-existing volumes; all three test ports are free. Podman remains running for
subsequent work. Newly cached public test images remain; existing images/data were
not edited. No fallback database or skipped-as-pass result was used.

Reproduce the passing focused checks from the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4'
mvn -B -ntp -f backend/pom.xml '-Dtest=CatalogSpecificationAdoptionTest,CatalogSpecificationAdoptionHttpTest,CatalogRulesTest,ArchitectureTest,ValidationArchitectureTest,SharedApiErrorContractTest,OpenApiContractTest,S3ImpactApiContractTest' test
```

Reproduce the true MySQL and complete verification with the existing Podman:

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/podman-machine-default'
$env:TESTCONTAINERS_RYUK_DISABLED = 'true'
mvn -B -ntp -f backend/pom.xml '-Dtest=FormulaSpecificationAdoptionMySqlTest' test
mvn -B -ntp -f backend/pom.xml verify
```

Frontend commands ran from `frontend`, with a dedicated Vite server on port
55173. The server was stopped and the port released after verification. Existing
Edge was used; no browser or global software installation was needed.

```powershell
npm ci --no-audit --no-fund
npm run build
npm run dev -- --host 127.0.0.1 --port 55173 --strictPort
$env:PLAYWRIGHT_CHANNEL = 'msedge'
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:55173'
npm run test:e2e -- tests/dashboard.spec.ts tests/declarations.spec.ts tests/derived-allergens.spec.ts tests/english-ui.spec.ts tests/formula-creator.spec.ts tests/formula-write-errors.spec.ts tests/impact-foundation.spec.ts tests/independent-readiness.spec.ts tests/labels.spec.ts tests/unsaved.spec.ts tests/validation-ui.spec.ts
npm run test:e2e -- tests/catalog-live.spec.ts --grep 'backend failure never falls back to seed data'
```

The offline npm restore first failed on an uncached yargs-parser package; normal
locked dependency restore succeeded without a source or lockfile change. The
delivery includes both logs, the build/test logs and 15 mock UI screenshots.
The live catalog, validation, formula-lifecycle and selected human RBAC cases
passed in separate runner invocations. The human-acceptance audit subprocess
assumes a Unix Docker socket/default Compose project and remains unexecuted here.
The full three-service Compose build was not used; runtime verification used the
local packaged backend and separate MySQL. Exact commands, reports and resource
inventories are included in the local delivery.

## Dependencies and remaining acceptance

SCRUM-77 and SCRUM-78 belong to Huang Xiangjia and were In Progress at the live
read. Their discovery/classification services were not implemented here.
RelevantProductLookupPort exists; current main and PR #56 have no production
lookup adapter. [PR #57](https://github.com/hxj04121-lab/FoodLabelFlow/pull/57) at
69d3aac1508fa888a3aa1d8a17aac7b5ad67c648 now adds a candidate adapter. The local M2
read-only review found lookup/discovery compatible with the unchanged port and
PR #56 contract. Missing published labels remain in the result and cause complete
discovery rejection with 422 PUBLISHED_LABEL_MISSING. Full analysis preconditions
before every write and transaction/concurrency behavior remain orchestration
acceptance; this PR's discovery tests do not prove that whole-run behavior.
Its independent CI run 37091021378 passed 408 backend tests and other checks, but
does not validate this separate adoption branch. PR #57 remains unmerged.
Adoption preserves supplier-material membership, keeping lookup semantics valid.

For SCRUM-78, use the published label's pinned formula N as the finding's current
formula, and the adopted product formula N+1 as its proposed formula. The old
label's `isCurrent()` can be false after adoption: it is a validation-eligibility
flag and must not be used as a published-label existence check.

This local change has not been pushed, opened as a PR, reviewed by M1, merged or
deployed. Jira has not been changed and no Day-4 Done marker is claimed. Next
acceptance requires publication authorization, a draft PR to main, real M1
review and the appropriate new-head CI evidence. Real local MySQL and full backend
verify now pass. Full duplicate
and concurrency acceptance remains SCRUM-56 / Day 5; SCRUM-48 stays In Progress.
