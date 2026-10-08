# S3 label product-flow A07 integration supplement

Later qualification: [compound-maker negative and live opt-ins](../evidence/S3-compound-maker-negative-20261008.md)
defines the disposable permitted-creator MySQL/HTTP/browser regression and
retains earlier officer ACL403 and unit/service evidence with their original
scope. Actual passing results require their own source-bound receipts.

Prepared 8 October 2026. Status: **implemented-source candidate; acceptance pending**.
M2's required use case remains UC-M2-SPEC-ADOPT: Adopt Specification Change into
Formula, documented by its original SAD, pattern decision and four adoption
analysis/design diagrams. Those artifacts and their historical evidence are
unchanged. This product-flow supplement covers fresh declarations, review,
publication, caller/task reads and integration transaction/UI concerns; it does
not replace the original M2 use case or assume another module's A07 assessment.

This supplement connects M2's existing
[adoption SAD](S3-M2-adoption-sad.md), M1's
[impact use case](../evidence/S3-M1-A07-run-change-impact-analysis.md), and the
new replacement-label declaration/HTTP boundary. It does not replace a module
owner's assessment, accepted login decision or cross-module signoff.
Exact runtime results and final commit bindings are recorded in the delivery receipt.

## Use case: validate, independently approve and publish a replacement

Maker creates the first replacement snapshot with explicit legal declarations.
The server fixes its current released formula, active jurisdiction rule set,
creator and declaration source, then binds the exact unbound ReviewTask.
The real validator records results for that exact snapshot. Submission requires
its latest exact-label/rule-set validation to pass. An independently permitted
checker records APPROVE, after which a permitted publisher separately invokes
the exact task/target publication.

Creation is a new immutable snapshot; explicit M2 formula adoption has already
occurred and is never a publication side effect. The old published label retains
its historical formula and declarations. Publication changes lifecycle/current
selection metadata atomically, preserving the old content.

Alternate flows include invalid/duplicate declarations, a bound or mismatched
task, absent identity/permission, failed validation, self-approval, stale
formula/label, missing APPROVE record, and persistence/audit failure. Their HTTP
codes and no-write/rollback behavior are in the
[error matrix](../contracts/s3-label-product-flow-error-matrix-v1.md);
wire resources are in the
[OpenAPI extension](../contracts/s3-label-product-flow-api-v1.yaml).

## Analysis models

[Analysis classes](diagrams/s3-label-product-flow-analysis-class.mmd) / [SVG](diagrams/s3-label-product-flow-analysis-class.svg) and
[analysis sequence](diagrams/s3-label-product-flow-analysis-sequence.mmd) / [SVG](diagrams/s3-label-product-flow-analysis-sequence.svg)
describe boundary/control/entity responsibilities. They do not invent new
runtime classes. The entities reflect actual exact-version relationships:
product selections, immutable formula/label content, explicit declarations,
validation evidence, task binding and decision/publication history.

## Design models and real implementation map

[Design classes](diagrams/s3-label-product-flow-design-class.mmd) / [SVG](diagrams/s3-label-product-flow-design-class.svg) and
[design sequence](diagrams/s3-label-product-flow-design-sequence.mmd) / [SVG](diagrams/s3-label-product-flow-design-sequence.svg)
name actual Java controllers, services, ports, JDBC adapters and policies.

| Responsibility | Actual implementation |
| --- | --- |
| Strict local command JSON, without changing unrelated modules | [StrictCommandJson](../../backend/src/main/java/com/spectrace/shared/api/StrictCommandJson.java) |
| Draft creation and exact label/declaration reads | [LabelDraftController](../../backend/src/main/java/com/spectrace/label/interfaces/web/LabelDraftController.java) |
| Permission/input checks and atomic immutable first snapshot | [LabelDraftService](../../backend/src/main/java/com/spectrace/label/application/LabelDraftService.java), [LabelDeclarationInput](../../backend/src/main/java/com/spectrace/label/application/LabelDeclarationInput.java) |
| Current released formula, active rule set and declaration persistence | [JdbcLabelDraftRepository](../../backend/src/main/java/com/spectrace/label/infrastructure/JdbcLabelDraftRepository.java) |
| Product-before-task lock, no silent rebind, exact expected-task check | [JdbcReviewTaskDraftBinding](../../backend/src/main/java/com/spectrace/workflow/infrastructure/JdbcReviewTaskDraftBinding.java) |
| Authenticated submission/decision/publication/task HTTP adapters | [LabelWorkflowController](../../backend/src/main/java/com/spectrace/workflow/interfaces/web/LabelWorkflowController.java) |
| Exact-version validation, current guards, transaction transitions | [LabelReviewService](../../backend/src/main/java/com/spectrace/workflow/application/LabelReviewService.java) |
| Independent checker and Java lifecycle policy | [MakerCheckerPolicy](../../backend/src/main/java/com/spectrace/workflow/domain/MakerCheckerPolicy.java), [LabelTransitionPolicy](../../backend/src/main/java/com/spectrace/workflow/domain/LabelTransitionPolicy.java) |
| Atomic label/task/approval/publication/audit records and committed guards | [JdbcLabelReviewCommandRepository](../../backend/src/main/java/com/spectrace/workflow/infrastructure/JdbcLabelReviewCommandRepository.java) |
| Real impact selection/findings and exact-task handoff | [Impact page](../../frontend/src/pages/Impact.tsx), [impact client](../../frontend/src/api/impact.ts), [Labels page](../../frontend/src/pages/Labels.tsx). |
| Frontend request lock, exact validation context and persistent uncertain-command guard | [LabelWorkflowPanel](../../frontend/src/components/LabelWorkflowPanel.tsx); existing connected context is used without inventing an actor/switch. |
| Frontend exact-target response checks and separate commands | [label-workflow client](../../frontend/src/api/label-workflow.ts) |
| Expected command errors versus unexpected persistence/audit failure | [LabelErrors](../../backend/src/main/java/com/spectrace/label/interfaces/web/LabelErrors.java), [WorkflowErrors](../../backend/src/main/java/com/spectrace/workflow/interfaces/web/WorkflowErrors.java), [LabelProductFailureErrors](../../backend/src/main/java/com/spectrace/label/interfaces/web/LabelProductFailureErrors.java); higher-precedence expected/identity mappings remain intact. |
| Shared active-actor resolution and existing development opt-out | [ExternalActorResolver](../../backend/src/main/java/com/spectrace/identity/application/ExternalActorResolver.java), existing IdentityService/AuthorizationService and higher-precedence IdentityErrors; provider namespaces are ASCII and DEV_EXTERNAL matching is case-insensitive. No property, user or grant change. |
| Actual caller read, sorted role/permission DTO | [CurrentIdentityController](../../backend/src/main/java/com/spectrace/identity/interfaces/web/CurrentIdentityController.java), [CurrentIdentityView](../../backend/src/main/java/com/spectrace/identity/application/CurrentIdentityView.java); this does not select a caller. |
| Ordered paged real ReviewTask reads | [ReviewTaskListController](../../backend/src/main/java/com/spectrace/workflow/interfaces/web/ReviewTaskListController.java), [ReviewTaskReadService](../../backend/src/main/java/com/spectrace/workflow/application/ReviewTaskReadService.java), [ReviewTaskReadRepository](../../backend/src/main/java/com/spectrace/workflow/application/port/ReviewTaskReadRepository.java), [JdbcReviewTaskReadRepository](../../backend/src/main/java/com/spectrace/workflow/infrastructure/JdbcReviewTaskReadRepository.java). |
| Permission presentation and live task collection/detail | [CurrentIdentityPanel](../../frontend/src/components/CurrentIdentityPanel.tsx), [identity client](../../frontend/src/api/identity.ts), [Reviews page](../../frontend/src/pages/Reviews.tsx); no actor selector. |
| Existing validation reads and commands respect the same actor seam | [ValidationController](../../backend/src/main/java/com/spectrace/validation/interfaces/web/ValidationController.java) and [RequestAuthorizationAdapter](../../backend/src/main/java/com/spectrace/validation/infrastructure/RequestAuthorizationAdapter.java); unchanged evaluator and configuration. |

The controller delegates business state changes to the Java application/domain
layer. Spring owns transactions across existing JDBC adapters; no review or
publication stored procedure is introduced.

## Design problem and decision: a validated snapshot must remain exact

Before this continuation, creating a draft inserted only `label_version`.
The validator correctly rejected missing SOY/WHEAT declarations. Directly
updating declaration rows would create a different label input while retaining
old passing evidence, because existing validation runs do not bind a declaration
revision. Creating another draft also cannot silently retarget an already bound task.

| Option | Benefit | Cost / invariant risk | Decision |
| --- | --- | --- | --- |
| Derive and silently insert declarations during validation/publication | Short positive demo path | Confuses derived facts with explicit legal-label input, alters validated input or history, and hides missing input | Reject |
| Edit declarations in place with no evidence invalidation | Familiar editor | An old PASSED run can authorize changed input; task target/history semantics are undefined | Reject |
| Add revision-bound editing and deliberate task replacement | Can support later REQUEST_CHANGES corrections | Requires a complete validation invalidation/revision and immutable task-history design, new tests and owner acceptance | Defer as explicit separate design |
| Accept explicit declarations only while creating the fresh first task target | Uses existing exact-version validation, small auditable transaction | Existing bound snapshots remain immutable; request-changes editing is outside this slice | Selected implementation for this continuation |

The implemented control is immutable first-snapshot creation plus a transaction
and locking order, not an invented GoF Factory Method/Builder/Prototype.
Product serialization covers creation and publication; first-task guards
prevent duplicate creation or silent rebinding. Declaration input validates
canonical jurisdiction catalog IDs, CONTAINS, bounded text and distinct IDs;
source is server-assigned USER_ENTERED.

APPROVE and publication remain separate commands. Their success responses are
the exact `LabelDraft` resource, not a new evidence envelope. The browser
must reconcile the same label/task after uncertain writes and verify persisted
publication/history. Switching a fixture actor proves test enforcement only.

## Unit-of-Work, rollback and audit design

This integration supplies a source-bound rationale for
[SCRUM-51](https://hxj04121.atlassian.net/browse/SCRUM-51) /
[SCRUM-70](https://hxj04121.atlassian.net/browse/SCRUM-70). Spring application
transactions coordinate the component/port/JDBC structure in the design class
and sequence diagrams. An entire browser interaction is deliberately not one
database transaction: each successful business command has its own durable
boundary, and later commands check the committed exact-version prerequisites.

| Unit of work | Actual component and writes | Failure behavior |
| --- | --- | --- |
| Impact run | [ImpactAnalysisApplicationService](../../backend/src/main/java/com/spectrace/impact/application/ImpactAnalysisApplicationService.java) coordinates run, finding, task-linkage and audit ports/adapters | Any run/middle-finding/task/audit failure propagates; the transaction rolls back all rows. [ImpactAnalysisRollbackIntegrationTest](../../backend/src/test/java/com/spectrace/impact/ImpactAnalysisRollbackIntegrationTest.java) exercises all four injected boundaries and retry. |
| First replacement | LabelDraftService, draft repository and task-binding port/JDBC insert label/declarations and bind the same unbound task after product locking | Failed catalog/first-binding/stale finding or persistence leaves no placeholder label, declaration or redirect. Concurrent creation has one winner. |
| Submission/decision | LabelReviewService and command repository check exact validation/current/lifecycle/permission/independent maker, then persist label/task/ApprovalRecord/audit as appropriate | A guard fails before later writes; a command persistence/audit failure rolls back all command writes. |
| Publication | The same service/repository supersede the old label, publish the exact approved target, update pointer, close task and write publication/audit | Current-target/APPROVE-record guards and product lock precede writes. Failed pointer/record/audit updates roll back the entire publication; repeat publication writes no extra record. |

The design rejects separate repository autocommits and exception swallowing:
either would make a reported failure leave orphaned findings/tasks or a partially
published pointer. It also rejects using one transaction across HTTP/user review,
which would hold product locks while a person decides and cannot safely represent
uncertain network outcomes. Existing ports keep business transaction ownership
in the application layer and SQL persistence in infrastructure. ArchUnit and
injected-failure/concurrency tests assess those responsibilities independently.
This delivered rationale is not M5's owner acceptance or an invented GoF pattern.

## M3 state, permissions and asynchronous reads

The implemented UI design supports
[SCRUM-49](https://hxj04121.atlassian.net/browse/SCRUM-49) /
[SCRUM-74](https://hxj04121.atlassian.net/browse/SCRUM-74). Domain label states
remain DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED, with REQUEST_CHANGES
returning the same immutable DRAFT and REJECT producing REJECTED. Current
published selection and SUPERSEDED history are separate persisted metadata.

| Presentation state | Actual transition/control |
| --- | --- |
| Identity loading/unavailable | CurrentIdentityPanel reads the real connected actor with cancellation, an active-response guard and a timeout; commands requiring that context stay disabled. Refresh rereads actual mappings. |
| Ready exact draft | Label/rule-set-bound validation and current actor permissions determine available controls; local connected-context consent is explicit. A display change does not establish a new actor. |
| Command in flight | A request lock prevents overlapping writes. Response target/status checks validate the requested exact snapshot before updating the selected view. |
| Unconfirmed outcome | Network, malformed-response or unexpected-server failure retains the original target/action and blocks further workflow commands even after switching labels. |
| Reconciled | Exact label/task reads confirm the expected persisted result; a different lifecycle or current pointer leaves the outcome guarded rather than silently retrying. |
| Task-list/detail loading | Real ordered status-filtered pages have bounded pagination and no implied total count. Abort/generation guards prevent obsolete list/detail responses overwriting the selected task. Nullable targets remain unbound until actual creation. |

This design rejects mock/seeded business responses for the live flow, client-side
role labels as authorization, clearing a write's uncertain outcome on navigation,
and automatically selecting the latest label after an exact-target mismatch.
UI fixtures test presentation/request/uncertain-state behavior separately from
real Spring/MySQL browser assertions. Existing officer UI/HTTP self-approval
attempts reject through missing LABEL.APPROVE permission; that ACL result must
not be described as reaching MakerCheckerPolicy. The actual policy/service
negative supplies an already permitted creator and rejects with
AuthorizationDeniedException/403 before decision/approval writes. These separate
assertions use existing test actors without introducing a new grant or production
identity. Missing/failed validation is a separate domain negative.
A controlled user switch/login decision and M3 assessment remain owner decisions.

## Executable evidence mapping

| Assertion responsibility | Actual source | Evidence limitation |
| --- | --- | --- |
| Strict JSON, actor/source spoofing rejected before application writes, 1000-character comments, missing body | [LabelProductCommandHttpTest](../../backend/src/test/java/com/spectrace/label/LabelProductCommandHttpTest.java) | MockMvc with mocked services proves the HTTP adapter; not MySQL publication or independent authorization. |
| Real first declarations, jurisdiction/duplicates, failed binding rollback, stale finding/formula and concurrent first creation | [LabelDraftDeclarationsHttpMySqlTest](../../backend/src/test/java/com/spectrace/label/LabelDraftDeclarationsHttpMySqlTest.java) | Real HTTP/MySQL; exact final execution is bound to its delivery receipt. |
| Actual 40 adoptions, 40 golden findings, 20 explicit snapshots/evaluator/HTTP decisions/publications, 60 histories | [Day7RealSoyPublicationPathMySqlTest](../../backend/src/test/java/com/spectrace/workflow/Day7RealSoyPublicationPathMySqlTest.java) | Strict backend test; no browser is asserted by its result. |
| Real browser/API path for 20 publications and persisted rereads | [s3-product-flow-live.spec.ts](../../frontend/tests/s3-product-flow-live.spec.ts), [local launcher](../../backend/src/test/java/com/spectrace/workflow/S3ProductFlowBrowserHarness.java) | No fulfilled business response. Seeded released-specification input and preauthenticated test contexts are explicit test setup; no synthetic declaration/PASSED input. |
| Presentation/request/uncertain-state behavior | [s3-product-flow-ui.spec.ts](../../frontend/tests/s3-product-flow-ui.spec.ts) | HTTP fixtures only, separately identified from the live run. |
| Actual caller DTO, paged/status-filtered task read and invalid queries | [CurrentIdentityHttpTest](../../backend/src/test/java/com/spectrace/identity/CurrentIdentityHttpTest.java), [ReviewTaskReadHttpTest](../../backend/src/test/java/com/spectrace/workflow/ReviewTaskReadHttpTest.java), [S3ReadModelsMySqlTest](../../backend/src/test/java/com/spectrace/workflow/S3ReadModelsMySqlTest.java) | Adapter tests and real MySQL assertions are separate; execution results remain in the actual receipt. |
| Existing development-auth opt-out across old/new routes | [DevExternalAuthDisabledIntegrationTest](../../backend/src/test/java/com/spectrace/identity/DevExternalAuthDisabledIntegrationTest.java), [LabelProductDevAuthDisabledMySqlTest](../../backend/src/test/java/com/spectrace/label/LabelProductDevAuthDisabledMySqlTest.java) | Must execute disabled-flag, case-variant and actual raw-Latin1 provider-alias negatives before claiming verified enforcement; no flag or grant is changed. |
| Actual permitted-creator maker-checker policy/service negative | [LabelReviewServiceTest](../../backend/src/test/java/com/spectrace/workflow/application/LabelReviewServiceTest.java), [MakerCheckerPolicyTest](../../backend/src/test/java/com/spectrace/workflow/domain/MakerCheckerPolicyTest.java), each rejectsSelfApproval | Service actor already has LABEL.APPROVE; decision/approval writes are never invoked. Unit/service tests are not live browser or MySQL negative evidence. |
| Real current/validation/APPROVE-record/rollback/publication races | [WorkflowIntegrationTest](../../backend/src/test/java/com/spectrace/workflow/WorkflowIntegrationTest.java) | Real MySQL transaction and independent approval/publication cases. No DB self-approval case is inferred from these unrelated assertions. |


Existing adoption/golden checks and their historical run receipts are linked from
[Day6 A07 evidence](../evidence/S3-M2-day6-a07-evidence.md). The new focused HTTP,
MySQL and browser tests are mapped to the final source/test receipt after they
run; this supplement does not assign a passing status to an unfinished run.
Preserve a prior strict diagnostic failure as historical evidence and distinguish
it from a later successful implementation test.

Acceptance requires all of these assertions to execute: real declaration
creation, real validation, supported submission, independent approval and
separate publication; exact old/new/task rereads after refresh; self-approval,
missing permission and missing/failed validation rejected with no transition;
immutable history and transactional rollback/concurrency preserved. A fixture
browser pass does not prove the real main Compose flow.

## Remaining decisions and limits

The [Sprint acceptance ledger](../evidence/S3-phase-acceptance-20261008.md)
defines the actual Jira gates. The
[identity/staging proposals](S3-demo-identity-and-staging-proposals.md)
retain owner decision status explicitly. No team freeze, human signature,
deployment or assessment acceptance is inferred from these diagrams.

REQUEST_CHANGES returns the same immutable target to DRAFT with an OPEN task.
This slice does not claim declaration editing or a replacement/rebind mechanism.
A future revision must define validation invalidation and immutable task/decision
history before it permits either operation.

## Resumed execution boundary

The twenty-target live source now explicitly uses officer/QA/publisher contexts.
Existing ADMIN supplies formula-adoption maintenance only; it lacks CREATE/APPROVE.
A completed real browser run for 4066b8cac6447913624bcaf044d9bdbd08d2d7e4
finished at 06:05:38 UTC with exit 0: legacy live validation 1 passed/4.4s,
S3 live 1 passed/1.6m, forty adoptions/forty findings/twenty publications,
twenty CLOSED tasks and sixty historical checks. Its 347 source inputs remained
byte-bound. This later completed local run qualifies its bound source; older interrupted
attempts retain their own status. Post-catalog-guard browser and broad/main results remain
separate pending checks.
The old impact adapter now shares
[ExternalActorResolver via RequestImpactIntegration](../../backend/src/main/java/com/spectrace/impact/infrastructure/RequestImpactIntegration.java).
Its unchanged sixteen-case regression was red with three failures, then green
with zero failures/errors/skips at 05:55:13 UTC; the three real paths return
401 before resource lookup and thirteen business-table snapshots are unchanged.
The exact red/green XML, adapter/test hashes and source freeze are retained in
the runtime receipt. Four enabled-auth fixtures compiled during this focused
run but were not executed; the final fresh broad verification remains separate.

## Catalog readiness correction awaiting final qualification

Independent review found the declaration selector could be unavailable while
the maker could still create and permanently bind an empty first snapshot.
The intended narrow UI guard requires the canonical jurisdiction catalog to be
ready before first creation; delayed/failed catalog tests must show zero draft
POSTs. It does not automatically declare allergens, change the server's legacy
optional-input contract, edit a validated snapshot or rebind the task.

The readiness correction, exact subsequent source/commit and its fresh live
twenty-target browser evidence must be qualified after implementation. Older
4066 browser proof remains dated evidence. Broad backend verification's currently
exposed positive-fixture opt-in errors are corrected in tests, leaving production
development authentication disabled by default. No green final verify or new
main CI is prefilled.
