# SCRUM-41 — validation contract, ports and rule registry

Implementation date: **2026-09-14 (Asia/Shanghai)**. Planned window: September 14–15;
estimate: **3 story points**. Parent: SCRUM-12 / S2-M1.

Status: **implementation ready for owner review; approval is not yet recorded**.
HTTP contract version: **1.0.0**, OpenAPI **3.1.0**. Internal port baseline: **1.0.0**.
The executable baseline inspected was `origin/main@a3887660ff54f5d88bd7bae3812fd53bf12344dc`.
The HTTP endpoints, derivation algorithms, concrete evaluators, snapshot adapters and
validation orchestration remain subsequent implementation work. This subtask supplies
their contracts and the executable registry, without registering placeholder Spring beans.

## Authority and compatibility

- [Canonical HTTP contract](../contracts/allergen-validation-api-v1.yaml).
- [SAD-001/002/003](../architecture/S1-M2-sad.md) and
  [analysis-to-design trace](../architecture/S1-M2-analysis-design.md).
- M2's candidate at `origin/codex/s2-m2-contract-diff`,
  `docs/evidence/S2-M2-contract-diff.md` ([PR #13](https://github.com/hxj04121-lab/FoodLabelFlow/pull/13)).
- M5 domain records/repositories merged on main, and canonical Flyway V1–V3.
- M4's `origin/feature/s2-m4-label-draft-api` candidate and M3's main
  `frontend/src/api/labels.ts` consumer.

PR #13 was observed **MERGED** on September 14 with an empty `reviews` list. Its merge
does not prove approval of these newly introduced Java ports. No team member's review,
Jira Done transition, actual hours or integration acceptance is inferred from this file.

The existing HTTP resource names, routes, request fields, four error fields and status
codes remain at 1.0.0. The severity enum now states the existing database/M5 tokens
explicitly; currentness, rule-set binding and FAILED-versus-422 semantics are clarified.
Any future breaking rename, new rule type/status/error, async lifecycle, schema change
or 503 response requires an explicit version/owner review. V1–V3 are unchanged.

## HTTP boundary

| Operation | Request | Committed success |
| --- | --- | --- |
| `GET /api/v1/allergens` | Required nonempty `jurisdictionCode` query | 200, direct `Allergen[]` |
| `POST /api/v1/label-versions/{labelVersionId}/validation-runs` | Exact label ID; JSON contains only nonempty `ruleSetVersionId` | 201, direct `ValidationRun`, including a completed `FAILED` run |
| `GET /api/v1/validation-runs/{validationRunId}` | Exact persisted run ID | 200, direct `ValidationRun` and results |

`Allergen`: `allergenId`, `allergenCode`, `displayName`, `jurisdictionCode` (strings).
`ValidationRun`: `validationRunId`, `labelVersionId`, `ruleSetVersionId`, `status`
(`PASSED`/`FAILED`), `ranAt` (RFC 3339 UTC), `results`; `summary` is optional and,
when absent in persistence, is omitted from HTTP rather than emitted as JSON null.
`ValidationResult`: optional/nullable `ruleDefinitionId`, `resultCode`, `severity`
(`INFO`/`WARNING`/`ERROR`), boolean `passed` and `blocking`, caller-safe `message`.

M5's `ValidationRun` and `ValidationResult` are persistence domain records, not HTTP
responses. The HTTP adapter must combine run/results, map `Instant` to UTC and omit
`ranByUserId`, `dataProvenanceId`, `validationResultId` and each result's internal
`validationRunId`. It must not serialize a repository record directly as the response.
M4's separate `/api/labels/*` draft API and `LABEL_NOT_FOUND` mapping do not replace
these versioned endpoints or validation's `RESOURCE_NOT_FOUND` code.

| HTTP | Code | Meaning / adapter obligation |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Invalid syntax, missing/blank required request field, or extra request field (including actor IDs) |
| 401 | `AUTHENTICATION_REQUIRED` | Missing, unknown or inactive trusted identity; retain existing identity exception mapping |
| 403 | `AUTHORIZATION_DENIED` | Active actor lacks permission; validation writes require canonical `LABEL.VALIDATE` |
| 404 | `RESOURCE_NOT_FOUND` | Requested label version or validation run is absent |
| 409 | `LABEL_VERSION_NOT_CURRENT` | Existing label snapshot is historical/non-current, or its formula is no longer the current released formula |
| 422 | `VALIDATION_PRECONDITION_FAILED` | Required formula/specification/catalogue inputs are unavailable/incomplete; requested rule set differs from the label, is missing/inactive/ineffective, belongs to another jurisdiction, or has no active definitions |
| 500 | `INTERNAL_ERROR` | Unexpected failure, including missing/duplicate evaluator configuration or failed audit; roll back and expose no implementation detail |

Every HTTP error uses existing `ApiError(code, message, traceId, evidenceId)`.
The identifiers are present and null unless real identifiers exist. Registry/input
constructor exceptions are internal programming/configuration failures; do not add
a broad handler that converts every `IllegalArgumentException` into HTTP 400.
The application must check business preconditions explicitly before constructing the
evaluation context. Read permissions remain M4's existing access-policy boundary;
this subtask adds no new permission codes.

## Cross-module ports and immutable values

Application ports live in the owning module's `application.port` package. No JPA
entities, `Map<String,Object>` rows or persistence adapters cross the boundary.
All lists are non-null, copied defensively and unmodifiable, including nested lists.
String identifiers are nonblank and passed through unchanged. Missing collections
must never silently become empty collections. Structural DTO checks are not a
replacement for application precondition checks or actual rule evaluation.

| Port | Provider → consumer | Signature / contract |
| --- | --- | --- |
| `LabelSnapshotPort` | label / M4 → validation / M1 | `Optional<LabelValidationSnapshot> findById(String labelVersionId)`; absence and non-current presence are distinct |
| `FormulaCompositionPort` | catalog / M1 → allergen/validation / M1 | `Optional<FormulaCompositionSnapshot> findById(String formulaVersionId)`; every formula item and its pinned specification components |
| `AllergenFactsPort` | allergen / M1 → validation and HTTP adapters | `listAllergens(jurisdictionCode)` and `derive(formulaSnapshot, ruleSetVersionId, jurisdictionCode)`; returns canonical catalogue or version-scoped derivation |
| `ValidationIntegration` | validation-side M4 bridge → validation / M1 | `requireActor(permission)` and `auditValidation(actorId, labelVersionId, ruleSetVersionId, validationRunId, dataProvenanceId)` |
| Existing three validation repositories | validation / M5 → validation / M1 | Reuse `RuleSetVersionRepository`, `ValidationRunRepository`, `ValidationResultRepository` unchanged |

The contracts are supplied here for M2 fixture/contract review and M4/M5 integration;
this does not transfer ownership of their implementations or create duplicate domain
records. In particular, validation never calls `LabelDraftRepository` or catalog SQL.
The allergen module may query its own mappings through its own future adapter, but
obtains formula/specification input only through `FormulaCompositionPort`.

| Value | Frozen fields |
| --- | --- |
| `LabelValidationSnapshot` | `labelVersionId`, `productId`, `formulaVersionId`, `ruleSetVersionId`, `jurisdictionCode`, `rawIngredientText`, boolean `isCurrent`, `dataProvenanceId`, `declarations` |
| `LabelValidationSnapshot.AllergenDeclaration` | `allergenId`, `declarationType`, `declarationSource`, nullable `displayText` |
| `FormulaCompositionSnapshot` | `productId`, `formulaVersionId`, boolean `isCurrentReleased`, `items` |
| `FormulaCompositionSnapshot.Item` | `formulaItemId`, `supplierMaterialId`, `specificationVersionId`, `components` |
| `FormulaCompositionSnapshot.Component` | `specComponentId`, `ingredientId`, `rawPhrase`, `matchRule`, `matchStatus` (`MATCHED`/`UNMAPPED`/`AMBIGUOUS`) |
| `AllergenEntry` | The four `Allergen` HTTP fields above |
| `AllergenDerivation` | `formulaVersionId`, `ruleSetVersionId`, `jurisdictionCode`, `facts`, `unresolvedComponents` |
| `AllergenFact` | `allergenId`, `allergenCode`, nonempty `derivationEvidence` |
| `AllergenFact.DerivationEvidence` | `formulaItemId`, `specificationVersionId`, `specComponentId`, `ingredientId`, `ingredientAllergenId`, `evidenceRule`, `dataProvenanceId` |
| `AllergenDerivation.UnresolvedComponent` | `formulaItemId`, `specificationVersionId`, `specComponentId`, `ingredientId`, `rawPhrase`, `matchRule`, `matchStatus` (`UNMAPPED`/`AMBIGUOUS` only) |
| `RuleEvaluationContext` | `label`, M5 `ruleSet`, `allergens`; rejects mixed formula IDs, rule-set IDs or jurisdictions |
| `ValidationFinding` | `ruleDefinitionId` (nullable for input-level findings), `resultCode`, M5 `severity`, boolean `passed`, boolean `blocking`, `message`; no persistence IDs |

Declaration tokens retain V2's `CONTAINS` and sources `MIGRATED_PUBLIC_LABEL`,
`FORMULA_DERIVED`, `SYSTEM_PROPOSED`, `USER_ENTERED`. They remain strings in the
snapshot so evaluators can report invalid input explicitly. Empty raw ingredient text
and empty declarations remain observable input, not constructor success claims.
The component `ingredientId` remains non-null as required by V1 even for unresolved
rows; adapters must preserve `matchStatus` rather than treating the ID as proof of a match.

Formula items/components are ordered by source sequence then ID. Derived facts are
grouped by allergen ID, sorted by allergen code then ID; all contributing evidence
paths are retained in formula/component order. Unresolved rows retain source order.
`DerivationEvidence.dataProvenanceId` identifies the canonical ingredient-allergen
mapping's provenance; formula/specification provenance remains traceable through their
exact version IDs. It must not be relabelled as the validation run's generated provenance.
Catalogue lists use allergen code then ID order. Unknown jurisdiction may yield an
empty catalogue; unavailable storage must raise an error, not return that same empty list.

An empty derived fact list can represent a complete negative result. It does not mean
the formula was absent or all ingredients were resolved: inspect `unresolvedComponents`.
Each unmapped/ambiguous component must produce an explicit failed blocking ERROR
finding in the later evaluator, even if other components derived allergens successfully.
A missing ingredient-to-allergen row for a known, matched ingredient can mean no
allergen association; do not invent a mapping. M2 golden fixtures define specific
rule/input `resultCode` values; the HTTP contract intentionally does not enumerate them.

## Currentness, version binding and transaction boundary

`is_current_published` is a publication pointer. `LabelValidationSnapshot.isCurrent`
is the label owner's decision about the exact version eligible for current validation.
The candidate policy for M4 review is:

| Label state | Current validation candidate |
| --- | --- |
| `DRAFT`, `PENDING_REVIEW`, `APPROVED` | Eligible when it still references the product's current released formula |
| `PUBLISHED` | Eligible only when it is the current published label and references the current released formula |
| `SUPERSEDED`, `REJECTED`, historical published label, or stale formula reference | Non-current; 409 |

There is no canonical global “current draft” pointer and no new “latest draft only”
uniqueness rule. A draft is not rejected merely because `isCurrentPublished == 'N'`.
The label adapter must obtain formula selection through catalog's application port,
not by selecting catalog tables. M4 must confirm this policy before integration.

The requested rule-set ID must equal the snapshot's rule-set ID and the derivation's
rule-set ID. It must be ACTIVE, effective on the operation's UTC date (inclusive
`effectiveFrom`/`effectiveTo`, null end means unbounded), and in the same jurisdiction.
`RuleSetVersionRepository.findActiveById` checks lifecycle only; the future application
must also check dates, jurisdiction and active definitions. Do not silently choose a
different version or reuse a run from another label/rule-set pair.

The application owns one transaction: trusted actor/permission → exact snapshots and
preconditions → registry coverage → all evaluations → run/results → audit → commit.
Providers join it and protect the label/declarations and current formula/rule-set
selection through commit. Subsequent structured label edits must invalidate prior
validation before review submission, even if the label ID did not change. An immutable
Java record alone does not prove database concurrency safety; M4/M5 adapter/integration
work must demonstrate this. No database helper or historical run substitutes for BR-05.

## SAD-003 registry contract

`RuleEvaluator.ruleType()` declares exactly one existing M5 `RuleType`.
`evaluate(RuleDefinition, RuleEvaluationContext)` returns all attributable findings
for one active definition; evaluators are deterministic and have no side effects.
The registry is a pure immutable object, constructed with the actual evaluator list.

- `INGREDIENT_TO_ALLERGEN` and `LABEL_DECLARATION_VALIDATION` are the only current types.
- Duplicate registrations fail construction; null/unknown types fail explicitly.
- `require(type)` fails explicitly if no evaluator is registered. No default evaluator,
  silent skip, first-match chain, reflection discovery or placeholder pass is provided.
- The future orchestrator visits active definitions in `ruleDefinitionId` order
  (matching M5's repository), preflights every active type, then invokes the matching
  evaluator for **every definition**. Inactive definitions are skipped. Multiple
  definitions of one type all use the same strategy; a failed finding does not stop
  later definitions. No active definitions is a precondition failure, not an empty pass.
- `ValidationFinding.forRule` preserves the exact definition ID/severity and makes a
  failed ERROR blocking. The constructor rejects `passed && blocking` and blocking
  INFO/WARNING. An input-level finding may have a null rule ID without inventing one.
- After complete evaluation, any failed blocking ERROR yields `FAILED`; otherwise
  `PASSED`. Persist every finding using M5's records, then synchronously audit. Neither
  registry construction nor these unit tests implement the full orchestration loop.

## Verification and owner review

| Automated evidence | What it establishes |
| --- | --- |
| `OpenApiContractTest` | YAML parsing; exact version/routes/request; canonical error/status mapping; HTTP fields and enums agree with the Java port/M5 values |
| `ValidationPortContractTest` | Absence versus stale snapshot; nested defensive copies; multi-item evidence retention; unresolved inputs; exact version/jurisdiction binding |
| `RuleEvaluatorRegistryTest` | Canonical dispatch; duplicate/missing/null/unknown failure; immutable registration |
| `ValidationFindingTest` | Rule attribution, severity/blocking invariant and nullable input-level rule ID |
| `ValidationBoundaryArchitectureTest` | Port/rule/domain independence from SQL, Spring/web and infrastructure; no foreign repository dependency |
| Existing M5 MySQL tests and full backend verify | Regression compatibility with current domain/repository and application baseline |

Execution results are recorded after verification in
[SCRUM-41 verification](../evidence/SCRUM-41-verification.md).

| Owner review | Concrete review item | State |
| --- | --- | --- |
| M2 | Snapshot/fact/finding fields, unresolved-input fixtures, 1.0.0 HTTP compatibility | Pending recorded acceptance |
| M4 | Currentness table, label adapter locking/invalidation, trusted actor and transactional audit bridge | Pending recorded acceptance |
| M5 | Reuse of domain/repositories, effective-date checks, finding-to-result and HTTP mapping | Pending recorded acceptance |

SCRUM-41's code/docs/basic-test work can be reviewed as this branch. The screenshot's
“contract version and fields reviewed” acceptance criterion remains pending until
actual owner feedback/approval is recorded. Do not mark that criterion or Jira Done
from automated tests alone.
