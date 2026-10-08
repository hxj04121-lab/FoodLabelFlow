# Sprint 3 acceptance ledger — 8 October 2026

[Later compound-maker regression and opt-in qualification](S3-compound-maker-negative-20261008.md)
adds disposable permitted-creator database/HTTP/browser negatives while preserving
dated records below. It does not adopt login/staging or supply team signoff.

Repository: `hxj04121-lab/FoodLabelFlow`. Scope: Sprint 3 — Change Impact.
This is an implementation/acceptance checklist, not a team signoff. Jira was read
on 8 October 2026 using Sprint 37 and SCRUM-48. Source baseline for this
continuation is `969dfe6b47cddf03746c6117bb1ac8c83b590f77`.

## Actual requirements and owner boundaries

| Work item | Owner | Required completion evidence |
| --- | --- | --- |
| SCRUM-47 — impact core | M1 / Huang Xiangjia | Exact SOY golden product/outcome sets, excluded controls, one task per REVIEW_REQUIRED finding, MySQL run/findings/task persistence, impact-strategy A07. Jira is Done; this status does not prove the entire Sprint flow. |
| SCRUM-48 / 57 / 58 — contracts and adoption | M2 / Cai Runchen | Immutable formula adoption, current pointer, duplicate/concurrent rejection, MySQL golden tests, adoption A07, attributable M1/M3/M4/M5 acceptance of the exact contract revision, and green merged-main full-path evidence. |
| SCRUM-49 — browser workflow | M3 / Xu Feiyang | Real API specification change → findings → exact ReviewTask target → validation → independent approval → publication; self-approval, missing-permission and missing/failed-validation feedback; main CI browser artifacts; implemented UI A07. |
| SCRUM-50 — review/publication and identity | M4 / Zhu Wenyu | Java transition/maker-checker policies, exact-version validation gates, APPROVE record requirement, atomic publication/rollback/concurrency, merged login code or an adopted login ADR, review/publication A07. |
| SCRUM-51 — persistence, CI and staging | M5 / Song Hanjie | Atomic impact persistence/idempotency/rollback/module rules, full SOY browser path in main CI, merged deployment evidence or an adopted local/CI Compose staging ADR, persistence A07. |

SCRUM-48, SCRUM-49, SCRUM-50 and SCRUM-51 are In Progress in the queried
Sprint parent records. SCRUM-48 children 52–56 are Done and 57–58 are In Progress.
Do not close teammates' issues on the strength of another owner's tests.

## Review records actually available

| Record | Attributable evidence | Boundary |
| --- | --- | --- |
| PR48 M1 review, 29 September | COMMENTED, review 5346823334. Required missing-allergen fields, non-null published label, adopted-formula precondition, replay conflicts, description and body-reference errors. | Explicitly withheld approval then; covers M1-relevant parts only. |
| SCRUM-75 comment 10096, 1 October | M1 states the PR48 contract review items were folded into the merged contract. | Positive acknowledgement of those corrections; retain it even while old GitHub threads remain unresolved. |
| PR61 exact head `3a2ebd9739866d1061986c51fe4cfae7cde061bf` | M1 APPROVED on 7 October: adoption, immutable history, current pointer/audits, and M1 consumption notes. | Actual adoption review; does not accept the full four-module contract set. |
| PR62 reviewed head `2a28088bff4c0b79597da4a3bc3369aa11c94d61` | M1 COMMENTED: material-before-specification locking is correct; 40 real adoption requests improve the golden test. | Actual positive scoped technical review. Draft/base prerequisites were subsequently handled by the integration work; this review is still COMMENTED. |
| PR63 reviewed head `b7777cd4df409f651cc3393d96f87df23e2bad75` | M1 COMMENTED: corrected missing-allergen wording, adoption diagrams and evidence privacy are good. | Actual positive scoped document review; no invented formal approval of a later head. |
| SCRUM-49 / 50 / 51 comments | Complete queried comment pages each contain zero comments. | No additional M3/M4/M5 signoff is established in these records. This is not a claim that no conversation exists elsewhere. |

PR62 and PR63 were merged earlier in the continuation. The subsequent normal
merges integrated PR69's replay repair, PR68's documentation repair into PR58,
and refreshed PR70's publication/history guards. PR70's exact refreshed head
was `38741f401915500d22265c38062f2418673336c8`; its merge produced
`419d35cc762f7058f3322605d3948c44d1cb86bc`.

PR58 then received an actual other-author APPROVED review by rcncai,
review 5451381539 at 8 October 04:28:56 UTC, bound to exact head
`4daf279ec7368caac465e86dffda23a854e0a7c4`. It was normally merged at
04:30:49 UTC, producing main `c81fe9062f5564f38422c26744c8e240920e45cd`.
The PR58 qualification receipt reported workflow 37725726934, six successful checks,
82 XML reports containing 545 tests with zero failures/errors/skips, Sonar PASSED,
and the existing validation browser scenario passing. Those are that reviewed
head's integration receipts. The subsequent actual main push workflow 37727842682
ran at main c81fe906 with five jobs/six checks successful, 545 tests with zero
failures/errors/skips and Sonar PASSED, as recorded by SCRUM-58 comment 10180.
Both receipts qualify that integration predecessor, not execution evidence for this still-unmerged
product-flow extension or its twenty browser publications. The earlier PR58
COMMENTED review 5451082420 remains attributable to its earlier head only.

These exact-head reviews and merges do not constitute acceptance of the whole
M1/M3/M4/M5 contract revision, auto-resolve old review threads, or adopt login and
staging choices.

A positive COMMENTED review can be real technical review evidence. Jira does not
invent a universal GitHub APPROVED gate. A review request or a merge alone is
not acceptance of a complete contract revision.

## Product gap addressed by this continuation

The strict Day7 diagnostic reached adoption, the 40-finding golden analysis,
20 bound replacement drafts and real validator execution. All 20 replacements
failed validation because their SOY/WHEAT structured declarations were missing.
Approval/publication consequently could not run. This was a correct validator
rejection, not a reason to insert a synthetic PASSED run.

The product continuation adds explicit declarations atomically with a new draft
and exposes the existing Java submission/decision/publication services through
guarded HTTP operations. The new paged task collection and current-caller DTO supply
real task-list/detail and permission-aware presentation without creating an actor
switch, account, grant or login session. Declaration contents are a draft-creation snapshot.
There is no declaration update operation: existing validation runs do not bind
a declaration revision, so silently editing a previously validated snapshot
would invalidate the passing evidence. Existing target binding must not be
silently redirected by creating a second draft.

The existing DEV_EXTERNAL authentication flag is enforced by the shared actor
resolver, including case-insensitive provider matching and rejection of non-ASCII provider
namespaces before accent-insensitive database lookup. Existing validation reads
and commands reuse that resolver; source presence is separate from the required
disabled-flag test result. Configuration and grants are unchanged.

The new contract and implementation mapping are linked from the product-flow
A07 supplement. Runtime results belong in the exact-head delivery and CI
receipts, not in this checklist before execution.

## Completion gate

1. New product operations preserve old formula/label/declaration contents and
   existing permission/maker-checker checks; atomic rollback and concurrency
   regressions pass.
2. A real browser run follows supported business operations for declaration
   creation, validator execution, submission, independent decision and publication.
   No intercepted business response, direct SQL declaration or synthetic validation
   may stand in for the flow. Fixture HTTP tests remain separately identified.
3. Exact-version API rereads prove the old label is SUPERSEDED, the new label is
   PUBLISHED/current, the task is resolved and durable publication/audit references
   exist. Negative attempts produce no persisted transition.
4. The merged current main workflow runs the new browser scenario, keeps all five
   jobs green and records its actual SHA/run/artifacts/Sonar result.
5. Owners accept the exact contract revision and reconcile relevant review comments;
   the marker remains candidate until that decision is attributable.
6. M4's login/demo decision and M5's staging decision are adopted and merged.
   The [reviewable proposals](../architecture/S3-demo-identity-and-staging-proposals.md)
   do not claim either decision.
7. A07 diagrams and source/test mappings reflect the final implementation, with
   assessment acceptance recorded separately from artifact delivery.

Only then can the affected Sprint acceptance items and parent stage be reported
as complete. A missing human decision should identify its exact owner, choice
and artifact while implementation, tests and review continue independently.

## Dated execution and remaining checks

The new command/read-model/flag regressions, strict real Day7 test, all backend
tests, frontend checks and separate live browser scenarios must be recorded with
their actual results, source binding and artifacts. This snapshot contains no
invented final run count. The current supported browser arrangement uses the
existing officer maker for all twenty targets, QA approver and publisher. ADMIN
has neither LABEL.CREATE nor LABEL.APPROVE. The maker's visible disabled approval
and actual 403 AUTHORIZATION_DENIED prove UI/ACL rejection; they do not reach the
independent maker-checker policy. That policy has a separate actual service/unit
negative with a creator already holding LABEL.APPROVE. Browser, service/unit and
database assertions must retain their separate scope; no compound-grant identity
is introduced to improve a coverage claim. Missing/failed validation is a
separate domain gate.

The later completed real browser run is now available for source
4066b8cac6447913624bcaf044d9bdbd08d2d7e4: command exit 0 at
8 October 06:05:38 UTC, 347 source inputs bound with no deltas, existing live
validation 1 passed (4.4s), then S3 live 1 passed (1.6m). Actual observations
contain forty adoptions, forty findings (twenty NO_ACTION/twenty REVIEW_REQUIRED),
twenty real publications/twenty CLOSED tasks and sixty historical checks.
This is a dated local source result, separate from the older interruption and
fixture/HTTP results. Its officer approval rejection is ACL403, not an exercised
maker-checker policy; the service/unit policy evidence remains separate.

Independent review subsequently identified a Labels UI defect: draft creation
could proceed while the canonical allergen catalog was not ready, bind an empty
immutable declaration snapshot and leave that task unable to be corrected in
place. A narrow readiness guard plus delayed/failed-catalog zero-POST regressions
is being finalized. The corrected guard needs a new source-bound twenty-product
browser run; the 4066 pass cannot qualify later source. Final full backend verify
is also pending: the in-progress run exposed old positive HTTP fixtures that
had not explicitly enabled their test-only development identity. Those fixtures
are being corrected while the production false default/guard remains unchanged.
The actual running red result is retained; no full-suite pass is inferred. The previously confirmed RequestImpactIntegration development-flag bypass now
has an actual same-source regression: 16 tests/3 failures before the adapter fix,
then 16 tests/zero failures/errors/skips at 05:55:13 UTC. The same three HTTP
paths changed from 200/404/404 to 401/401/401, with thirteen business-table
snapshots unchanged. This closes that targeted bypass; the broader final fresh
verify, post-guard browser and new main results remain pending.

Login/demo actor-switch adoption remains M4's decision; staging/promotion adoption
remains M5's decision. Exact cross-module acceptance and A07 assessment remain
attributable human decisions after artifact delivery.

## Exact candidate revision and reviewer responsibilities

The candidate is a bundle of the existing impact/review/publication v1.0.0
[OpenAPI](../contracts/s3-impact-review-publication-api-v1.yaml) and
[error matrix](../contracts/s3-impact-api-error-matrix-v1.md),
[explicit adoption v1 contract](../contracts/specification-adoption-api-v1.md),
[60-row SOY oracle](../../backend/src/test/resources/golden/s3-m2-soy-spec-v2-impact-v1.csv),
and the new product-flow v1.0.0 [OpenAPI](../contracts/s3-label-product-flow-api-v1.yaml) /
[error matrix](../contracts/s3-label-product-flow-error-matrix-v1.md).
The separate filenames and exact artifact hashes identify each revision; an
earlier v1.0.0 label or merged PR cannot accept later changed contents. The final
source/PR/main binding and actual review scope accompany the bundle.

| Required module / actual owner | Concrete review/acceptance scope |
| --- | --- |
| M1 — Huang Xiangjia | Current/proposed formula and published-label references, 40 relevant/20 excluded golden rows, one task per review finding, change/analysis replay and no-write preconditions. Earlier positive M1 comments remain scoped evidence. |
| M3 — Xu Feiyang / Xu fy | Actual page/task/caller DTOs, pagination/nullable targets, exact validation and command status/errors, visible ACL/validation negatives and controlled-switch consumer requirements. |
| M4 — Zhu Wenyu / zhuwenyu26 | Existing active-identity/read policy, trusted context/ASCII provider boundaries, permission versus maker-checker distinction, Java transitions, exact target/validation and APPROVE-record/publication guards. |
| M5 — Song Hanjie / shj040128shj | Transaction/rollback/replay/current committed response, duplicate/concurrent publication/creation, run/task/audit references, SQL constraints and complete exact-main evidence. |
| M2 — Cai Runchen | Own immutable adoption/golden/source/test/A07 traceability and truthful assembly of those records; does not accept another module on its behalf. |

The fresh complete Jira comment pages still contain no M3/M4/M5 acceptance in
SCRUM-49/50/51 (each total zero). SCRUM-48/57/58 remain In Progress. Attributable
positive COMMENTED content may establish its stated review scope; do not relabel
it APPROVED or replace the required acceptance with a new artificial approval
rule. The authoritative old review records and newer SCRUM-58 comment 10180
remain separate from this candidate bundle.
