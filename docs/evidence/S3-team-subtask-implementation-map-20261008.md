# S3 team subtask implementation and acceptance map

Later test scope: [compound-maker negative and enabled live opt-ins](S3-compound-maker-negative-20261008.md).
This uses an isolated test identity with existing roles; older officer/unit/service
rows retain their execution scope. No production actor switch or owner acceptance
is inferred from that fixture.

Prepared 8 October 2026 from the complete 26-row Jira query
`parent in (SCRUM-47, SCRUM-49, SCRUM-50, SCRUM-51)`.
This maps substantive criteria to real sources and tests. Jira state alone is
not used to infer absence or completion. Existing code is distinguished from
this continuation's unmerged product-flow work. Passing executions must be
bound separately to their actual source head and CI run.

## M5 persistence and CI — SCRUM-51

| Subtask | Substantive requirement | Actual implementation / test artifact | Boundary after the new flow |
| --- | --- | --- | --- |
| SCRUM-59 | One transaction for run/findings/task/audit; failures propagate | [ImpactAnalysisApplicationService](../../backend/src/main/java/com/spectrace/impact/application/ImpactAnalysisApplicationService.java) with Spring transaction and application ports; [application integration test](../../backend/src/test/java/com/spectrace/impact/ImpactAnalysisApplicationServiceIntegrationTest.java) | Existing runtime. A new browser pass supplements success evidence; it does not replace injected-failure tests. |
| SCRUM-60 | Superseded into SCRUM-62 | Historical Jira pointer to 62 | No separate new completion gate or duplicated work. |
| SCRUM-61 | Run/middle-finding/task/audit failures leave zero residue; retry succeeds | [ImpactAnalysisRollbackIntegrationTest](../../backend/src/test/java/com/spectrace/impact/ImpactAnalysisRollbackIntegrationTest.java): four named injected failure/retry paths | Existing substantive tests. Verify in the new full backend/main run, rather than assuming Idea/Done proves execution. |
| SCRUM-62 | JDBC ports/adapters, business-key idempotency, sequential replay; no duplicate business logic | ImpactAnalysisRunRepository/ImpactFindingRepository/ReviewTaskLinkageRepository and Jdbc adapters; [ImpactPersistenceIntegrationTest](../../backend/src/test/java/com/spectrace/impact/ImpactPersistenceIntegrationTest.java), [ImpactPortContractTest](../../backend/src/test/java/com/spectrace/impact/ImpactPortContractTest.java) | Existing persistence. PR69 additionally repairs concurrent replay's complete committed response. Complete four-module contract acceptance remains separately required. |
| SCRUM-63 | Enforced domain/application/infrastructure boundaries, negative shortcut and legitimate port fixture | [ImpactWorkflowArchitectureTest](../../backend/src/test/java/com/spectrace/ImpactWorkflowArchitectureTest.java): real production rules, forbidden application shortcut, foreign root/nested dependencies, own root/nested and legitimate-port controls | Implementation exists despite Jira Idea. This continuation repairs the foreign-root predicate gap with meaningful fixtures. Final compile/ArchUnit/main evidence must name the resulting head. |
| SCRUM-64 | Superseded into SCRUM-62 | Historical Jira pointer to 62 | No separate implementation or duplicate gate. |
| SCRUM-65 | Superseded into SCRUM-62 | Historical Jira pointer to 62 | No separate implementation or duplicate gate. |
| SCRUM-66 | Canonical MySQL change → impact → task → real replacement → publication/audit; IDs/provenance/no duplicates | [Day7RealSoyPublicationPathMySqlTest](../../backend/src/test/java/com/spectrace/workflow/Day7RealSoyPublicationPathMySqlTest.java) now drives actual adoption, declaration creation, real evaluator, HTTP submit/decision/publication for 20 targets and retains 60 histories | New positive implementation path addresses the prior real missing-declaration failure. Require its actual successful result; preserve the prior failure receipt. |
| SCRUM-67 | Real-API controlled-user browser path in containers; visible failure; exact main run/artifacts/logs | [live browser spec](../../frontend/tests/s3-product-flow-live.spec.ts), [isolated local launcher](../../backend/src/test/java/com/spectrace/workflow/S3ProductFlowBrowserHarness.java), [.github/workflows/ci.yml](../../.github/workflows/ci.yml) | Local Spring/MySQL browser launcher is separate from CI Compose. Full path requires actual merged-main containers execution and uploaded artifacts, not only local/PR results. Mandatory M3 negatives need their own assertions too. |
| SCRUM-68 | Adopted staging/demo ADR or verified deployment; roles/promotion/verification/owner/limits | [reviewable identity/staging proposal](../architecture/S3-demo-identity-and-staging-proposals.md); existing [local smoke](../LOCAL_STAGING_SMOKE.md) is explicitly a 3 September historical smoke | **Decision pending:** M5 must select/adopt the exact staging rule and merge/link it. New green CI is evidence for that choice, not an adopted ADR or shared deployment. |
| SCRUM-69 | Superseded into SCRUM-67 | Historical Jira pointer to 67 | No duplicate CI work or independent completion claim. |
| SCRUM-70 | Source-bound Unit-of-Work/component and rollback/audit sequence, rationale/rejected option, Jira link | Existing transactional ports/adapters; [product-flow A07 supplement](../architecture/S3-label-product-flow-a07.md) and its actual class/sequence artifacts | Reviewable integration diagrams exist. Its source-bound Unit-of-Work and rollback rationale now link SCRUM-51/70 explicitly; the artifact remains reviewable, with M5 assessment/acceptance separate. |

## M3 browser delivery — SCRUM-49

| Subtask | Substantive requirement | Actual implementation / test artifact | Boundary after the new flow |
| --- | --- | --- | --- |
| SCRUM-71 | Real change creation/selection/run/findings; correct reload/error/empty context | [Impact.tsx](../../frontend/src/pages/Impact.tsx), [impact client](../../frontend/src/api/impact.ts), exact server ChangeRequest/Impact APIs, live spec and [UI fixture tests](../../frontend/tests/s3-product-flow-ui.spec.ts) | New real consumption replaces the earlier foundation. Full exact-version contract acceptance/reload/error tests still need final source/main evidence. |
| SCRUM-72 | ReviewTask list/detail; exact first replacement/validation; reload/task-switch safety | Finding-to-task links, [Labels.tsx](../../frontend/src/pages/Labels.tsx), exact task GET and existing version-bound validation/feedback; strict task-to-proposed-formula guard | Real collection/list/detail now use ReviewTaskListController, ReviewTaskReadService/JDBC and Reviews.tsx. Status filtering, ordered bounded pagination and nullable exact bindings are explicit. The completed 4066 local browser rereads all twenty CLOSED tasks and exact bound targets. Post-guard source, final read-model/broad regressions and main evidence remain pending. |
| SCRUM-73 | Real permission-aware commands, selected confirmed identity, user switch, old/new durable publication; negatives/no duplicates | [label-workflow client](../../frontend/src/api/label-workflow.ts), [LabelWorkflowPanel](../../frontend/src/components/LabelWorkflowPanel.tsx), server permission/maker-checker/current guards and real live flow | Separate guarded commands and lifecycle/reconciliation UI exist. Actual caller/permission reads and display now use CurrentIdentityController/CurrentIdentityView and CurrentIdentityPanel; command controls check real permissions and maker identity. A controlled actor switch and adopted login/demo choice remain pending; the 4066 local live flow demonstrates twenty officer/QA/publisher publications. The later catalog readiness guard, complete broad verification and new main evidence remain pending. |
| SCRUM-74 | Live main CI positive path plus self-review/missing-permission/failed-validation visible negatives; implemented M3 state/A07/demo evidence | Live spec, separately marked UI fixtures, real validation browser, [product-flow A07 supplement](../architecture/S3-label-product-flow-a07.md) and original [M3 draft](S3-M3-A07-draft.md) | Require clear self-review/missing-permission/failed-validation presentation and merged-main evidence. Current officer self-review HTTP403 is ACL rejection; permitted-creator maker-checker proof is the separate actual service/unit test. No ADMIN compound-grant browser actor exists. The completed 4066 local browser passed at 06:05:38 UTC with twenty publications/CLOSED tasks and sixty history checks; the later catalog readiness correction still needs fresh source-bound browser qualification. Original M3 draft is historical; state/async design must reflect final code and source/tests. |

## M1 change-impact delivery — SCRUM-47

| Subtask | Substantive requirement | Actual implementation / test artifact | Boundary after the new flow |
| --- | --- | --- | --- |
| SCRUM-75 | Recorded contract decisions, impact model/ports, architecture | [M1 review](../s3-m1/SCRUM-75-impact-contract-review.md), domain/port tests, PR48 review and SCRUM-75 comment 10096 | Real M1 contract correction acknowledgement exists. No complete M3/M4/M5 freeze is inferred. |
| SCRUM-76 | Real ChangeRequest create/read and permission/body preconditions before writes | ChangeRequestService/Controller; [ChangeRequestApiMySqlTest](../../backend/src/test/java/com/spectrace/impact/ChangeRequestApiMySqlTest.java), [service tests](../../backend/src/test/java/com/spectrace/impact/application/ChangeRequestServiceTest.java) | Existing implementation. Live change creation adds consumer proof, while full new backend run verifies negative paths. |
| SCRUM-77 | Current released formula lookup: 40 relevant, 20 excluded; no historical matching | RelevantProductDiscovery and catalog lookup; [RelevantProductDiscoveryMySqlTest](../../backend/src/test/java/com/spectrace/impact/RelevantProductDiscoveryMySqlTest.java), [golden relationship test](../../backend/src/test/java/com/spectrace/impact/S3SoyGoldenRelationshipMySqlTest.java) | Existing source and oracle. New real adoption preserves exact old/current/proposed references and tests excluded controls. |
| SCRUM-78 | Fail-fast registry, real N+1 derivation versus published declarations, attributable outcomes; strategy A07 | IngredientSpecImpactStrategy/Registry; [registry test](../../backend/src/test/java/com/spectrace/impact/application/strategy/ImpactStrategyRegistryTest.java), [MySQL strategy](../../backend/src/test/java/com/spectrace/impact/IngredientSpecImpactStrategyMySqlTest.java), [strategy A07](S3-M1-A07-impact-strategy-draft.md) | Existing actual implementation. No client-side classification or automatic legal declaration is introduced. |
| SCRUM-79 | Trigger/query orchestration, one task per required finding, atomic replay and rollback | ChangeImpactAnalysisService/ImpactAnalysisController; [ImpactAnalysisApiMySqlTest](../../backend/src/test/java/com/spectrace/impact/ImpactAnalysisApiMySqlTest.java), [strict concurrent replay](../../backend/src/test/java/com/spectrace/impact/ImpactAnalysisConcurrentReplayMySqlTest.java) | Existing orchestration; normally merged PR69 addresses complete concurrent replay. Require exact main full regression for current evidence. |
| SCRUM-80 | MySQL golden exact results/exclusions, full main browser handoff, actual class/sequence/strategy A07 | [SoyGoldenImpactRunMySqlTest](../../backend/src/test/java/com/spectrace/impact/SoyGoldenImpactRunMySqlTest.java), shared 60-row M2 oracle, [M1 A07](S3-M1-A07-run-change-impact-analysis.md), new real Day7/live tests | Existing golden/A07 artifacts are present despite historical SQL adoption setup. New actual 40 adoption calls and 20 publications supply the missing whole-path evidence once executed on merged main. |

## M4 review/publication delivery — SCRUM-50

| Subtask | Substantive requirement | Actual implementation / test artifact | Boundary after the new flow |
| --- | --- | --- | --- |
| SCRUM-81 | Java lifecycle/latest-validation/current/formula/decision/permission/maker-checker | LabelReviewService, LabelTransitionPolicy, MakerCheckerPolicy; [LabelReviewServiceTest](../../backend/src/test/java/com/spectrace/workflow/application/LabelReviewServiceTest.java), [WorkflowIntegrationTest](../../backend/src/test/java/com/spectrace/workflow/WorkflowIntegrationTest.java) | Existing Java behavior. New guarded HTTP exposure is an adapter, not a new stored-procedure policy. Exact canonical 400/401/403/404/409/500 responses add consumer evidence. |
| SCRUM-82 | Exact target, approved/matching record, atomic supersede/publish/pointer/task/record/audit | JdbcReviewTaskDraftBinding, LabelReviewService.publishReviewTask, JdbcLabelReviewCommandRepository; WorkflowIntegrationTest and V8 migration compatibility/history suites | Existing behavior plus normally reviewed fixes. New first declaration snapshot/HTTP publication makes it reachable. Latest committed-label guard, target mismatch and no duplicate publication must remain exercised. |
| SCRUM-83 | Real MySQL rollback/concurrent/current/self/permission/validation/record negatives | WorkflowIntegrationTest, [LabelWorkflowConcurrencyIntegrationTest](../../backend/src/test/java/com/spectrace/workflow/LabelWorkflowConcurrencyIntegrationTest.java), [MakerCheckerPolicyTest](../../backend/src/test/java/com/spectrace/workflow/domain/MakerCheckerPolicyTest.java), new Day7 HTTP and [declaration HTTP MySQL tests](../../backend/src/test/java/com/spectrace/label/LabelDraftDeclarationsHttpMySqlTest.java) | Substantive tests already exist despite Idea. New all-suite/main result is required. Genuine permitted-creator maker-checker proof is in service/domain unit tests; real MySQL rollback/concurrency and independent approval/publication cases are separate. A DB self-checker negative is not inferred from them. |
| SCRUM-84 | Adopted login/controlled switch with security implications; real State/Transition A07; main E2E | [proposed alternatives](../architecture/S3-demo-identity-and-staging-proposals.md), existing Java policy/ports, integration A07 and new real browser tests | **Decision pending:** M4 must adopt the login/demo option and allowed actor-switch representation. New guarded UI/API and isolated seeded contexts do not adopt that choice or sign a human approval. |

## Scope of the new positive evidence

Successful new backend and browser runs can complete the technical adoption,
classification, exact first-target declaration, actual validation, independent
test-checker decision and atomic publication chain. They can also demonstrate
history retention and the explicitly executed negative/rollback/race paths.

They cannot, by themselves, supply an adopted login or staging decision, approve
a contract revision on behalf of M1/M3/M4/M5, adopt a controlled actor switch,
execute an unasserted negative scenario, or serve as
course assessment acceptance. Those gaps should remain explicit while ordinary
review/integration and independently available tests proceed.

No teammate issue was edited by this documentation work. M2's 5/7 child-task
count is solely its own seven-task scope and is not a Sprint-wide completion
percentage.

The new collection and current-caller read models are physically implemented and
mapped in the [A07 supplement](../architecture/S3-label-product-flow-a07.md).
Their HTTP/MySQL and disabled-development-auth tests require actual execution;
no unrun source is counted as a passing check. The final integration predecessor
is main c81fe906 after normal merges of PR68/69/70/58. Its passing qualification
receipt covers the predecessor, not this new extension.

The completed 4066 source has an actual twenty-publication local browser receipt.
A later catalog-readiness defect requires its own narrow guard and delayed/failed
catalog zero-POST regressions, followed by a fresh full browser run. The current
broad backend verification has genuine positive-fixture authentication failures;
test-only explicit opt-in is under correction with the production guard unchanged.
No stage-wide Done or new-main qualification is inferred from the older pass.
