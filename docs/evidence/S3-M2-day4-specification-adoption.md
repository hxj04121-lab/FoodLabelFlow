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
| FormulaSpecificationAdoptionMySqlTest | ENVIRONMENT_BLOCKED: attempted 4 tests, 4 initialization errors, zero assertion failures. Testcontainers could not find a valid Docker environment; test bodies did not run. |
| Frontend locked dependency restore and production build | PASS: npm ci, TypeScript and Vite. Local Node 24.14.0 differs from CI Node 22. Existing 575.62 kB chunk warning remains. |
| Existing mock API browser regressions, installed Edge | PASS: 66 cases plus 1 catalog backend-failure fixture, zero failures/skips. These do not exercise a real backend or today's adoption transaction. |
| git diff --check | PASS. |
| Full backend verify, real-backend/browser/Compose E2E, security/Sonar, new-head CI | NOT RUN for this change. |

Docker Desktop failed during startup while creating its `dockerInference` IPC
listener: "The file cannot be accessed by the system." A controlled launch of the
existing application in the owner's Windows session at 03:08 UTC reproduced the
same fatal error while removing
`C:\Users\rcncai\AppData\Local\Docker\run\dockerInference`. A bounded
`docker info --format {{.ServerVersion}}` probe timed out after 10 seconds. Native
WSL enumeration worked, with the docker-desktop distribution stopped. An attempt
to start the existing helper service lacked OS permission; that service is not
established as the cause and is not required for the WSL2 Linux engine.

Start-only recovery did not restore the engine. No settings, permissions, IPC
files, images, volumes or credentials were changed. Database-dependent checks
were not blindly repeated. Further recovery requires a separately approved,
narrowly scoped IPC repair decision; no reset or broad cleanup is proposed.
No fallback database or skipped-as-pass result was used. The four
MySQL tests use the existing harness and isolated fixtures to assert exact old
formula/items/label preservation, all copied fields, current pointer and audit
payload, rejected unauthorized/invalid requests, and rollback after a real audit
insert throws. Those assertions remain unverified until a working Docker runtime
executes them.

Reproduce the passing focused checks from the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4'
mvn -B -ntp -f backend/pom.xml '-Dtest=CatalogSpecificationAdoptionTest,CatalogSpecificationAdoptionHttpTest,CatalogRulesTest,ArchitectureTest,ValidationArchitectureTest,SharedApiErrorContractTest,OpenApiContractTest,S3ImpactApiContractTest' test
```

With a working Docker engine, run:

```powershell
mvn -B -ntp -f backend/pom.xml '-Dtest=FormulaSpecificationAdoptionMySqlTest' test
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
Live catalog, formula-lifecycle and validation cases remain unexecuted. The
human-acceptance audit subprocess also assumes a Unix Docker socket and requires
a Windows-compatible execution decision before it can run here.

## Dependencies and remaining acceptance

SCRUM-77 and SCRUM-78 belong to Huang Xiangjia and were In Progress at the live
read. Their discovery/classification services were not implemented here.
RelevantProductLookupPort exists, but neither current main nor PR #56 has a
production lookup adapter. Runtime discovery requires that additional M2-owned
adapter delivery. Adoption preserves supplier-material membership, so the lookup
semantics and relevance set stay compatible.

For SCRUM-78, use the published label's pinned formula N as the finding's current
formula, and the adopted product formula N+1 as its proposed formula. The old
label's `isCurrent()` can be false after adoption: it is a validation-eligibility
flag and must not be used as a published-label existence check.

This local change has not been pushed, opened as a PR, reviewed by M1, merged or
deployed. Jira has not been changed and no Day-4 Done marker is claimed. Next
acceptance requires publication authorization, a draft PR to main, real M1
review, executable MySQL evidence and the appropriate CI evidence. Full duplicate
and concurrency acceptance remains SCRUM-56 / Day 5; SCRUM-48 stays In Progress.
