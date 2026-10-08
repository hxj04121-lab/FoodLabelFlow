# S3 compound maker negative and live opt-in qualification

This supplement defines the additional regression and evidence scope after the
merged `0dc1737` qualification. Actual passing results require their own local,
PR and resulting-main receipts; this document does not prefill a run or record
M1/M3/M4/M5 acceptance. The earlier officer ACL403 and twenty independently
approved publications retain their original source and execution bindings.

## Disposable identity fixture

[The compound fixture](../../backend/src/test/resources/fixtures/s3-compound-maker.sql)
adds only `user_test_compound_maker_s3` and two links from that new user to the
existing `role_label_officer` and `role_approver`. Its password hash is NULL.
Existing users, roles, permissions, role-permission rows, security configuration
and migrations are unchanged. The fixture belongs only to the owned MySQL
Testcontainers database or the existing disposable CI Compose database. It must
not be installed in a shared or production database.

`dev-external-test-compound-maker-s3` and its uppercase ASCII spelling must first
return the same internal user ID from the real `/api/identity/current`, with
`LABEL.CREATE` and `LABEL.APPROVE`. This uses the existing MySQL subject collation;
it does not add a mapping table, multiple IdP identities or an actor-switch UI.
Production authentication and login/demo ADR decisions remain separate.

## Backend transaction, HTTP and database procedure

[CompoundMakerCheckerHttpMySqlTest](../../backend/src/test/java/com/spectrace/workflow/CompoundMakerCheckerHttpMySqlTest.java)
uses real adoption, impact, declaration creation, evaluator and submission to
produce an exact-current `PENDING_REVIEW` label created by this compound user.
The subject's real profile and the persisted creator must agree before rejection
can count as maker-checker evidence.

HTTP APPROVE requests use both subject spellings and must return 403,
`AUTHORIZATION_DENIED`, with `Maker-checker violation: creator cannot approve own
label`. Missing permission is a different negative. Production permission,
identity and policy implementations execute normally; observation spies must not
stub outcomes. The active Spring transaction and exact creator/policy arguments
are observed. All business rows and existing identity rows must remain unchanged
after each rejection: no decision, approval, task transition, publication or
command audit may survive. A distinct existing QA can subsequently approve and
the existing publisher can publish through the real commands.

The existing database `sp_record_label_decision` is tested separately with the
same creator and APPROVE, requiring SQLSTATE 45000 and unchanged rows. HTTP uses
Java policy followed by JDBC; it does not call that procedure. A procedure result
must not be reported as the HTTP implementation or vice versa.

## Real browser and consented UI

[The fixture UI test](../../frontend/tests/s3-product-flow-ui.spec.ts) supplies
CREATE and APPROVE to the creator and explicitly checks consent; approval must
remain disabled because the creator is not independent.

[The real S3 browser](../../frontend/tests/s3-product-flow-live.spec.ts) requires
the compound fixture environment whenever `LIVE_S3_FLOW=1`; it cannot silently
skip the additional negative. One of the twenty makers is the compound subject,
the other nineteen use the original officer. Actual profile reads prove both
spellings resolve to the creator with APPROVE. Consented self-approval controls
remain disabled; real API attempts return the specific policy403 and persisted
label/task rereads remain unchanged. Existing QA and publisher contexts continue
all twenty approvals/publications, twenty CLOSED targets, repeat409 checks and
sixty immutable-content history comparisons. No browser business response is
intercepted and no SQL fixture inserts labels, declarations, PASSED runs,
decisions or publications.

## Three existing opt-in tests

| Test | Earlier skip reason | Additional qualification |
| --- | --- | --- |
| [catalog-live](../../frontend/tests/catalog-live.spec.ts) | `LIVE_CATALOG` was absent in the fixture coverage suite; the case requires a real backend. | Set `LIVE_CATALOG=1` in the owned full stack. Read actual products/formula trace and exercise product detail/version history. The separate simulated unavailable-backend case keeps its fixture scope. |
| [validation-live](../../frontend/tests/validation-live.spec.ts) | `LIVE_VALIDATION` was absent in that suite. | Set `LIVE_VALIDATION=1`. Run actual PASSED and blocking FAILED before S3 changes current pointers; this was also separately executed by the prior main. |
| [formula-lifecycle-fullstack](../../frontend/tests/formula-lifecycle-fullstack.spec.ts) | `LIVE_WRITES` was absent; the case requires explicit consent for real fixture writes. | Set `LIVE_WRITES=1` only in the disposable full stack. Create/publish a real formula and compare immutable previous formula items/release time. |

[The owned local harness](../../backend/src/test/java/com/spectrace/workflow/S3ProductFlowBrowserHarness.java)
and [existing CI containers job](../../.github/workflows/ci.yml) execute catalog
read, validation, S3 compound negative and independent publications, then formula
lifecycle in separate sequential processes. S3 observations and harness SQL
twenty-publication proof are captured before the final formula write. That later
write deliberately changes one product's current formula; an earlier S3 pointer
snapshot must not be misrepresented as database state after it.

The normal fixture coverage suite retains opt-in skips because it has no real
backend. Its counts remain separate from these explicitly enabled live results.
Screenshots and raw observations use the supplied evidence directory. The CI
workflow retains its existing browser-artifact destination. Sharing a separately
prepared local delivery package follows the user's explicit delivery instructions.
