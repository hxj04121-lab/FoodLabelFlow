# S3 M2 Day 6: cross-module review and freeze ledger

Owner: Cai Runchen / M2. Jira: SCRUM-57 under SCRUM-48.
Observed: 2026-10-06, Asia/Shanghai. State: **candidate; acceptance incomplete**.

This ledger reconciles recorded review with the current candidate. It distinguishes
an implemented/documented response, a GitHub thread's resolution state and an owner's
acceptance. Only an attributable owner decision can satisfy the last condition.

## Reviewed baseline and prerequisite state

Main remains `e7512c638dd8661cff7e6c64aaf978a8acba3bf0`, containing the merged golden
[PR56](https://github.com/hxj04121-lab/FoodLabelFlow/pull/56), catalog lookup
[PR57](https://github.com/hxj04121-lab/FoodLabelFlow/pull/57) and M1 strategy
[PR59](https://github.com/hxj04121-lab/FoodLabelFlow/pull/59).
[PR61](https://github.com/hxj04121-lab/FoodLabelFlow/pull/61) remains an unmerged draft
at `d6ca803`, based on main. [PR62](https://github.com/hxj04121-lab/FoodLabelFlow/pull/62)
remains an unmerged draft at `20a41e4`, based on PR61's branch. Both have zero recorded
reviews and review threads. The Day 6 design is traceable to that Day 5 implementation
baseline; it is not represented as already integrated on main.

Jira assigns SCRUM-57's A07 diagrams, pattern decision and cross-module review work
to M2. Its code/test prerequisites are available on the dependent branches, so the
documentation can be delivered independently. Its final freeze requirement cannot
be satisfied by assuming a calendar date, an earlier ticket status or a merge.
Day 7 SCRUM-58 and parent SCRUM-48 closure remain gated on accepted, merged current-main
evidence.

## Actual review records

| Artifact | Observed record | What it establishes |
| --- | --- | --- |
| [PR46, Day 1 candidate](https://github.com/hxj04121-lab/FoodLabelFlow/pull/46) | Merged; no reviews or human comments. Review requests recorded. | Candidate integration and requested review; no explicit cross-module acceptance. |
| [PR48, Day 2 contract](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48) | Merged 2026-09-30. M1 `hxj04121-lab` submitted one `COMMENTED` review on 2026-09-29, explicitly withholding approval. Ten threads remain unresolved; five are outdated, with no later thread replies. | Actual M1 findings and a remaining re-review requirement. An outdated diff is not an accepted resolution. |
| [M2 remediation notice](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#issuecomment-5885837207) | M2's own notice lists corrections and green CI, and states that re-review/acceptance is pending. | An attributable remediation claim, not a reviewer decision. |
| [M1 Jira acknowledgement](https://hxj04121.atlassian.net/browse/SCRUM-75?focusedCommentId=10096) | M1's own 2026-10-01 comment confirms that the contract-review items were incorporated into merged PR48. | Real human acknowledgement of incorporated corrections. It does not explicitly approve the complete current adoption/golden/contract set or a Day 6 freeze. |
| PR56 / PR57 / PR59 | Merged; no recorded reviews or review threads in the queried records. | Executable golden/lookup/strategy baselines, not M1/M3/M4/M5 approval of the entire contract set. |
| PR61 / PR62 | Open drafts, no human reviews or threads. PR61's only comment is the earlier Sonar result. | Existing implementation and automated checks; no independent acceptance. |
| Jira SCRUM-49 / SCRUM-50 / SCRUM-51 | Assigned owner scopes available; no comments in the queried pages. | Ownership, but no additional M3/M4/M5 acceptance record in those sources. |

The [original M1 review](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#pullrequestreview-5346823334)
and its later Jira acknowledgement are both retained. The M1 acknowledgement is
positive evidence and must not be omitted merely because GitHub threads remain open.
The [SCRUM-53 completion record](https://hxj04121.atlassian.net/browse/SCRUM-53?focusedCommentId=10095)
also explicitly preserves candidate-only status and the unrecorded cross-module
acceptance gate.

This is a bounded audit of those PR records, Jira records and repository evidence.
It does not claim that no conversation could exist elsewhere. An external decision
must be linked and its accepted version/scope recorded before it changes this ledger.

## PR48 findings and current disposition

All ten GitHub threads below are still **unresolved** in the observed records.
M1 has subsequently acknowledged incorporation of the contract corrections in Jira;
the table separates that acknowledgement from exact-version freeze signoff.
The implementation column describes the candidate response; it does not close a
thread or turn `COMMENTED` into `APPROVED`.

| Original finding | Current M2 artifact / executable mapping | Acceptance disposition |
| --- | --- | --- |
| [Missing-allergen codes and explanation](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867049) | Both finding variants require `missingAllergenCodes` and caller-safe `explanation`. NO_ACTION has zero codes; REVIEW_REQUIRED has at least one, with uniqueness and sorted semantics. Day 6 corrects the two descriptions: codes are derived from N+1 but absent from published label N's CONTAINS declarations. `S3ImpactApiContractTest.changeAndImpactSchemasCarryStableVersionAndReviewHandoffReferences` checks the shape; the existing M1 strategy/MySQL golden verifies the comparison. | Candidate response present; M1 re-review still required. |
| [Non-null current label](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867054) | Three finding/handoff `currentLabelVersionId` references are required non-null `Identifier`. Impact prerequisites include 422 `PUBLISHED_LABEL_MISSING` before writes. Existing golden negative coverage verifies the discovery failure without impact-table writes. | Candidate response present; M1/M5 acceptance still required. |
| [Required proposed formula](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867056) | `proposedFormulaVersionId` is required. Absent target-spec adoption is 422 `FORMULA_ADOPTION_PENDING`. The older nullable proposal in SCRUM-75 is not the current candidate. | Candidate response present; M1 re-review still required. |
| [Replay and duplicate tuple](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867062) | Same change request + RuleSet returns the existing 200 result; a different RuleSet and duplicate change tuple return 409 `DATA_CONFLICT`. The contract/error test checks these declared semantics. | Candidate response present; M1/M5 must confirm executable run idempotency. |
| [Required description](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867073) | Create and response require a 1..1000-character `description`; existing schema tests check both. | Candidate response present; M1 re-review still required. |
| [404 versus 422](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867075) | Path-resource misses use 404. Missing body references, inactive RuleSet and unchanged specification use named 422 preconditions in the error matrix. | Candidate response present; consumer acceptance pending. |
| [Remove impact CURRENT_FORMULA_CHANGED](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867079) | Removed from the impact candidate/matrix and asserted absent by the contract test. Catalog adoption retains its independently valid stale-source 409 code. | Candidate response present; an outdated thread is not approval. |
| [Impact failures and rollback](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867085) | Impact routes no longer declare 503; integration/audit failures are caller-safe 500 `INTERNAL_ERROR` with whole-operation rollback. This does not alter catalog's separate unavailable-adapter behavior. | Candidate response present; M5 run/findings/tasks/audit acceptance pending. |
| [POST permissions / GET policy](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867092) | `CHANGE_REQUEST.CREATE` and `IMPACT.RUN` are named. Active-identity reads are the candidate policy, explicitly pending M4 acceptance. No new permission is seeded. | M4 decision still missing; not resolved by technical compatibility alone. |
| [Course evidence scope](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48#discussion_r4128867095) | Day 2 evidence had removed personal execution controls. Day 6 additionally removes the remaining personal scheduled-runner paragraph from Day 3 evidence and corrects its obsolete JSON claim to the actual shared CSV. | Documentation response present; no new reviewer confirmation is recorded. |

Contract mappings are in [the OpenAPI candidate](../contracts/s3-impact-review-publication-api-v1.yaml)
and [the error/acceptance matrix](../contracts/s3-impact-api-error-matrix-v1.md).
Executable checks are in [S3ImpactApiContractTest](../../backend/src/test/java/com/spectrace/S3ImpactApiContractTest.java)
and [IngredientSpecImpactStrategyMySqlTest](../../backend/src/test/java/com/spectrace/impact/IngredientSpecImpactStrategyMySqlTest.java).
The existing classifier belongs to M1; this reconciliation adds no classifier, workflow
or persistence implementation.

## Owner decisions still required

Role ownership is grounded in the current Jira parent scopes SCRUM-47/49/50/51 and
the repository's [earlier explicit role evidence](S2-M2-contract-diff.md).

| Owner | Required decision | Recorded acceptance |
| --- | --- | --- |
| M1 / Huang Xiangjia | Confirm the current candidate and adoption/golden references after the recorded remediation; consume lookup/golden/adoption without transferring classification ownership. | Human acknowledgement of incorporated corrections is recorded in Jira10096. Exact-current-version/adoption acceptance and freeze signoff are not recorded; PR48's formal review remains COMMENTED. |
| M3 / Xu Feiyang | Accept direct payloads, selector pagination/loading/error states and any run-query pagination decision. | Missing in the collected records. |
| M4 / Zhu Wenyu | Confirm 401/403, POST permissions, the shared GET read policy, required ReviewTask assignee selection, and review/publication references. | Missing in the collected records; GET/assignee choices remain explicit blockers. |
| M5 / Sun Huajian | Confirm 409/replay behavior and one transaction for analysis/findings/tasks/audit, including rollback and provenance. | Missing in the collected records. |

## Candidate version and freeze rule

The S3 OpenAPI retains version `1.0.0` and
`x-spec-trace-contract-state: CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`.
Day 6 changes only two descriptions, correcting their interpretation against the
existing strategy; no field, required set, route, status, permission or wire constraint
changes. The SOY CSV remains version 1 and
`PROPOSED_SCENARIO_NOT_A_RELEASED_SPECIFICATION`; test fixtures do not establish actual
business V2 release.

Freeze only after all required owners provide attributable acceptance of an exact
version/commit, all required findings are accepted or explicitly dispositioned, and
the agreed executable checks pass. Record links and decisions here; make any later
breaking change an explicitly reviewed versioned revision. Until then SCRUM-57 and
SCRUM-48 remain In Progress, and no contract/golden freeze or Sprint closure is claimed.
