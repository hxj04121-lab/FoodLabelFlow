# S2 M2 negative golden fixtures

Scope: SCRUM-13 / SCRUM-24, logical Day 4. The fixture registry is
`NegativeGoldenFixtures`; its SQL twin is
`/fixtures/s2-m2-negative-golden-fixtures.sql`. Both use exact versioned IDs and
owner application-port values, so M1 rule/orchestrator tests and M5 integration/API
tests can consume the same truth without copying an evaluator or persistence layer.

## Deterministic fixture matrix

| Fixture | Exact fact / unresolved context | Declaration context | Expected outcome | Blocking | Rule or error code | HTTP / shared error |
| --- | --- | --- | --- | --- | --- | --- |
| `S2M2-NEG-MISSING-DECL-001` | One `AllergenFact(all_s2_m2_neg_soy, SOY)` with relational evidence from `item_s2_m2_neg_missing_decl_v1` → `component_s2_m2_neg_soy_v1` → `ia_s2_m2_neg_soy_active_v1`; no unresolved rows | Empty declarations | completed `FAILED` (`passed=false`) | `true`, `ERROR` | `rule_s2_m2_neg_soy_decl` / `ALLERGEN_DECLARATION_MISSING` | `201`; no `ApiError` because evaluation completed |
| `S2M2-NEG-NO-ACTIVE-RULESET-001` | One otherwise-valid SOY fact and no unresolved rows, bound to exact retired `ruleset_s2_m2_negative_retired_v1` | One exact `CONTAINS` / `FORMULA_DERIVED` / `Contains: Soy` declaration | precondition FAIL; no run status or findings | `true` at the expected outcome | `VALIDATION_PRECONDITION_FAILED` | `422`; `ApiError(code, message, traceId, evidenceId)` = `("VALIDATION_PRECONDITION_FAILED", "The current label has no active rule-set available for evaluation.", null, null)` |
| `S2M2-NEG-UNMAPPED-001` | No facts; one unresolved `UNMAPPED` component for `Mystery protein blend` | Empty declarations | completed `FAILED` (`passed=false`) | `true`, `ERROR` | `rule_s2_m2_neg_unmapped` / `INGREDIENT_UNMAPPED` | `201`; no `ApiError` because evaluation completed |
| `S2M2-NEG-AMBIGUOUS-001` | No facts; one unresolved `AMBIGUOUS` component for `Natural flavor concentrate` | Empty declarations | completed `FAILED` (`passed=false`) | `true`, `ERROR` | `rule_s2_m2_neg_ambiguous` / `INGREDIENT_AMBIGUOUS` | `201`; no `ApiError` because evaluation completed |

`201` is intentional: the frozen HTTP contract returns a created validation run with
status `FAILED` when evaluation completes with a blocking error. `422` is reserved for
a business precondition that prevents evaluation.

## Reuse and fallback guard

- Java consumers use each fixture's exact `FormulaCompositionSnapshot`,
  `LabelValidationSnapshot`, `AllergenDerivation`, `ValidationFinding` list and
  `ExpectedOutcome`; no module repository or evaluator is duplicated.
- SQL consumers apply the resource after exactly Flyway V1–V2 in an isolated MySQL
  schema. The script uses ordinary `INSERT` statements, so missing prerequisites or
  duplicate fixture state fail loudly; it contains no migration, upsert, seed import,
  validation output, or default mapping.
- The inactive fixture deliberately coexists with an active same-jurisdiction rule set.
  Active-only lookup of the exact retired ID must stay empty; selecting the other active
  ID would violate the fixture and the version-binding contract.
- Unmapped and ambiguous components remain in `unresolvedComponents` with zero facts.
  Empty facts therefore cannot be interpreted as a successful complete derivation.

## Focused verification

The Day-4 gate is exercised by:

```text
mvn -B -ntp -f backend/pom.xml \
  -Dtest=NegativeGoldenFixtureContractTest,NegativeGoldenFixtureMySqlTest,PositiveGoldenFixtureContractTest,PositiveGoldenFixtureMySqlTest,SharedApiErrorContractTest \
  test
```

The positive fixture tests are included as a regression guard: adding blocking truth
must not weaken or relabel the existing Day-3 PASS fixtures.
