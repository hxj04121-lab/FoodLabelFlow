# FOOD M3/M4 Increment and Acceptance Evidence - 2026-10-21

Repository: `hxj04121-lab/FoodLabelFlow` monolith. This note records candidate test evidence and branch status; it does not claim Jira acceptance or completed demo acceptance.

## Current PR status

- PR #91 has merged to `main` as `b93e278c55d8acd98e200603e89dca1d89ed0cf7`. Its pre-merge branch head was `49ddf8b43820a941ae2831b0add2372aa0c858c4`.
- PR #92 is open and Draft on `codex/food-m3-m4-on-pr91`, with `main` as its base. It remains unmerged and depends on the changes now included in #91's merge commit.
- The current comparison against `main` contains 23 changed paths: the original 13-path M3/M4 increment, two browser-test corrections found while running CI (`frontend/tests/scrum84-identity-live.spec.ts` and `frontend/tests/s3-product-flow-live.spec.ts`), and this eight-path M4 history API/test/evidence increment. The batch-adoption UI test also uses the shared catalog fixture. The diff against merged `main` does not reintroduce #91's changes.
- The impacted local UI tests passed 14/14 after those corrections. This targeted local result does not replace the latest exact-head GitHub CI result; use PR #92's checks for that status.
- Complete demo acceptance, an independent member's replay, and independent security review or formal risk acceptance remain incomplete. No Jira issue is marked Done from this evidence.

## PR #91 upstream fix (historical review evidence)

- Before merge, PR #91 was reviewed at head `49ddf8b43820a941ae2831b0add2372aa0c858c4`. Review `5595159213` approved that exact SHA; the approval and checks below are historical pre-merge evidence.
- The fix locks the product before the first nonlocking replay lookup. The MySQL regression is a deterministic two-transaction interleaving test, not a latch or parallel-thread test.
- GitHub CI on that exact pre-merge head reported 684 backend tests with 0 failures/errors/skips; focused batch MySQL (2), service (9), and S4 golden path (3) passed. These are upstream results, not local-candidate test counts.
- Earlier Request changes review `5595005514` was on old head `52478cf...`; it was superseded by the author's fix.

## M4 product label history API

- Implements the frozen `GET /api/audit/products/{productId}/label-history` contract. The controller requires a resolved identity with `AUDIT.READ` before reading data; responses are read-only and product-scoped. Contract-defined fields include label versions/declarations, approval records, publication metadata, and events attached to the product's label versions or review tasks. No audit before/after values or unrelated product records are returned.
- `productDisplayName` is read from the current catalog description, consistent with the frozen contract. It is not represented as a historical name snapshot. Version declarations, creator/publisher identities and timestamps, approvals, publication timestamps/channels, and audit event actors/timestamps come from their corresponding stored records.
- `ProductLabelHistoryApiMySqlTest` seeds two products with separate two-version histories in the ephemeral MySQL database. It verifies the requested product's version ordering, exact declaration fields, approval and publication values, actor names, three chronological audit events, cross-product isolation, anonymous 401, missing `AUDIT.READ` 403, and missing-product 404. The fixture is removed by exact fixture IDs in `@AfterEach`; no business database or persisted test volume is involved.
- `mvn -DskipTests test-compile` passed with JDK 25.0.4. Final targeted run `mvn -Dtest=ProductLabelHistoryApiMySqlTest test` passed **2/2, 0 failures/errors/skips** against MySQL 8.4.11 / Docker Desktop 29.7.2 in a fresh Testcontainers database migrated through Flyway v11. The testCompile phase compiled all 123 test sources before execution. During fixture development, MySQL first rejected an unsupported declaration-source enum and then a missing impact-run `idempotency_key`; both were corrected to the actual migration contract before this passing run.

## Original exact-base M3/M4 increment

- Local worktree: `C:\Users\rcncai\AppData\Local\Temp\FoodLabelFlow-m3-m4-apply-check-20261021`.
- Original branch: `codex/food-m3-m4-on-pr91`, created from PR #91 head `49ddf8b43820a941ae2831b0add2372aa0c858c4`; the original M3/M4 increment changed 13 paths. The branch now targets `main`, which contains #91's merge. Old-base candidate `codex/food-m3-m4-delta-local` at `527e982...` and full recovery backup `bb2b2cc...` are preserved separately.
- Read-only GitHub data confirmed PR #91 has 29 changed paths and the local 13-path delta has no path overlap with that PR. Commit `49ddf8b...` itself changes only `BatchSpecificationAdoptionService.java`, `BatchSpecificationAdoptionMySqlTest.java`, `BatchSpecificationAdoptionServiceTest.java`, and the M1 A07 evidence file; none is in the M3/M4 delta.
- To make the precise base verifiable despite local Git DNS failure, I fetched the four exact blobs and commit metadata through the approved GitHub read-only connector. Reconstructing the tree from parent `52478cf...` yielded remote tree SHA `bd9e1b52d89ef15678dab0682b6dbf01835eedb5`; reconstructing the unsigned commit object yielded exact SHA `49ddf8b...`. The new local delta commit is based on this verified object. No remote write occurred during this reconstruction step; the later GitHub web-editor commits are recorded below.
- Key changes: development identity endpoint and fixture tests add Data Admin / Change Manager demo actors without grants; frontend identity switch checks minimum permissions; new batch-adoption client validates counts, target, and per-product results; new panel lists released/effective targets, gates the action on permissions, and pins ambiguous replay to the same target for the current mounted identity context; impact renders `productDisplayName` from the current catalog lookup and filters by outcome; human-acceptance API fixture now creates/releases a formula through the normal API before reading audit history.
- The 13 changed paths are:
  - `backend/src/main/java/com/spectrace/identity/interfaces/web/CurrentIdentityController.java`
  - `backend/src/test/java/com/spectrace/identity/DemoIdentityHttpTest.java`
  - `docs/evidence/FOOD-local-ui-permission-recovery-20261021.md`
  - `frontend/src/api/catalog.ts`
  - `frontend/src/api/identity-session.ts`
  - `frontend/src/api/identity.ts`
  - `frontend/src/api/impact.ts`
  - `frontend/src/components/BatchAdoptionPanel.tsx`
  - `frontend/src/pages/Impact.tsx`
  - `frontend/tests/batch-adoption-ui.spec.ts`
  - `frontend/tests/human-acceptance-api-evidence.spec.ts`
  - `frontend/tests/impact-workflow-ui.spec.ts`
  - `frontend/tests/scrum84-identity-switch.spec.ts`
- An earlier local `git fetch` failed because `github.com` DNS did not resolve. The exact base was instead reconstructed and verified from read-only source; the incremental patch is now generated against `49ddf8b...`.
- After the user's browser-connection recovery at 2026-10-21 05:52:58 UTC, a fresh CUA inventory exposed the designated Edge PR #91 tab and a separate `edge://extensions/?id=odlomjlbamekndcpllcnffbgeohgkmjh` settings tab. The PR tab is now readable; the visible GitHub account is `rcncai`, PR #91 is Open at `49ddf8b43820a941ae2831b0add2372aa0c858c4`, and all 6 checks pass. The repository's Open PR list contains only #91. Searching repository branches found no `codex/food-m3-m4-on-pr91` branch; a separate existing `codex/food-m3-m4-recovery-20261021` branch ends at the same `49ddf8b...` base and was not modified. Per parent direction, I first used GitHub's standard upload page on `codex/scrum-85-batch-adoption-impact` at `49ddf8b...`. The page displayed its upload form and commit choices; clicking `Choose your files`, pressing Enter with it focused, and clicking its visible coordinates left the empty page focused and did not open a chooser. No file-URL permission error surfaced. The extension setting remains unverified because its Edge internal settings tab cannot be claimed by CUA. At that initial attempt, no source files had yet been uploaded and no increment PR existed; this describes the unsuccessful upload-page attempt, not the later commits. GitHub integration write access previously returned 403 and was not retried.

## Test evidence and limits

- On the exact-base branch, JDK 25.0.4/offline Maven compiled 179 backend production and 121 test sources. `DemoIdentityHttpTest`, `CatalogBatchAdoptionHttpTest`, `BatchSpecificationAdoptionServiceTest`, `S4M2ContractAlignmentTest`, and `S3ImpactApiContractTest`: 37/37 passed, 0 failures/errors/skips. These selected tests used mocks; this run did not start Docker.
- On exact increment commit `ccd3d6c69784d478f4e37dbfbd6ef126c156a3df`, `mvn -o test` with JDK 25.0.4 and Testcontainers MySQL 8.4.11 completed: **688 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; 15:30**. Surefire reports are under `backend/target/surefire-reports`. This is local candidate evidence, separate from upstream PR #91 CI.
- GitHub CI run `39606632788` on exact M3 SHA `7377ea06d24e4c3555c8ee8172e2a0944da5cdb1` completed successfully across backend, frontend, containers, security, and SonarQube jobs. It validates that M3 SHA only; it does not validate the later M4 commit.
- On that same exact commit, `npm run build` passed: TypeScript build and Vite 7.3.7 production build transformed 2,011 modules. Vite emitted its existing large-chunk notice for the 628.37 kB JS bundle.
- `npm run test:e2e -- --list` enumerated 140 Playwright tests in 26 files. They were not executed on `ccd3d6c...`: Playwright's Chromium executable is absent. Installed Edge's isolated headless launch exited before assertions with `TransactNamedPipe: pipe ended (0x6D)`; no browser was installed. Historical run results must be kept with their exact candidate: the earlier pre-`bb2b2cc...` focused run had 17/20 pass and 3 batch-adoption fixture failures (missing released-target option and an unmocked catalog request reaching Vite's proxy with `ECONNREFUSED`); the later `bb2b2cc...` recovery candidate was reported as 134 passed/6 skipped in the full frontend suite and 20/20 in the focused suite. Neither result is for `ccd3d6c...`. The retained `FOOD-live-evidence-20261021/browser-output-*` artifacts contain separate Edge smoke/read-validation failures, not machine-readable reports for those 17/20, 134/6, or 20/20 runs; treat the latter counts as prior execution records, not independently replayable artifacts. No assertions were removed or skipped to hide failures.
- Fresh isolated Compose project `food-m3-m4-e2e-20261021` used project-local MySQL data and ports 23311 (DB), 28311 (API), 25311 (UI). Only the released Spec V2 input fixture was seeded; adoption, CR, impact, task, approval, and publication tables remained empty before live tests. No user demo or business data was touched.
- Chromium was absent. Installed Edge was launched headless with an isolated Playwright profile and `--remote-debugging-pipe`; it exited before tests reached assertions with `TransactNamedPipe: pipe ended (0x6D)`. The first run also logged Edge DualEngine temp-file replacement errors (`error=-5`) under the sandbox temp path. Retrying with task-local `TEMP/TMP` removed those file-replacement errors, but the named-pipe failure remained. The exact underlying Edge exit cause is unresolved; no live E2E result counts as a pass. No browser was installed, and existing browser tabs were not used for live acceptance.
- The batch-adoption panel clears its uncertain replay target when the identity generation or status changes. The existing identity-scoped route remount also discards unsaved panel state on same-actor refresh; evidence does not claim that Refresh preserves an uncertain retry target. Cross-identity recovery remains a known limitation pending an explicit recovery test.
- Isolated Compose services were stopped with `docker compose -p food-m3-m4-e2e-20261021 down` (no `-v`); the named test volume is retained. No test process remains.

## Acceptance still required

- The M3/M4 increment and this evidence file are on open Draft PR #92 from `codex/food-m3-m4-on-pr91` to `main`. It remains unmerged; #91 was merged at `b93e278c55d8acd98e200603e89dca1d89ed0cf7`. The current branch has 23 changed paths versus `main`, including two CI-driven test corrections beyond the original 13-path increment and the M4 history API/test files.
- Complete fresh M3 SOY browser journey, replacement-draft prefill/edit/stale notice, and another member's replay of the exact RC SHA.
- M4 history API, product isolation, chronological approval/publication/audit mapping, and anonymous/negative permission behavior now have local MySQL-backed test evidence. Another member's independent replay and independent security review or formal risk acceptance are still required.
- Complete M5 reset/regression on the exact final RC with first-failure and SHA-to-CI/Sonar/security/browser evidence, plus another member's replay.
- No Jira issue was closed from this evidence. Monolithic FOOD completion does not imply CN AWS deployment.
