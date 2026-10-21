# Sprint 4 final report index and M2 contract review

First created: 10 October 2026. Updated: 14 October 2026. Owner: Cai Runchen / M2. Jira: SCRUM-86.

## 14 October candidate update

M2's S4 contract revision, contract tests and A07 consolidation are summarized in
[S4-M2 contract-freeze evidence](S4-M2-contract-freeze-20261014.md). This index
does not claim ownership of the other members' implementation work or record
their acceptance. The cross-module contract review remains pending.

This is an evidence index and contract review, not a Sprint 4 acceptance statement.
It records the current main snapshot and links the available member use cases,
analysis/design diagrams and design-problem decisions. S4 task artifacts are marked
pending where the owner work has not landed.

## Baseline and contract review

The GitHub API reported `main` at `80297ae89d825bd937002fecb912ee4cfc9378d8`.
This local worktree is based on `80297ae89d825bd937002fecb912ee4cfc9378d8`,
the current `origin/main` head. After GitHub DNS recovered, `git ls-remote` and
`git fetch --no-tags origin main` both succeeded; the final remote-ref check returned
the same SHA. Branch `codex/s4-scrum-86-main-80297ae` was created from that main,
and both candidate commits were cherry-picked without conflicts. The original
candidate branch/worktree remains unchanged. The implementation and tests below
were run at code head `9129e74890abe84c543a08b175f0859fc77a6675` on this main-based
branch. GitHub's earlier compare of main to the PR #87 baseline showed 13 commits
ahead and 19 changed paths in CI/docs/frontend, with no backend paths; this run
verifies against a full main checkout.
PR #76 and PR #88 are merged; PR #88 integrates PR #87's returned-task revision
implementation and the M3 identity/context flow. GitHub returned no PR #88 review
submissions. The main commit status and workflow-run queries returned no records in
this snapshot, so this report does not claim current-main CI is green. The issue
records for SCRUM-85/87/88/89 were still in `Idea` when checked, with no comments or
linked issues.

The S3 label contract already describes `POST /api/review-tasks/{reviewTaskId}/draft-revisions`,
but its error matrix still called the operation a local proposal and the identity
switch a proposal. The merged M3 client also calls `GET /api/identity/demo-options`,
which was absent from OpenAPI. This change updates the label contract to v1.2.0,
documents that development-only read with its existing feature gate, and corrects
the stale revision/identity language. The contract remains
`CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE` until owners record their review.

The impact read model remains M1-owned. The S4 contract now names its additive
product display field `productDisplayName` alongside `relevantProductCount`,
`noActionCount`, `reviewRequiredCount` and the `NO_ACTION` / `REVIEW_REQUIRED`
discriminator. M2 documents the consumer contract without duplicating M1's
implementation. Producer confirmation and consumer acceptance remain open review
items.

The next cross-module acceptance gates remain: M1 confirms the final additive
read-model names and adoption/reset handoff; M3 records consumer acceptance of the
impact and label payloads; M4 records the identity/history/audit boundary and the
history read API needed by the demo; M5 supplies the fresh reset/regression receipt.
This work records no teammate approval and does not invent a contract for an API
that is not yet implemented.

## A07 use cases, diagrams and design problems

| Member | Current use case and diagram sources | Design problem / decision | Sprint 4 status |
| --- | --- | --- | --- |
| M1 | [Run Change Impact Analysis A07](S3-M1-A07-run-change-impact-analysis.md) includes analysis/design class and sequence diagrams. S4: [Adopt a Released Specification for Current Products A07](S4-M1-A07-adopt-specification-for-current-products.md) adds the use case, analysis/design class and design sequence diagrams for batch adoption and the impact read-model names. | [Impact Strategy design problem](S3-M1-A07-impact-strategy-draft.md); S4 per-product Unit of Work + idempotency-key decision in the S4 A07. | SCRUM-85 delivered by the owner: batch adoption, `productDisplayName`, and the no-SQL demo path, with [evidence](S4-M1-SCRUM-85-demo-adoption-impact.md). M3 UI entry and M4 identity options are tracked in SCRUM-87/88. |
| M2 | [S3 Day 6 A07 evidence index](S3-M2-day6-a07-evidence.md) links the [adoption use case](../architecture/S3-M2-adoption-sad.md), [analysis class](../architecture/diagrams/s3-m2-adoption-analysis-class.mmd), [analysis sequence](../architecture/diagrams/s3-m2-adoption-analysis-sequence.mmd), [design class](../architecture/diagrams/s3-m2-adoption-design-class.mmd), [design sequence](../architecture/diagrams/s3-m2-adoption-design-sequence.mmd) and [pattern decision](../architecture/S3-M2-adoption-pattern-decision.md). The [S1 analysis/design](../architecture/S1-M2-analysis-design.md) and [S1 SAD](../architecture/S1-M2-sad.md) remain the earlier baseline. | Adoption's immutable-snapshot and transaction decision; this report records the S4 contract mismatch and acceptance boundary. | SCRUM-86 M2 contract revision/test delivered as a candidate; cross-module approval pending. |
| M3 | [M3 impact/review/publication A07](S3-M3-A07-draft.md) contains the updated integration sequences and async-consistency design problem; [M3 consumer review](S3-M3-contract-consumer-review-2026-09-30.md) retains its dated decisions. | Async responses must not replace the selected identity, task or URL context; no UI permission check substitutes for backend enforcement. | SCRUM-87 final demo review and another-member rehearsal pending. |
| M4 | [SCRUM-84 identity/workflow sequences](../architecture/SCRUM84-A07-state-and-sequences.md) and [login/demo-switch ADR](../architecture/ADR-SCRUM84-login-demo-switch.md). | Actor identity, maker-checker and workflow transitions stay server-authoritative. | SCRUM-88 history/audit/security and S4 contract review pending; no history endpoint is added here. |
| M5 | [SCRUM-70 persistence and publication A07](../s3-m5/SCRUM-70-A07-atomic-persistence.md) includes the use-case flow, class diagram, sequence/failure paths and transaction rationale. | Keep impact persistence and publication atomic within their existing unit-of-work boundaries. | SCRUM-89 fresh reset/regression evidence and independent rerun pending. |

These links are the current S1–S3 evidence base. They are not substitutes for each
member's SCRUM-85–89 S4 use case, diagrams, pattern issue or review record. Each
owner should add the S4 artifact and exact tested commit to the table before the
final report is called complete.

## Checks for this M2 change

| Check | Result |
| --- | --- |
| Offline Maven contract and demo HTTP tests on JDK 25 (`S4M2ContractAlignmentTest`, `S3ImpactApiContractTest`, `OpenApiContractTest`, `SharedApiErrorContractTest`, `DemoIdentityHttpTest`) | **PASS - 29 tests, 0 failures/errors/skips.** Mockito Core 5.23.0 was supplied as a process-only `-javaagent`. |
| `git diff --check` and relative links in this report | **PASS.** |
| `ReturnedDraftRevisionHttpMySqlTest` with Testcontainers MySQL 8.4.11 | **PASS - 10 tests, 0 failures/errors/skips.** Docker Engine 29.7.2. Includes real HTTP/database validation, revision, rollback, concurrency and publication checks. |
| Latest-main base and branch | **PASS.** Branch base is `80297ae89d825bd937002fecb912ee4cfc9378d8`; the two earlier M2 candidate commits cherry-picked cleanly. |
| E2E topology and artifacts | Disposable Compose project `foodlab-scrum86-20261011`, dedicated MySQL volume and loopback port 13308; local JDK 25 backend on 18081; built frontend container on 15174 with an ignored, read-only test proxy override. Raw observations and screenshot are in `frontend/test-results/revision-evidence-foodlab-scrum86/`. Backend Docker image build was stopped after Maven `dependency:go-offline` stalled; the same main-based jar was packaged locally and used for the passing E2E. |
| Local package build | **PASS.** `mvn -o -DskipTests package` built the main-based backend jar. |
| Full backend suite | **Not run.** Validation covered the contract/demo HTTP group, the returned-revision HTTP/MySQL integration class, and the requested live revision browser flow; no `mvn clean verify` or full Sprint 4 signoff is claimed. |
| Jira SCRUM-86 | **In Progress.** Progress and earlier checks are recorded in Jira comment `10222`; no implementation defect was found in this run. Cross-module acceptance remains pending. |
| Live returned-revision browser E2E | **PASS - 1 test.** Real browser created V2, required fresh V2 `PASSED` validation, obtained Checker approval, published through Publisher, and verified immutable V1, zero-write denial paths, task `CLOSED`, and V2 current/published. Task `3e0980a5-e865-4d69-a395-74611912f155`; V1 `label_0780ff362d1b4a7f9d5dc009452aaf26`; V2 `label_ac475d0fb1134f69adeb582e2586ab0d`; validation `5020745f-0bae-4268-8031-27111b055d80`. |
