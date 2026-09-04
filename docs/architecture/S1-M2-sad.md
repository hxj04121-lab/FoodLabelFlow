# S1-M2 SAD — shared allergen and validation contracts

Status: **prepared on `prep/s1-m2-sad-contracts`; not integrated**. This document is a
Sprint 1 M2 design decision, not a claim that the described endpoints or M2's Sprint 2
validation slice are implemented.

## Decision context

The M2 work order (`.project-control/work-orders/S1/M2-sad-acceptance-support.yaml`)
requires architecture decisions, acceptance-criteria refinement, and shared-contract
support. It also prohibits schema changes without PM change control. The binding PM
contract hash is recorded in [`.project-control/pm-contract.sha256`](../../.project-control/pm-contract.sha256).

The canonical Flyway baseline already contains `allergen`, `rule_set_version`,
`rule_definition`, `ingredient_allergen`, `label_version`, `validation_run`, and
`validation_result`. M2 therefore defines a contract over those concepts; it does not
introduce a migration or a second persistence model.

## SAD-001 — contract-first module boundary

**Decision.** The validation module owns validation orchestration and result semantics.
The allergen module owns allergen lookup and derivation policy. The label module owns
the label-version aggregate. Cross-module calls use application-level ports/DTOs; a
module must not access another module's repository or issue SQL against another
module's tables.

| Concern | Owner | Contract supplied to other modules |
| --- | --- | --- |
| Label version and whether it is current | `label` | immutable `LabelValidationSnapshot` |
| Allergen catalogue and derived allergen facts | `allergen` | immutable `AllergenFact` list |
| Rule-set selection and evaluation orchestration | `validation` | `ValidationRun` and result DTOs |
| Actor/permission decision | `identity` | authenticated actor and permissions (M4-owned implementation) |
| Audit persistence | `audit` | append-only audit-event port |

**Rationale.** This preserves the modular-monolith architecture in the PM contract and
keeps BR-02 (relevant lookup), BR-03 (multi-item relational derivation), BR-05 (exact
current validation guard), BR-07 (permission checks), and BR-10 (same-transaction
audit) testable without hidden database coupling.

**Consequences.** An implementation may add interfaces inside the owning modules, but
may not make the API controller query a catalog/allergen table directly. A changed
shared DTO requires contract review by M1, M4, and M5 before integration.

## SAD-002 — explicit error and evidence envelope

**Decision.** All M2-shared HTTP failures use the `ApiError` schema in
[`allergen-validation-api-v1.yaml`](../contracts/allergen-validation-api-v1.yaml).
`code` is stable and machine-readable; `message` is safe for a caller; `traceId` is an
observability correlation value when available; `evidenceId` is populated only when a
real evidence record exists. A caller must never infer a business decision from a
free-text message.

| HTTP status | Stable code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | request syntax or required field is invalid |
| 403 | `AUTHORIZATION_DENIED` | authenticated actor lacks the M4-defined permission |
| 404 | `RESOURCE_NOT_FOUND` | requested label version or validation run is absent |
| 409 | `LABEL_VERSION_NOT_CURRENT` | BR-05 prevents validation of a non-current label |
| 422 | `VALIDATION_PRECONDITION_FAILED` | the current label cannot be evaluated from canonical inputs |
| 500 | `INTERNAL_ERROR` | an unexpected failure; no internal details are exposed |

The reserved evidence naming convention is `EVID-M2-S1-<KIND>-<NNN>`. It is a naming
rule only: no identifier in this document represents already-collected evidence.

## SAD-003 — Strategy for versioned validation rules

**Design problem.** A rule set can contain multiple `rule_type` values and must remain
versioned. A single `if/else` evaluator would couple every new rule type to one service,
make exceptional behavior hard to isolate, and obscure which rule generated a result.

| Candidate | Assessment |
| --- | --- |
| One conditional evaluator | Small initially, but violates open/closed growth and gives weak per-rule tests. Rejected. |
| Chain of Responsibility | Useful when only the first handler may decide; validation must collect all applicable findings. Rejected. |
| Rule-evaluator Strategy registry | Each evaluator supports one `rule_type`; the orchestrator selects all applicable evaluators and persists attributable results. Selected. |

The selected design has `ValidationOrchestrator` load the exact current label snapshot
and active rule-set version, obtain a `RuleEvaluator` for each `rule_type`, collect
`ValidationFinding` values, persist a `ValidationRun` plus `ValidationResult` values,
and request an audit event in the same transaction. This is a preparation for M2's
Sprint 2 implementation, not a Sprint 1 endpoint commitment.

For the MVP there is no background queue: a successful create operation returns only
after the run is persisted. Introducing asynchronous execution later is an architecture
change requiring a new SAD/contract revision because it changes observable status and
retry semantics.

## Cross-module integration checkpoints

1. M1 confirms which label/formula snapshot fields are exposed and that the current
   label relation is authoritative.
2. M4 supplies permission names and maps `AUTHORIZATION_DENIED` to its identity/RBAC
   contract; M2 does not implement RBAC in this preparation branch.
3. M5 agrees the Testcontainers fixture, API contract test location, and CI evidence
   binding to the actual integration commit.
4. Any new rule-set lifecycle, async execution, database change, or contract-breaking
   error code reopens this SAD for cross-module review.

## Decision review state

This decision has not yet received the required cross-module review or human
acceptance. It must be rebased/reconciled against the actual accepted `main` commit
after PR #2 merges before being presented as an M2 S1 deliverable.
