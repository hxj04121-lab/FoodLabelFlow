# SCRUM-78 — Impact Strategy and NO_ACTION / REVIEW_REQUIRED classification

Parent: SCRUM-47 / S3-M1.4. Estimate: **3 story points**. Builds on SCRUM-77
([PR #57](https://github.com/hxj04121-lab/FoodLabelFlow/pull/57)). Design rationale:
[A07 Impact Strategy draft](../evidence/S3-M1-A07-impact-strategy-draft.md).

## What was added (`com.spectrace.impact.application.strategy`)

| Type | Role |
| --- | --- |
| `ImpactStrategy` | One classifier per `ChangeType`. It reads through ports and writes nothing. |
| `ImpactStrategyRegistry` | Immutable `EnumMap` dispatch. A duplicate strategy, or a missing *required* type, fails construction. Spring requires `INGREDIENT_SPEC`, so a wiring mistake stops startup. |
| `IngredientSpecImpactStrategy` | The only registered strategy. `FORMULA` and `RULE_SET` are extension points with no placeholder beans; `require` throws for them. |
| `ProductImpactAssessment` | The verdict before the run assigns IDs. Its classification is computed from `missingAllergenCodes`; `toFinding(...)` builds the `ImpactFinding`. |

## INGREDIENT_SPEC rules

For each relevant product from SCRUM-77, with change `specV1 → specV2` and the run's
explicit `ruleSetVersionId`:

1. Read the **published label** by its exact ID. Its formula is N. After adoption, the label
   snapshot's `isCurrent` is false by design, so it is not checked.
2. Read the product's **current released formula**. Every item that made the product
   relevant must now pin `specV2`. If one does not, the run gets **422
   `FORMULA_ADOPTION_PENDING`**.
3. If the published label already sits on that same formula, there is no N+1 to propose:
   **409 `DATA_CONFLICT`**.
4. Derive allergens from N+1 with the Sprint 2 `AllergenFactsPort`, using the run's rule set
   and the label's jurisdiction.
5. `missing` = the codes of derived allergens whose `allergenId` has no CONTAINS
   declaration on the label, sorted. If `missing` is empty, the product is **NO_ACTION**.
   Otherwise it is **REVIEW_REQUIRED** with those codes. Each finding has a short
   explanation that names N+1, the target spec and the label.
6. **An empty derivation is not automatically "no allergens".** It counts as a basis for
   NO_ACTION only when every N+1 item has components and every component is `MATCHED`.
   - If the derivation is incomplete and something is still missing, the product is
     REVIEW_REQUIRED, and the explanation says the inputs need review.
   - If the derivation is incomplete and nothing is missing, the strategy fails and the run
     rolls back. NO_ACTION would be unproven, and REVIEW_REQUIRED needs a missing code.

`currentFormulaVersionId` is N (behind the label) and `proposedFormulaVersionId` is N+1, as
agreed in SCRUM-75 decision #4.

## Decision for reviewers

The frozen v1 error matrix has no code for "the derivation is incomplete". Today that case
is a 500 with a full rollback. If M2 wants it to be a caller-visible 422 (for example
`ALLERGEN_DERIVATION_INCOMPLETE`), that is a contract revision. The V3 seed has no
UNMAPPED/AMBIGUOUS components, so the SOY scenario never reaches this case.

## Verification

- `ImpactStrategyRegistryTest` (6) and `IngredientSpecImpactStrategyTest` (14): unit tests.
- `IngredientSpecImpactStrategyMySqlTest` (3): real catalog, label and allergen adapters on
  MySQL 8.4.11. Spec V2 (Chocolate Base plus Soy Lecithin) and the N+1 adoption are rolled-back
  fixtures until M2's adoption lands. The result matches M2's golden CSV exactly: 20
  NO_ACTION, 20 REVIEW_REQUIRED `[SOY]`, and the 20 negative controls absent. Before
  adoption, every relevant product is `FORMULA_ADOPTION_PENDING`, and no rows are written.
