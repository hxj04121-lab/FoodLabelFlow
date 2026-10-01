# A07 draft — M3 review/publication UI

Owner: Xu Feiyang / M3. Related: SCRUM-49, SCRUM-71–74.
Date: 2026-09-29. Status: proposed interaction design, not implementation evidence.

## Scope and source of truth

The M3 use case is **Review and publish a replacement label through the browser**.
M3 presents task/version context, validation feedback and permitted operations.
M4 authenticates actors and enforces submission, maker-checker, transitions and
publication. M1 creates impact findings/tasks; M2 defines the shared contracts;
M5 supplies persistence and CI support. The browser does not decide whether a
label may legally transition or perform publication transactions.

Sources: Jira SCRUM-49/71/72/73/74 and module parents SCRUM-47/48/50/51 read on
28 Sep; [M2 Day 1 candidate](S3-M2-day1-contract-diff-freeze-candidate.md), merged
in `0745fcd`; current source listed below. S3 method names in these diagrams are
logical operations, **not proposed HTTP paths or implemented Java/React methods**.
The exact contract, login approach and state transitions still require review.

## Existing implementation and proposed extension

| Area | Current main / this foundation | Proposed next step |
| --- | --- | --- |
| Impact route | `frontend/src/pages/Impact.tsx`: unavailable state, disabled selector/action, materials navigation, no API client | Wire the frozen change/run/finding contract in SCRUM-71 |
| Review route | `frontend/src/pages/Upcoming.tsx` remains a placeholder | Task list/detail and replacement label navigation in SCRUM-72 |
| Exact label reads | `frontend/src/api/labels.ts`: getLabelDraft, getLabelDerivedAllergens, getLabelDeclarations | Reuse with the task's bound label; never substitute current/latest |
| Validation feedback | `frontend/src/components/LabelValidationPanel.tsx`: version checks and rule-level results | Reuse for the task target; assess pending-response/reset behavior at the task boundary |
| Read cancellation | `frontend/src/components/useLabelRead.ts`: abort and active-request guard | Apply this existing approach to task/run reads; include identity in invalidation |
| Identity | Label client uses fixed local demo headers; shell profile is a preview | Adopt M4's login/controlled-user-switch decision; no identity claim from avatar text |
| Review/publication | No S3 HTTP controller in the reviewed main | M4 provides authoritative operations and guards; M3 consumes results |

## Analysis sequence: user-visible review and publication

Preconditions: an impact finding requires review; its task has a replacement
draft bound to an adopted formula; M4 supplies authenticated Maker A and Checker
B. The maker in the self-approval rule means the label creator, not merely the
last person to submit it. Any missing target must be resolved through M4's
agreed creation/binding flow before validation or submission.

```mermaid
sequenceDiagram
    actor maker as Maker A
    actor checker as Checker B
    participant ui as M3 Review UI
    participant tasks as Task and Label Boundary
    participant validation as Validation Service
    participant workflow as M4 Review and Publication
    participant store as Transactional Persistence
    maker->>ui: Open the finding's review task
    ui->>tasks: Read task and its exact replacement label
    tasks-->>ui: Task, target versions and permitted context
    ui-->>maker: Show task, label and version context
    maker->>ui: Validate replacement label
    ui->>validation: Validate exact label and rule-set versions
    validation-->>ui: Validation run and rule-level results
    ui-->>maker: Show blocking findings or successful validation
    maker->>ui: Submit for review
    ui->>workflow: Submit using authenticated actor and version context
    workflow->>validation: Check latest validation for the same versions
    alt Validation or state precondition fails
        workflow-->>ui: Agreed domain error
        ui-->>maker: Explain failure; do not show submission success
    else Submission permitted
        workflow->>store: Persist submission and required audit
        store-->>workflow: Committed
        workflow-->>ui: Authoritative review state
        checker->>ui: Switch to Checker B and open the task
        ui->>tasks: Reread task, label and actor-dependent context
        tasks-->>ui: Exact target and current permitted actions
        checker->>ui: Approve replacement
        ui->>workflow: Request approval for the exact target
        alt Actor, permission or current-version guard fails
            workflow-->>ui: Agreed error; no successful transition
            ui-->>checker: Explain rejection and refresh when appropriate
        else Approval permitted
            workflow->>store: Record approval
            store-->>workflow: Approval recorded
            workflow->>store: Publish atomically under approved-state guards
            store-->>workflow: Old superseded, new published, task resolved, evidence committed
            workflow-->>ui: Authoritative outcome
            ui->>tasks: Reread task and old/new labels
            tasks-->>ui: Persisted publication state
            ui-->>checker: Show published and superseded versions
        end
    end
```

The approval/publication segment groups logical responsibilities only. M4/M2
must decide whether publication is part of the approval command or a separate
authorized command. The diagram does not assert two backend transactions or
an automatic publish trigger. For a separate command, add the corresponding
actor action and request after approval; until confirmed, do not implement
either behavior in the UI. Publication includes the current-pointer change,
task resolution, PublicationRecord and AuditEvent in one backend transaction.

## Design problem: keeping version, identity and async state consistent

A task page can still have a request for task A in flight after the user opens
task B. The actor can also change while a validation or decision is pending.
Blindly accepting late responses can show a passing result for the wrong label,
enable an operation for the wrong identity, or report publication that has not
been verified. Independent booleans such as `approved` and `published` also
permit contradictory combinations in client state.

### Options and decision

| Option | Benefit | Cost / failure mode | Decision |
| --- | --- | --- | --- |
| Independent booleans with optimistic lifecycle updates | Small initial UI | Contradictory states, stale responses, rollback complexity; frontend may imply a transition the server rejected | Reject for review/publication |
| A new frontend state-machine library mirroring all domain states | Explicit transition model | Adds a dependency and duplicates an unsettled M4 policy | Defer; reconsider only if UI complexity warrants it |
| Local explicit request states, exact-context guards and authoritative rereads | Fits existing React/hooks; keeps server rules authoritative | Requires deliberate invalidation and command reconciliation | Proposed approach |

Use a small presentation state model (unavailable, loading, ready, error,
submitting, outcome-uncertain) rather than pretending these are server lifecycle
states. Do not introduce a reusable workflow framework in the foundation.
Keep the server's lifecycle and permission information distinct from transport
state. UI action availability is a convenience, never authorization.

On task, target-version or actor change, invalidate pending reads and clear
identity-sensitive results. AbortController reduces wasted work; an active
request/context check prevents late commits even when cancellation arrives too
late. Keep label, formula, rule-set and jurisdiction bound consistently with
the existing S2 contracts. The exact task DTO and actor representation are TBD.

### Proposed design sequence: reads and uncertain commands

```mermaid
sequenceDiagram
    actor user as User
    participant view as Review Page
    participant client as API Client
    participant api as Owner API
    user->>view: Open task A as actor X
    view->>client: Read with context A and cancellation signal
    client->>api: Read authoritative resources
    user->>view: Switch to task B or actor Y
    view->>view: Invalidate A and clear old results/actions
    view->>client: Abort old read and start new-context read
    api-->>client: Late response for A
    client-->>view: Old read resolves or fails
    view->>view: Ignore response from invalidated context
    api-->>client: New-context response
    client-->>view: Resources for current context
    view->>view: Verify target binding, then render
    user->>view: Request a permitted operation
    view->>view: Lock duplicate submission for current command
    view->>client: Send command under captured actor/target context
    client->>api: Command
    alt Confirmed response
        api-->>client: Authoritative result or domain rejection
        client-->>view: Response for captured context
        view->>view: Apply only if context still matches
        view->>client: Reread state after successful operation
    else Response lost or outcome uncertain
        client-->>view: Uncertain outcome
        view->>view: Keep write retry blocked; show reconciliation guidance
        view->>client: Reconcile using frozen owner contract
    end
```

Cancelling a browser request does not undo a committed server write. Never
automatically resend a decision/publication command after a timeout. If the
actor changes mid-command, do not apply the old result to the new actor's page;
reconcile the original operation and reread the new context. Read retries are
different from write retries. M4/M5 must define how command outcomes and
idempotency are identified before this flow is implemented.

## UI invariants and verification

1. No selected/bound target means no validation or review operation.
2. A displayed validation result matches the label and rule-set versions being
   reviewed; mismatches produce an error, not a passing badge.
3. Missing permission, self-approval and validation gates are enforced by the
   backend, even if someone bypasses disabled UI controls.
4. A successful approval response alone is not assumed to mean publication.
   Display the server-returned lifecycle, then verify persisted old/new versions.
5. Failed/unknown requests never become empty-success results or seed data.
6. Task/actor changes cannot inherit old success, error or pending state.

Verification maps to [the test design](S3-M3-test-design.md): UI-05/07/08 cover
stale reads and version/identity changes; UI-10 covers uncertain writes;
E2E-02/03/04 prove required backend rejection and user feedback; E2E-01 checks
durable publication and history. Foundation tests currently prove only the
unconnected impact page's behavior.

## Open decisions and final A07 gate

- M2/M1: final run/finding/task resource shapes and exact version references.
- M4: task query/binding operations, allowed-action/permission representation,
  login decision, submission rules, decision states and publication command model.
- M4/M5: command reconciliation, conflict tokens and publication evidence IDs.
- M3: after integration, replace logical participants with the actual components,
  clients and operations; verify diagrams against code and add screenshots.
- Capture the design tradeoff before/after with implemented examples, link the
  final contract revision and main CI evidence, and review with module owners.

This is the requested initial A07 draft. It does not claim SCRUM-74 completion,
backend design ownership, or a currently working review/publication UI.
