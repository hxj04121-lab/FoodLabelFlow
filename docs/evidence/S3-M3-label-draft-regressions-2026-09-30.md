# M3 label-draft regression fixes

Date: 30 Sep 2026. Owner: Xu Feiyang / M3. Project: SWE5006 / SpecTrace / FoodLabelFlow.

## Pre-PR update — 3 Oct 2026

The reproduction and execution record below is historical. Submission is now
being checked on `main@e53f7b0`, safely fast-forwarded into XFY with the local
fixes intact. The changed frontend files had no overlapping upstream changes.
The [consumer review](S3-M3-contract-consumer-review-2026-09-30.md) now distinguishes
available change-request reads from still-missing impact/workflow integration.
This PR contains the existing label/validation reliability fixes and evidence;
it does not implement the SCRUM-71 selector or claim live S3 acceptance.

Uncertainty is guarded only in the mounted page/panel and resets on a reload
or remount. No persistent command tracking or backend reconciliation is added.

Fresh validation on 3 Oct used Node 24.19.0 and a dedicated Vite server at
`http://127.0.0.1:5193` against the current XFY working tree:

- `npm run build`: passed; the existing approximately 576 kB bundle advisory remains.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:5193 npx playwright test --grep-invert 'captures.*live' --reporter=list`:
  **75 passed, 3 skipped**. No live-write opt-in was enabled; backend-dependent
  cases remain unverified by this local run.
- `git diff --check` and all relative Markdown links in the three new evidence
  documents passed. No blocking issue was found in the scoped source review.
- This record predates the new PR's CI results and does not claim live S3 or
  backend acceptance. Backend and CI source were not changed by these fixes.

## Scope and preserved work

Repository: `C:/Users/16023/Desktop/SWE5006_Project/FoodLabelFlow`.
Branch remains `XFY`, HEAD `86c67d2cb2408f16c2e1846f3b7fcbaf880ee149`.
This round extends the existing frontend fixes without synchronizing/merging,
committing, pushing, deploying or changing teammate-owned interfaces.

Before this round, the working tree already contained changes to the label API
client, validation panel and validation tests, plus the untracked deployment
document and [previous independent-work record](S3-M3-independent-validation-2026-09-30.md).
They were preserved. Current files were copied into an isolated verification
workspace; pre-round copies of all affected and existing changed files/documents
are under `C:/Users/16023/Documents/Codex/2026-09-30/task/before-label-draft-fixes`.
No personal memory or parent-directory handoff was changed.

## Reproduction and implemented behavior

Five new browser checks reproduced the suspected problems against the unchanged
pre-round draft implementation, with explicit HTTP fixtures:

| Check | Original behavior reproduced | Implemented behavior |
| --- | --- | --- |
| Successful create response has an incomplete draft body | INVALID_RESPONSE appears but another create remains available | Keep the write uncertain and disable another create |
| Successful create response has invalid JSON | INVALID_RESPONSE appears but another create remains available | Keep the write uncertain and disable another create |
| Connection is lost after create; an existing draft is then read | A historical read clears the unknown-create state | Allow the exact read but retain uncertainty and explain that the create remains unconfirmed |
| Product changes after unknown create | Selection clears the lock, including when switching back | Keep the page's uncertain-create lock and guidance across product changes |
| GET requests one label ID but returns another valid draft | The other draft is accepted and dependent reads can start | Reject with client-side LABEL_VERSION_MISMATCH; display no new draft or bound requests, and allow a subsequent correct read |

`frontend/src/pages/Labels.tsx` now treats invalid write responses as uncertain,
preserves that state during history reads/product selection, and gives explicit
guidance. `frontend/src/api/labels.ts` verifies the exact returned label ID in
`getLabelDraft()`. Its previously added validation-run ID check is retained.
`frontend/tests/labels.spec.ts` adds the five regressions, including read recovery
and checks that rejected targets trigger no derived/declaration/validation reads.

`LABEL_VERSION_MISMATCH` is a local response-consistency error, not a proposed
server error code. Confirmed server errors retain their existing handling.
No endpoint, identity, DTO, server transition or dependency was changed.

The uncertainty lock remains component-local. It does not supply persisted
cross-page recovery or a way to correlate an unknown command with a server
outcome. Reconciliation still requires the owner contract; loading an old
version or switching a product is not evidence that a creation failed.

## Validation

Runtime: existing dependencies and bundled Node 24.19.0. Verification source:
`C:/Users/16023/Documents/Codex/2026-09-30/task/FoodLabelFlow-verification/frontend`.
Vite ran on isolated localhost port 5187, using an isolated cache and the
runner config loader. TypeScript build-info files stayed in the verification
copy. Source files were refreshed from the actual working tree, including
earlier uncommitted fixes.

- Baseline: **5 focused checks failed**, exposing the expected incorrect states.
- An intermediate build caught the new ID guard in the wrong function. The
  placement was corrected in the verification copy before any original-repo
  write, then the complete regression and build were rerun.
- Final complete independent frontend regression: **75 passed, 3 skipped**
  (78 selected; the two live capture cases were excluded by the CI rule).
- TypeScript application/config checks and Vite production build: **PASS**.
  The existing bundle advisory remains, approximately 576 kB minified JS.

The complete independent suite follows the repository CI selection:

```powershell
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:5187'
node node_modules/@playwright/test/cli.js test --grep-invert 'captures.*live' --reporter=list
node node_modules/typescript/bin/tsc -p tsconfig.app.json --incremental --tsBuildInfoFile .verification-build/app.tsbuildinfo
node node_modules/typescript/bin/tsc -p tsconfig.node.json --incremental --tsBuildInfoFile .verification-build/node.tsbuildinfo
node node_modules/vite/bin/vite.js build --configLoader runner
```

No LIVE_CATALOG/LIVE_WRITES/LIVE_VALIDATION opt-in was set. Real catalog,
full-stack formula-write and validation cases therefore remain skipped; the
two live human-acceptance capture cases are excluded by the existing CI grep
rule. These fixture tests verify frontend behavior, not backend enforcement,
real S3 acceptance, main CI or independent human review.

Raw local logs under `C:/Users/16023/Documents/Codex/2026-09-30/task`:
`draft-baseline-reproduction.log`, `draft-full-frontend-regression-final.log`,
and `draft-production-build-final.log`.

## Independent completion and dependencies

The requested independent scope is complete locally: the draft reliability fixes and the
[local M3 consumer review](S3-M3-contract-consumer-review-2026-09-30.md) form this
round's independent scope. The review describes candidate shapes, questions and
owner decisions; it is not a contract freeze or a submitted team approval.

Integrated S3 work stays paused:

- M2 Cai Runchen: accepted/frozen contracts and corrected/confirmed finding
  meaning, complete-result semantics, SOY oracle and negative controls.
- M1 Huang Xiangjia: real change/impact/query/task-generation APIs and supported
  explicit Spec V2/Formula N+1 setup, consistent exact-version references.
- M4 Zhu Wenyu: task read/binding, submission/decisions/publication operations,
  allowed actions, backend guards, login/user switching and test identities.
- M5 Sun Huajian with owners: command outcome correlation/reconciliation,
  durable persistence/audit/publication, reproducible environment and main CI.

The reviewed candidate remains at already-fetched `origin/main@0755d06` and is
still marked `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`. It was read through
Git without copying its owner files into the current branch. No Jira access or
browser workaround was attempted. Recheck integrated repository contracts,
implementation/tests and main CI before resuming SCRUM-71/72/73/74. Do not
interpret this independent work as S3 task completion or infer member lateness.
