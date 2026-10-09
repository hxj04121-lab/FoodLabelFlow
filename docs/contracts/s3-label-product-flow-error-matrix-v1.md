# S3 label product-flow error matrix — extension v1.1.0 revision proposal

[Later compound-maker qualification](../evidence/S3-compound-maker-negative-20261008.md)
separately exercises a creator who has APPROVE through isolated MySQL, HTTP and
real browser paths. It adds tests without changing the wire contract or the
earlier officer missing-permission scope. Actual results remain receipt-bound.

State: **candidate; exact cross-module acceptance pending**. The associated
[OpenAPI extension](s3-label-product-flow-api-v1.yaml) adds the declaration-create
and workflow adapter boundary, paged task reads and current-caller read. The existing
[impact matrix](s3-impact-api-error-matrix-v1.md), declaration read and frozen
S2 validation contract keep their earlier versions and responsibilities.

| HTTP | Stable code | Trigger | Recovery and persistence rule |
| --- | --- | --- | --- |
| 400 | `LABEL_COMMAND_INVALID` | Non-object/missing/malformed JSON, duplicate keys, trailing JSON, unknown request fields, wrong JSON types, forbidden actor/source/state fields, invalid decision or blank/oversized command values. | Correct the command. The four write routes validate the command before writes. Framework method/media failures retain 405/415 status; no canonical error-code/envelope promise is made for a rejection before handler lookup. |
| 400 | `LABEL_DRAFT_INVALID` | Invalid draft/declaration application input: unknown/noncanonical allergen ID, missing jurisdiction catalog membership, repeated case-insensitive allergen ID, unsupported declaration type or invalid first-snapshot input. | Use canonical catalog IDs such as `all_soy`, not `SOY`. No new label, declaration or binding survives rejection. |
| 400 | `REVIEW_TASK_QUERY_INVALID` | Task collection status is blank/unknown/lowercase, limit or offset is malformed, limit is outside 1–100 or offset outside 0–100000. | Correct the query. Filtering precedes deterministic pagination; no business write occurs. |
| 401 | `AUTHENTICATION_REQUIRED` | Missing provider/subject, unknown subject, inactive identity, unsupported non-ASCII provider namespace, or DEV_EXTERNAL when the existing development-auth flag is false (case-insensitive provider match). | Obtain the existing supported connected identity context. Do not fabricate a subject in the UI; no write occurs. |
| 403 | `AUTHORIZATION_DENIED` | Caller lacks the operation permission; APPROVE caller is the label creator. | A distinct permitted checker must approve. UI controls do not replace the backend check. No decision/task/approval/audit transition commits. |
| 404 | `LABEL_NOT_FOUND` | The exact path label does not exist. | Correct the label ID; do not silently select a latest label. |
| 404 | `REVIEW_TASK_NOT_FOUND` | The exact task read/publication path resource is absent. | Correct the task ID and reread its binding. No publication commits. |
| 405 | Framework response | Unsupported HTTP method; may fail before controller lookup. | Use the documented method. No command executes; this revision does not promise a stable code or four-field body. |
| 415 | Framework response | Command content type is not application/json; may fail before controller lookup. | Correct Content-Type. No command executes; this revision does not promise a stable code or four-field body. |
| 409 | `LABEL_VERSION_CONFLICT` | Current label/formula changed, publication body does not match the task target, requested first task is mismatched/ambiguous, or an unresolved task is already bound. | Reread exact resources and reconcile; no blind command replay, new draft or task rebind. |
| 409 | `LABEL_WORKFLOW_CONFLICT` | Invalid lifecycle transition, missing/failed latest exact-label/rule-set validation, missing pending task, final APPROVE decision/record absent, or task already resolved. | Fix the domain prerequisite through supported operations. No synthetic PASSED run or direct SQL state/decision may stand in for the gate. |
| 500 | `INTERNAL_ERROR` | Unexpected application, persistence or audit failure. | The write transaction rolls back. Treat a lost/failed response as uncertain until exact label/task reads reconcile it; no automatic repeat decision/publication. |

Expected controller/identity errors use the shared four-field `ApiError`: `code`, `message`, `traceId`,
`evidenceId`. IDs remain null when real corresponding identifiers do not
exist. Consumers branch on the stable code, not a message substring. The
identity advice has higher precedence, preserving 401 and 403.

## Operation and permission map

| Operation | Request | Required permission | Success |
| --- | --- | --- | --- |
| `POST /api/labels/drafts` | Product/jurisdiction, optional declarations and exact expected ReviewTask | `LABEL.CREATE` | 201 LabelDraft plus Location. Declarations and first binding committed atomically. |
| `POST /api/labels/{id}/review-submissions` | Exactly `{}` | `LABEL.SUBMIT_REVIEW` | 200 same-label resource in PENDING_REVIEW. A returned REQUEST_CHANGES version is 409 `LABEL_WORKFLOW_CONFLICT`; create a fresh revision first. |
| `POST /api/labels/{id}/review-decisions` | APPROVE with optional comment | `LABEL.APPROVE` plus independent maker-checker | 200 same-label APPROVED; task still unresolved IN_REVIEW. |
| Same decision route | REQUEST_CHANGES with optional comment | `LABEL.REQUEST_CHANGES` | 200 same-label DRAFT; task OPEN, unresolved. |
| `POST /api/review-tasks/{id}/draft-revisions` | Exact `expectedLabelVersionId` plus complete `declarations` | `LABEL.CREATE` | 201 new LabelDraft plus Location; both task references atomically advance, decision/resolver summary clears. Local proposal pending actual M4 workflow adoption. |
| Same decision route | REJECT with optional comment | `LABEL.REJECT` | 200 same-label REJECTED; task CLOSED/resolved. |
| `POST /api/review-tasks/{id}/publications` | Exact `labelVersionId` | `LABEL.PUBLISH` | 200 same-label PUBLISHED/current; task CLOSED/resolved and durable publication/audit. |
| `GET /api/review-tasks` | Optional exact status, limit default 20 (1–100), offset default 0 (0–100000) | Active resolved identity; no added operation permission | 200 direct ReviewTaskView array ordered created_at DESC/task ID ASC, possibly empty. |
| `GET /api/identity/current` | Existing connected request context | Active resolved identity; no grants or actor selection | 200 actual userId/username/displayName and sorted roles/permissions. |
| Exact label/declaration/task GET | Path ID | Existing active identity; no new permission grant | 200 direct resource, including null unbound task references or an empty declaration list. |

The response to creation/submission/decision/publication is the existing
`LabelDraft` resource. It is not a new publication-evidence envelope. Persisted
records and task state are checked separately in integration tests and exact
resource rereads.

## Snapshot and recovery limits

- Omission of declarations retains legacy empty-snapshot behavior. Empty
  declarations never imply allergen-free; the real validator may reject them.
- Entries are explicit legal-label input; no derived-allergen result is
  automatically converted into a declaration. The server fixes source to
  USER_ENTERED and rejects a caller-supplied source.
- There is no declaration PATCH/PUT. REQUEST_CHANGES returns the same immutable
  target to DRAFT. The explicit revision proposal creates a new label version and
  advances the same returned task under an expected-target check; it never changes
  the old snapshot or copies its PASS. The new version requires its own validation.
  Old-target/replayed/concurrent revisions return 409 `LABEL_VERSION_CONFLICT`;
  wrong task stage returns 409 `LABEL_WORKFLOW_CONFLICT`; an absent task is 404
  `REVIEW_TASK_NOT_FOUND`. Strict revision JSON errors use 400 `LABEL_COMMAND_INVALID`;
  application declaration errors retain 400 `LABEL_DRAFT_INVALID` through the existing
  global label advice. All failed revision writes roll back.
  This proposal is not an attributed M4 approval or a replacement of earlier acceptance.
- The separate APPROVE response is not publication. An unresolved approved task
  can only be published by the guarded publication command.
- Write timeouts/cancellation do not undo a committed operation. Read the exact
  task and label to determine the result before considering another command.
- The browser's seeded fixture contexts are test arrangements. Their existence
  does not record the M4 login decision or a human approval.

## Read models and identity boundary

Task collection pages contain the same eight fields as exact task reads. Nullable
draft/target/decision/resolvedAt values retain their meaning; no latest-label
substitution occurs. Pages do not claim a stable multi-page snapshot or a total
count. The offset cap is a traversal limit, not proof that all tasks were read.

The current-caller resource contains only five actual DTO fields. Permission-aware
presentation uses this read and stays disabled while it is loading/unavailable;
application services remain authoritative for command permissions and maker-checker.
Refreshing the resource rereads the connected actor; it does not select a different
actor. A controlled switch and the adopted login/demo choice remain proposed.

[ExternalActorResolver](../../backend/src/main/java/com/spectrace/identity/application/ExternalActorResolver.java)
enforces the existing DEV_EXTERNAL opt-out and rejects non-ASCII provider namespaces
before the database's accent-insensitive lookup, without changing its property, credentials
or grants. The same resolver is reused by the existing validation HTTP adapter.
The disabled-flag regression must execute before reporting that enforcement as
verified; this document records source behavior rather than a prefilled result.

The browser's existing officer maker has no LABEL.APPROVE. Its attempted
self-approval is therefore a 403 permission failure, distinct from a permitted
creator reaching MakerCheckerPolicy; both map to AUTHORIZATION_DENIED. ADMIN
does not have CREATE/APPROVE. The service/unit policy test uses an actor that
already holds APPROVE and proves the independent guard without changing grants.

The older RequestImpactIntegration now uses the same resolver for existing
change-request/impact reads and writes. Its actual unchanged sixteen-case
regression reproduced three pre-fix failures, then passed all sixteen cases:
the three disabled-flag paths changed from 200/404/404 to 401/401/401 before
resource lookup with zero business-table changes. That targeted closure does
not substitute for final all-source verification or the completed live browser.

A completed 4066 local live browser run records twenty actual publications and
the officer's ACL403 with no decision write. Its maker-checker policy reached flag
is false. Permitted-creator policy/service unit tests remain the actual independent
negative; no database policy-negative result is asserted. A later catalog-readiness
UI guard needs fresh browser qualification. It blocks first creation while legal
input is unavailable and does not revise the server's optional declaration schema.
