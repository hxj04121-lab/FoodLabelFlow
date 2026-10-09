# Returned ReviewTask correction proposal

Status: **local implementation proposal; actual M4 workflow adoption and M3 consumer
acceptance must be recorded separately**. This document does not sign for either owner.

M3's PR76 correctly reports the current main behavior: REQUEST_CHANGES returns
the same immutable LabelVersion to DRAFT and its ReviewTask to OPEN. That task
retains its draft/target IDs. First-draft creation rejects an existing binding,
and the old validation can satisfy the old submission gate. The original
[product-flow A07](S3-label-product-flow-a07.md) deliberately deferred corrections.
This separate proposal closes that gap without modifying a historical declaration.

## Recommended workflow

Keep the same ReviewTask and its impact finding/current published label as the
stable review context. Its current draft and target references advance only by
`POST /api/review-tasks/{reviewTaskId}/draft-revisions` with an exact
`expectedLabelVersionId` and a complete declaration array. The operation requires
the existing `LABEL.CREATE` permission, matching first-snapshot creation; it adds
no assignee restriction or role grant. The authenticated revision creator becomes
the new LabelVersion maker and cannot APPROVE that version.

The returned task must be OPEN and unresolved with REQUEST_CHANGES as its
current decision, or a null decision summary plus an attributed REQUEST_CHANGES
ApprovalRecord for that exact task/label. The target must equal the expected
returned DRAFT, and the draft reference must match when non-null. Existing
target-only legacy bindings therefore have an explicit correction path too.
The product row is locked first. The task, old snapshot, published-label context,
impact finding and latest version are then checked with locking reads. The
finding's proposed/current formula must still equal the product's current
released formula. A task for an older impact context cannot silently acquire
an unrelated replacement.

The existing creation gateway makes a fresh LabelVersion N+1 and fresh
USER_ENTERED declarations. It uses the existing current released formula and
ACTIVE/effective highest-version rule set for the same jurisdiction. This
preserves current creation semantics: the new label rule-set ID is not asserted
to equal the earlier impact-run rule set. The response includes the actual pinned
IDs, and validation must use the new label's rule-set ID.

The gateway reads the chosen rule set and latest version with `FOR SHARE`.
The earlier task-to-product lookup can establish a repeatable-read snapshot
before a product-lock wait; these current locking reads therefore see committed
rule-set activation/retirement and versions after that wait, rather than selecting
a now-retired rule set from the earlier snapshot.

In the same transaction, both task references advance to the new ID, its mutable
decision/resolver summary clears, and a LABEL_DRAFT_REVISED audit stores the
task, old ID, new ID and actual actor. The old label content/lifecycle, declarations,
validation runs/results, ApprovalRecords and audits are unchanged. The task
identity, finding, assignee, creation metadata and current published label are
unchanged. Only subsequent authorized publication changes the published pointer.

```mermaid
sequenceDiagram
    actor Maker
    participant API as Revision application service
    participant DB as MySQL transaction
    Maker->>API: task ID + expected returned label + complete declarations
    API->>DB: lock product, task, versions and finding; verify REQUEST_CHANGES
    API->>DB: insert new LabelVersion and declaration snapshot
    API->>DB: rebind both task references; clear current decision; append lineage audit
    DB-->>API: commit all changes
    API-->>Maker: 201 new DRAFT and Location
    Maker->>API: validate exact new label and pinned rule set
    API-->>Maker: persisted new validation evidence
    Maker->>API: submit new label after its own latest PASS
```

The old returned version cannot be submitted again, even if its old PASS remains
or somebody validates its unchanged content again. A new revision begins with no
validation or approval evidence. The ordinary gate requires its own latest exact
label/rule-set PASSED run, then an independent checker and the separate guarded
publication command. No validation or approval record is copied between versions.

The frozen S2 rule permitting an older independent DRAFT to be validated against
its current released formula is preserved. Validation of historical input is
not authorization to resubmit or publish a retired task target. The current
submit/decision/publication latest-version guards continue to reject stale IDs.

## Conflicts, rollback and direct helper compatibility

The [OpenAPI candidate](../contracts/s3-label-product-flow-api-v1.yaml) and
[error matrix](../contracts/s3-label-product-flow-error-matrix-v1.md) define the
explicit route. A stale target, replay, concurrent loser or changed formula
returns 409 LABEL_VERSION_CONFLICT. A task in the wrong stage or resubmission of
the returned version returns 409 LABEL_WORKFLOW_CONFLICT. An absent task is 404
REVIEW_TASK_NOT_FOUND. Strict revision JSON errors are 400 LABEL_COMMAND_INVALID;
application declaration errors retain 400 LABEL_DRAFT_INVALID through the existing
global label advice. Authentication and permission retain 401/403.

A failed declaration insert, task rebind or audit rolls back the entire revision.
The expected old ID is a concurrency condition, not a generic idempotency key.
One successful revision makes every competing command with that old expected ID
stale, preventing duplicate rows/audits. After a timeout, consumers read the exact
task and referenced label without blindly repeating a write. Observed state does
not alone establish which actor's command caused it.

V9 replaces only the current `sp_submit_label_for_review` procedure to apply the
returned-version guard to direct helpers too. The legacy V4 decision helper did
not populate V7's decision summary, so the guard also checks an attributed
REQUEST_CHANGES ApprovalRecord for the exact active task/label. It does not choose
among same-second decisions by random UUID. V1-V8 and legacy migration fixtures
remain unchanged; historical target('6') upgrade histories stay reproducible.

## Owner decision and evidence boundary

M2 recommends **same-task new immutable version**, with existing CREATE permission
and active rule-set selection, because it preserves impact/task identity and all
old decisions while giving M3 an explicit correction path. M4 must confirm that
workflow choice, or identify a concrete alternative such as closing the task and
creating a new review context. M3 must confirm consumer behavior and update its
page/A07 wording after the chosen protocol is adopted. Neither automated tests
nor a comment written under M2's account constitutes their acceptance.

Technical qualification covers real return/correct/revalidate/independent
approve/publish, old snapshot/history preservation, lost/stale/replayed targets,
permissions, current-formula and catalog checks, a controlled transaction rollback,
concurrent callers, and the current direct-helper guard. Record actual test
counts/runs and exact commit separately; do not reuse the older 619-test baseline
as evidence for this new runtime code.
