# M3 consumer review of the M2 Day 2 candidate

Date: 30 Sep 2026. Author: Xu Feiyang / M3. Related: SCRUM-49/71–74.
Status: **local review draft; candidate and owner decisions remain pending**.

## Integration update — 3 Oct 2026

The review below is a dated snapshot of `0755d06`, not a statement of current
main availability. The pre-PR check now uses `main@e53f7b0`:

- C01's change discovery gap is resolved by the merged paged collection and
  exact-item GET. See [the selector handoff](../contracts/s3-change-request-selector-decision.md).
  SCRUM-71 can proceed with real selection/context reads; this PR does not wire them.
- Impact run trigger/query operations remain contract-only. The new
  `ImpactAnalysisApplicationService` persists already-classified results;
  classification and public run/query controllers are still absent.
- Submission/decision Java services now exist, but task query/binding and
  review/publication HTTP operations, publication execution and the S3 login
  decision are not available for the full browser flow.
- C02's `missingAllergenCodes` wording, command reconciliation, the SOY oracle
  and remaining version/workflow questions still need owner resolution. The
  YAML retains `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`.

This is M3 consumer feedback and does not declare cross-module acceptance,
contract freeze or completion of SCRUM-71–74. The historical waiting guidance
below is superseded by this update for the implemented change-request reads.

## Reviewed sources and scope

This review reads the already-fetched `origin/main` revision
`0755d061ee9a0a89ce7854a147c9e49453490f13` through `git show`. It performs no
branch synchronization and does not imply a fresh Jira read, a submitted team
review, cross-module acceptance, or a contract freeze.

- [M2 candidate OpenAPI at that revision](https://github.com/hxj04121-lab/FoodLabelFlow/blob/0755d061ee9a0a89ce7854a147c9e49453490f13/docs/contracts/s3-impact-review-publication-api-v1.yaml)
- [Candidate error matrix](https://github.com/hxj04121-lab/FoodLabelFlow/blob/0755d061ee9a0a89ce7854a147c9e49453490f13/docs/contracts/s3-impact-api-error-matrix-v1.md)
- [Day 2 evidence and pending acceptance](https://github.com/hxj04121-lab/FoodLabelFlow/blob/0755d061ee9a0a89ce7854a147c9e49453490f13/docs/evidence/S3-M2-day2-contract-candidate.md)
- Local M3 [test design](S3-M3-test-design.md), [A07 draft](S3-M3-A07-draft.md),
  `frontend/src/pages/Impact.tsx`, `Labels.tsx`, label API client and validation UI.

The YAML explicitly retains `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`.
It exposes only change-request creation/exact read and impact-analysis
trigger/exact read. ReviewTask and PublicationHandoff are reference schemas;
they do not supply review-query, decision, publication or login operations.
Only M3 presentation/consumption needs are assessed here. Domain rules, wire
contracts, authentication, transactions and interface implementation stay with
their owners. No owner artifact was edited and no review message was sent.

## Shapes that fit the documented M3 needs

These are consumer observations, not formal acceptance:

- Direct success resources and the existing four-field ApiError can use the
  current frontend response/error approach without a new response envelope.
- Stable change/run/finding/product/version IDs allow exact context checks.
  Explicit `ruleSetVersionId` supports version-bound validation.
- The two finding variants identify `NO_ACTION` and `REVIEW_REQUIRED`; only the
  latter contains a review handoff. The UI can consume the server outcome
  without implementing classification or creating tasks locally.
- Required non-null current label and proposed formula references make the
  candidate impact preconditions explicit. The UI can display a blocked run
  rather than fabricate missing resources.
- A nullable `draftLabelVersionId` exposes a real missing-binding condition;
  the UI can withhold label-dependent actions until the owner binds a draft.
- An impact replay with the same change and rule set is proposed to return
  the existing analysis as 200. This is limited to that impact operation;
  it does not establish replay safety for draft/review/publication writes.

## Questions to resolve before the corresponding integration

| ID / M3 consumer need | Candidate/source evidence | Owner decision or evidence required | Gate |
| --- | --- | --- | --- |
| C01 — discover a change and reopen its run | Paths provide POST change requests, GET one change request, POST its analysis and GET one analysis. There is no change-list/search or analysis-by-change GET. The M3 test design expects change selection. | M1 Huang Xiangjia + M2 Cai Runchen: confirm the supported discovery path, such as an agreed list/search capability or an exact-ID/create-result entry. Confirm how to reopen an existing run from a change. M3 proposes no URL or extra endpoint. | SCRUM-71 selection and reopen flow |
| C02 — explain missing declarations accurately | Both finding schemas describe `missingAllergenCodes` as codes "absent from the proposed formula". The documented M3 glossary explains REVIEW_REQUIRED as missing declarations on the published label. | M1 + M2: clarify which sets are compared and correct/confirm the field meaning. Supply a SOY example and a NO_ACTION control. M3's interpretation is required allergens from the proposed formula that the published label does not declare; it is an interpretation to verify, not a new classification rule. | Finding explanation and result oracle |
| C03 — display complete results without misleading totals | `ImpactAnalysis` contains counts and `findings`; findings are sorted by productId. No pagination/truncation metadata is defined. | M2 + M1: confirm whether this candidate always returns the complete set and its expected maximum size. M3 has no present evidence requiring server pagination. If pagination is needed, owners must define completeness/totals and version the response; the UI cannot silently treat one page as the whole run. | SCRUM-71 results and empty-state assertions |
| C04 — preserve before/after formula and label meaning | Findings name `currentFormulaVersionId`, `proposedFormulaVersionId` and `currentLabelVersionId`; the trigger description distinguishes the formula backing the published label from adopted target-spec N+1. | M1 + M2: demonstrate these references after explicit adoption, including their relationship to the product's current pointer. Supply the owner-supported adoption/setup path and exact expected IDs. M3 will use returned bound references and never replace them with a later current/latest lookup. | Version context and E2E setup |
| C05 — open the correct replacement draft | `ReviewTaskHandoff` has task/finding/product/current-version IDs and nullable `draftLabelVersionId`; its assigned-user rule is pending M4 review. There is no task list/detail/binding operation. | M4 Zhu Wenyu with M1/M2: deliver the task read model and supported creation/binding flow. Define who may bind, behavior while null, the assignee rule, and how the bound draft's formula/rule set/jurisdiction are checked against the task/run. | SCRUM-72 task-to-label integration |
| C06 — switch authenticated actors and show permitted operations | POST change creation and impact run name permissions; GETs do not name permissions. No actor/session/capability or user-switch contract is supplied. Existing S2 headers use a fixed local demo identity. | M4 + M2: confirm read permissions and the login/controlled-switch decision, current actor and operation-permission representation. Supply distinct maker/checker/restricted identities. UI text/avatar changes cannot authenticate an actor; backend must enforce maker-checker and all guards. | SCRUM-71 protected reads and SCRUM-73 actions |
| C07 — submit, decide, publish and show durable history | `PublicationHandoff` gives opaque IDs/timestamp but no operation, lifecycle/allowed-action model or explanation of `sourceLabelVersionId`. | M4 with M2/M5: supply submission, decision and publication contracts and error meanings; confirm whether approval includes publication or a separate command is required. Define source/published/superseded relationships and authoritative rereads of task, versions and publication evidence. | SCRUM-73 and final A07 sequence |
| C08 — recover uncertain command outcomes | Impact replay semantics are proposed, but a lost trigger response may leave the caller without its analysis ID. There is no general reconciliation contract for other commands. | M1 + M5 Sun Huajian for impact; M4/M5 for workflow: define outcome lookup/correlation and any supported key/replay operation. Keep read retries separate from writes. A historical label/validation record cannot prove the outcome of a new command. No automatic write retry is designed from this candidate alone. | Safe command handling and UI-10 |
| C09 — prove integration with stable data and expected outcomes | Existing M3 test design requires a SOY outcome map, excluded controls, adopted Spec V2/Formula N+1, exact labels/rule set and durable audit/publication. Candidate documents alone do not provide that runtime evidence. | M2: independent product/outcome oracle and negative controls. M1/M4: owner-supported setup/operations. M5 with owners: reproducible isolated environment, durable results and real main-CI path evidence. | SCRUM-74 main-path and permission negatives |

## Candidate error-handling consumption

If accepted, M3 can branch on the machine-readable codes in the reviewed
matrix and present caller-safe messages plus real trace/evidence IDs when
returned. These are proposed consumer mappings only:

- 400/422: explain input or missing domain prerequisite; retain exact context.
  `PUBLISHED_LABEL_MISSING` and `FORMULA_ADOPTION_PENDING` require owner-supported
  state/setup changes before another run.
- 401/403: distinguish absent/invalid identity from insufficient permission.
  The recovery action depends on the still-pending M4 login/permission model.
- 404: show the absent exact path resource; never retarget to current/latest.
- 409: expose conflicting duplicate/replay context; do not silently change the
  requested rule set or generate a new change request.
- 500, connection loss or invalid success body: show failure/uncertain command
  outcome as appropriate; do not convert it to empty success or seed data.

No review/publication-specific server code or state is inferred from this
impact matrix. The actual M4 workflow matrix must cover the required self-review,
permission and validation-gate negatives before those actions are wired.

## Waiting state and next steps

This local consumer review is complete as a draft. The questions remain open
for owner resolution; no Jira task or contract is marked Done/frozen.
The currently authorized independent label-draft fixes have their own
[local evidence](S3-M3-label-draft-regressions-2026-09-30.md).

Resume SCRUM-71 only after its accepted contract and real API conditions are
met; resume 72/73 when task binding, workflow and login conditions are met.
Final SCRUM-74/A07 acceptance still requires integrated runtime behavior,
distinct real test identities, main CI and independent review. No temporary
workflow or guessed interface should fill these gaps.
