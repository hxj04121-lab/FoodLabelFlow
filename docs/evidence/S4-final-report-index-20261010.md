# Sprint 4 final report index and M2 contract review

Date: 10 October 2026. Owner: Cai Runchen / M2. Jira: SCRUM-86.

This is an evidence index and contract review, not a Sprint 4 acceptance statement.
It records the current main snapshot and links the available member use cases,
analysis/design diagrams and design-problem decisions. S4 task artifacts are marked
pending where the owner work has not landed.

## Baseline and contract review

The GitHub API reported `main` at `80297ae89d825bd937002fecb912ee4cfc9378d8`.
This local worktree is based on `417133e947a2bc9c1a771bbe69b50b6498c1eee6`
(the PR #87 head), because native `git fetch` could not resolve `github.com` and
that commit was the newest available local object. A final recovery check found that the `80297ae` main object was still absent locally and DNS resolution for `github.com` still failed, so the candidate could not be rebased or cherry-picked onto current main. GitHub compare reports main 13
commits ahead of this baseline, with 19 changed paths across CI, docs and frontend
and no backend paths. The S3 OpenAPI/error matrices, impact/workflow controllers and
existing backend contract tests were also compared by blob SHA and matched. The
focused backend checks therefore use the same backend sources as main; this worktree
is still not a checkout-wide main verification.

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

The existing impact read model remains M1-owned. Its response and OpenAPI already
carry `relevantProductCount`, `noActionCount`, `reviewRequiredCount` and the
`NO_ACTION` / `REVIEW_REQUIRED` discriminator, and the current-identity schema
already carries `displayName`. No duplicate read-model field was added. The new
contract test compares the impact OpenAPI properties to the existing Java response
record. If SCRUM-85's “display name” means a separate product display field, the
current `ImpactAnalysisResponse` and impact contract do not identify that field;
M1 should name its intended source and canonical property before M2/M3 freeze it.

The next cross-module acceptance gates remain: M1 confirms the final additive
read-model names and adoption/reset handoff; M3 records consumer acceptance of the
impact and label payloads; M4 records the identity/history/audit boundary and the
history read API needed by the demo; M5 supplies the fresh reset/regression receipt.
This work records no teammate approval and does not invent a contract for an API
that is not yet implemented.

## A07 use cases, diagrams and design problems

| Member | Current use case and diagram sources | Design problem / decision | Sprint 4 status |
| --- | --- | --- | --- |
| M1 | [Run Change Impact Analysis A07](S3-M1-A07-run-change-impact-analysis.md) includes analysis/design class and sequence diagrams. | [Impact Strategy design problem](S3-M1-A07-impact-strategy-draft.md). | SCRUM-85 adoption/read-model/reset evidence pending owner delivery. |
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
| Offline Maven contract suite on JDK 25 (`S4M2ContractAlignmentTest`, `S3ImpactApiContractTest`, `OpenApiContractTest`, `SharedApiErrorContractTest`) | **PASS - 14 tests, 0 failures/errors/skips.** Includes route/schema parity for the returned-revision and demo-options consumers and DTO/OpenAPI parity for the existing M1 impact response. |
| `git diff --check` and relative links in this report | **PASS.** |
| `DemoIdentityHttpTest` on JDK 25 | **PASS - 15 tests, 0 failures/errors/skips.** Mockito Core 5.23.0 was supplied as a test-process `-javaagent`; no POM or OS security settings were changed. |
| Current-main verification and push | **Blocked.** The current `main` object was unavailable locally and DNS still failed at the final recovery check. No remote branch or PR was created; all candidate commits remain local and based on `417133e`. |
| Jira SCRUM-86 | **In Progress.** Local candidate progress, passing contract checks and outstanding acceptance blockers are recorded in Jira comment `10222`; cross-module acceptance remains pending. |
| Docker-backed integration and full Sprint 4 live demo | **Not run.** No usable Docker engine was available for this task. |
