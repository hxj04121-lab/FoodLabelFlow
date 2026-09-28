# SCRUM-75 — impact contract review, domain model and ports

Implementation date: **2026-09-28 (Asia/Shanghai)**, the Sprint 3 contract-freeze day.
Estimate: **2 story points**. Parent: SCRUM-47 / S3-M1.

Status: **M1 review input and executable ports ready for team review; no approval is
recorded here**. Baseline: `origin/main@0745fcd7b3c2c18292c78e9cb4f35674dcdf8ef9`,
which contains M2's freeze candidate
[`S3-M2-day1-contract-diff-freeze-candidate.md`](../evidence/S3-M2-day1-contract-diff-freeze-candidate.md)
([PR #46](https://github.com/hxj04121-lab/FoodLabelFlow/pull/46)).

This subtask adds the impact domain model and application ports only. Change-request
use cases, relevant-product discovery, the Impact Strategy, orchestration, HTTP
endpoints and JDBC adapters are SCRUM-76 to SCRUM-80 and the M2/M4/M5 issues. Flyway
V1–V3 are unchanged.

## M1 answer to the freeze candidate

M2 asked whether the supplier-material lookup and the impact/finding boundary can be
consumed without M2 owning classification, task creation or M1 persistence. **Yes**,
provided the lookup follows `RelevantProductLookupPort` below: M2 returns facts only
(product, current formula, published label pointer and the formula items that use the
material). M1 owns classification, finding construction and the decision to open a
ReviewTask. M5 owns run/finding storage and idempotency. M4 owns the task lifecycle.

## Decisions needed before the freeze

Each item is an M1 proposal. The listed owner must confirm it; this document does not.

| # | Question found in the schema | M1 proposal | Confirm with |
| --- | --- | --- | --- |
| 1 | SCRUM-47 says `target_label_version_id`; V1 has `review_task.draft_label_version_id`, also used by the V2 procedures. | Contract field `draftLabelVersionId`; schema unchanged; Jira wording updated. | M2, M4 |
| 2 | `review_task.assigned_to_user_id` is NOT NULL. | Impact passes an explicit assignee chosen by a rule agreed with M4 (for example, a user holding the review permission). It is never guessed by the adapter. | M4 |
| 3 | `impact_analysis_run.rule_set_version_id` is NOT NULL. | The trigger request carries `ruleSetVersionId` explicitly, as the validation API does, and an inactive rule set is 422. There is no implicit "current" rule set. | M2, M5 |
| 4 | After N+1 adoption the product's current-formula pointer already names N+1, but a finding has both `current_formula_version_id` and `proposed_formula_version_id`. | `currentFormulaVersionId` is the formula behind the published label (N); `proposedFormulaVersionId` is the adopted N+1, or null. They never match. | M2, M5 |
| 5 | `change_request.status` allows DRAFT/SUBMITTED/ANALYZED/COMPLETED/CANCELLED, but no transitions are defined. | Created as SUBMITTED; a completed run moves it to ANALYZED in the same transaction; COMPLETED once every ReviewTask is resolved. | M4, M5 |

Two further facts for M2's golden data: `impact_finding.current_label_version_id` is
NOT NULL, so a relevant product without a published label cannot be stored as a
finding (SCRUM-77 defines the explicit outcome); and the seed has no
`spec_chocolate_v2`, which M1 tests supply as fixtures until M2's adoption lands.

## Domain model (`com.spectrace.impact.domain`)

| Type | Invariants |
| --- | --- |
| `ChangeRequest` + `VersionChange` | `changeType` selects which version pair `versionChange` names, so the V2 `chk_change_request_typed_refs_v3` rule holds by construction; from and to must differ. |
| `ChangeType`, `ChangeRequestStatus`, `ImpactRunStatus`, `ImpactClassification` | Exactly the V2 CHECK tokens (asserted against the migration); unknown database values fail and are never defaulted. |
| `ImpactAnalysisRun` | `completedAt` is present exactly for COMPLETED/FAILED and never precedes `startedAt`. |
| `ImpactFinding` | NO_ACTION has no missing allergen codes and REVIEW_REQUIRED has at least one; codes are copied, sorted and unique; the proposed formula is optional but never the current one. |

## Application ports (`com.spectrace.impact.application.port`)

| Port | Implemented by | Contract |
| --- | --- | --- |
| `ChangeRequestRepository` | M1 (SCRUM-76) | Save/find by ID; the adapter maps `VersionChange` to the typed columns. |
| `ImpactAnalysisRunRepository` | M5 (SCRUM-51) | Save/find by ID; list a change request's runs oldest first, which is the basis for idempotent re-triggering. |
| `ImpactFindingRepository` | M5 (SCRUM-51) | Save a run's findings; read them ordered by product, at most one per run and product. |
| `SpecificationVersionLookupPort` | M1 catalog read (SCRUM-76) | Exact spec version read: material, version number, released flag. Empty means 404. |
| `RelevantProductLookupPort` | M2 (SCRUM-48) | Products whose **current released** formula uses the material, ordered by product; historical formulas excluded; no mapping table; a null label pointer is reported, not dropped. |
| `ReviewTaskPort` | M4/M5 (SCRUM-50/51) | Open one OPEN task per finding in the caller's transaction. `OpenReviewTask` holds the finding and rejects NO_ACTION, so a NO_ACTION task cannot be requested. |
| `ImpactIntegration` | M1 adapter over identity/audit (SCRUM-76); run atomicity verified by M5 (SCRUM-51) | `requireActor(CREATE_CHANGE_REQUEST \| RUN_IMPACT)` using the seeded codes `CHANGE_REQUEST.CREATE` / `IMPACT.RUN`; audit writes are MANDATORY-transaction and throw on failure. |

Reused unchanged from Sprint 2: `catalog.application.port.FormulaCompositionPort`,
`allergen.application.port.AllergenFactsPort` (an empty derivation is a completed
negative result, never "no data") and `label.application.port.LabelSnapshotPort`.
No impact code reads foreign tables.

## Verification

`backend/src/test/java/com/spectrace/impact/support/InMemoryImpactPorts.java` provides
fakes for every new port. Each fake enforces the matching V1/V2 key constraint, so
SCRUM-76 to SCRUM-79 can be unit-tested before the M2/M4/M5 adapters exist.

- `ImpactDomainContractTest` (7) and `ImpactPortContractTest` (7) cover the invariants
  above, the V2 token sets and the V3 permission codes.
- Full backend build on 2026-09-28 with Java 21 and local Colima:
  `mvn -B -ntp -f backend/pom.xml verify` → **315 tests, 0 failures/errors/skips,
  BUILD SUCCESS**, including `ArchitectureTest` and `ValidationArchitectureTest`.
