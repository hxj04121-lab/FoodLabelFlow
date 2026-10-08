# M3 independent validation fixes and S3 waiting conditions

Observed: 30 Sep 2026. Owner: Xu Feiyang / M3. Related: SCRUM-49, SCRUM-72/74.

## Pre-PR update — 3 Oct 2026

The execution details and dependency assessment below record 30 Sep. XFY has
now been fast-forwarded to `main@e53f7b0` with these local fixes preserved for
review and submission. The changed frontend files had no overlapping upstream
changes. For current integration availability, see the 3 Oct update in the
[consumer review](S3-M3-contract-consumer-review-2026-09-30.md); change-request
list/detail reads can now be integrated independently of impact execution.

The uncertainty protections remain component-local. Refreshing or remounting
the page/panel resets them; this PR does not provide durable idempotency or
server-correlated command recovery. Historical reads are available for
inspection, but do not unlock an uncertain write in the mounted component.

## 8 Oct 2026 source availability and review cleanup

The execution and dependency sections below remain **30 Sep observations**.
Their references to missing integrated implementations describe that baseline,
not the current repository. At `main@18136397d696885ae678af4ec5776c5f7fdea321`,
source inspection confirms the delivered
[impact trigger/query controller](https://github.com/hxj04121-lab/FoodLabelFlow/blob/18136397d696885ae678af4ec5776c5f7fdea321/backend/src/main/java/com/spectrace/impact/interfaces/web/ImpactAnalysisController.java),
[replacement-draft binding](https://github.com/hxj04121-lab/FoodLabelFlow/blob/18136397d696885ae678af4ec5776c5f7fdea321/backend/src/main/java/com/spectrace/workflow/infrastructure/JdbcReviewTaskDraftBinding.java),
[Java submit/decision/publication service](https://github.com/hxj04121-lab/FoodLabelFlow/blob/18136397d696885ae678af4ec5776c5f7fdea321/backend/src/main/java/com/spectrace/workflow/application/LabelReviewService.java)
and [injected workflow policies](https://github.com/hxj04121-lab/FoodLabelFlow/blob/18136397d696885ae678af4ec5776c5f7fdea321/backend/src/main/java/com/spectrace/workflow/infrastructure/WorkflowInfrastructureConfiguration.java).
These delivered components supersede the historical missing-source statements;
they do not establish a complete browser workflow, command reconciliation or
live publication acceptance. The
[contract at this revision](https://github.com/hxj04121-lab/FoodLabelFlow/blob/18136397d696885ae678af4ec5776c5f7fdea321/docs/contracts/s3-impact-review-publication-api-v1.yaml)
still declares `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`.

M1's six document-cleanup comments on [PR #58](https://github.com/hxj04121-lab/FoodLabelFlow/pull/58)
are addressed by repository-relative descriptions and public CI references.
The original PR-head CI evidence is
[run 37120575734](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37120575734),
including its successful
[frontend production-build job](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37120575734/job/111195716860).
This is evidence for PR head `563068e6`, not a new main validation run or a
claim that the historical local test suites were rerun on 8 Oct.

## Verified project and baseline — 30 Sep 2026

- Repository: [hxj04121-lab/FoodLabelFlow](https://github.com/hxj04121-lab/FoodLabelFlow).
- Identity: SWE5006 / SpecTrace / FoodLabelFlow, TEAM 16, as recorded by the
  repository README and team work orders.
- Remote: `https://github.com/hxj04121-lab/FoodLabelFlow.git`.
- Working branch: `XFY`; HEAD remains `86c67d2cb2408f16c2e1846f3b7fcbaf880ee149`.
- On this date, `git ls-remote` and `git fetch origin main XFY` verified latest
  main as `0755d061ee9a0a89ce7854a147c9e49453490f13` and remote XFY as
  `c49b59ca45d87b37caf528a7c481ac11c3989b47`. The pre-existing `origin/XFY`
  tracking ref remains old; it was not used as the live branch result.
- Latest main has merged PR #48 (M2 Day 2 candidate) and PR #51 (M3 foundation).
  Source, frontend tests and dependency manifests at working HEAD and this main
  are identical before these fixes. Only M2 contract/evidence/test additions
  differ. No local merge, branch switch, commit or push was performed.
- Original untracked `docs/TENCENT_LIGHTHOUSE_DEPLOYMENT.md` is preserved and is
  outside this change. No personal handoff or local memory was modified/copied.
- No applicable repository/ancestor `AGENTS.md` or `.agents/skills` was found.
- Current frontend uses React 19, TypeScript, Tailwind/shadcn and Vite. Older
  proposal/README progress descriptions are historical snapshots.

Jira access to TEAM 16 was denied; no browser or alternate Jira access was
attempted. Issue ownership/scope comes from the existing local handoff and work
orders, not a fresh Jira status claim. No member is described as overdue.

## Independently completed changes

The existing, frozen S2 validation API and frontend are sufficient for these
fixes; they require no new S3 endpoint, DTO, authentication or backend work.

1. Invalid JSON or a malformed successful validation-write response now keeps
   the write outcome uncertain and disables another validation submission.
   A transport/schema failure cannot prove that the server did not commit.
2. Loading an older persisted validation run for the same label/rule set no
   longer clears the uncertain-write lock. Reading history does not identify
   the outcome of that particular command. The UI explains this distinction.
3. A validation read verifies its returned `validationRunId` against the ID
   requested in the URL, in addition to the existing label/rule-set checks.
   `VALIDATION_RUN_MISMATCH` is a frontend response-consistency error, not a
   newly proposed server error code. A subsequent correct read can recover.
4. Existing version-mismatch fixtures now return their requested run ID, so
   label/rule-set mismatch coverage remains distinct from run-ID coverage.

The uncertainty lock is local component state. This change adds no persisted
command tracking, cross-page recovery or owner API for reconciliation. A known
old run is never presented as confirmation of the uncertain write. Recovery
that correlates a command with its outcome still needs the owner contract.

## Validation evidence

An isolated copy of the working tree was tested with the existing repository
dependencies and bundled Node 24.19.0.
Vite used localhost port 5187, a local cache and the runner config loader;
TypeScript build-info files were redirected into the verification copy.
No existing user dev server was reused.

- Before the code fix, all four focused checks failed at the expected missing
  uncertainty/identity protections: malformed body, invalid JSON, historical
  run after an uncertain write, and a different returned run ID.
- TypeScript application and Vite-config checks passed (`tsc -p` for each
  referenced project, using isolated build-info paths).
- Vite production build passed. The existing bundle-size advisory remains
  (approximately 576 kB minified JS); it is not a build failure.
- Final selected Playwright suite: **53 passed**, covering validation UI,
  labels, declarations, derived facts, and impact foundation. The initial
  wider run exposed the old inconsistent version-mismatch fixture; it was
  corrected before the final run.

Selected suite (from `frontend/`):

```sh
npx playwright test tests/validation-ui.spec.ts tests/labels.spec.ts tests/declarations.spec.ts tests/derived-allergens.spec.ts tests/impact-foundation.spec.ts
```

Local execution used `PLAYWRIGHT_BASE_URL=http://127.0.0.1:5187` and the existing
Playwright CLI directly. These are explicit HTTP-fixture frontend regressions;
they are not live S3 API, main CI, independent review or publication acceptance.
Publicly reviewable automated build evidence is provided by
[PR #58 CI run 37120575734](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37120575734)
and its [frontend job](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37120575734/job/111195716860).
The local results above remain dated observations; the frontend CI job verifies
the production build and does not prove live S3 acceptance.

## Historical dependencies awaiting owner delivery — 30 Sep 2026

The M2 Day 2 contract in verified main is still marked
`CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`; merging it did not mark it frozen.
Reviewed source: [candidate at the verified main revision](https://github.com/hxj04121-lab/FoodLabelFlow/blob/0755d061ee9a0a89ce7854a147c9e49453490f13/docs/contracts/s3-impact-review-publication-api-v1.yaml).
The latest main has only `ImpactModuleBoundary` in the impact module and no S3
review/publication HTTP controller. Implementation on unmerged member branches
was not treated as available integration.

Names below are verified from the local team work orders; S3 module scopes
are documented in the M2 candidate and M3 personal handoff.

| Paused M3 work | Responsible owner | Required interface / condition to resume |
| --- | --- | --- |
| SCRUM-71 impact client and real findings | M2 Cai Runchen (SCRUM-48/53), M1 Huang Xiangjia (SCRUM-47) | Accepted/frozen change/run/finding contract plus integrated real APIs. Candidate routes currently cover POST/GET change requests, POST change-request impact analyses and GET impact analyses. Confirm final fields, permissions, replay and errors before wiring them. |
| Impact test setup and outcome oracle | M2 Cai Runchen, M1 Huang Xiangjia | Owner-supported released Spec V2/adopted Formula N+1 setup, current published-label prerequisites, and the SOY product/outcome map including excluded controls. Do not create adoption logic in M3. |
| SCRUM-72 ReviewTask and replacement validation integration | M4 Zhu Wenyu (SCRUM-50), with M1/M2 | Task query/detail and creation/binding boundary must identify the exact replacement draft. Confirm null `draftLabelVersionId` handling and assignee rule. Reuse validation only after this context is available. |
| SCRUM-73 submit, decisions, publication and actor switching | M4 Zhu Wenyu, M2 Cai Runchen | Real submission/APPROVE/REQUEST_CHANGES/REJECT/publication APIs, allowed actions, backend guards, and M4 login/identity decision. Confirm whether approval publishes or a separate command is required. Existing fixed local S2 headers are not an S3 user-switch contract. |
| Unknown-command recovery and SCRUM-74 live acceptance | M4 Zhu Wenyu, M5 Sun Huajian (SCRUM-51), with module owners | Command correlation/reconciliation, durable task/publication/audit results, reproducible isolated data and maker/checker/restricted identities. Complete real main-CI E2E and permission negatives only after the chain is integrated. |

These portions remain paused. No temporary endpoint, seed fallback, local
workflow simulation, backend implementation or teammate messaging was added.
The existing [test design](S3-M3-test-design.md) and [A07 draft](S3-M3-A07-draft.md)
remain preparation materials. Their final diagrams and acceptance evidence must
be reconciled with the delivered owner interfaces.

Next: recheck the integrated contract/API/login conditions above, then resume
SCRUM-71, followed by SCRUM-72/73 and real SCRUM-74 acceptance. Local fixes and
fixture checks do not mark any of these Jira tasks Done.
