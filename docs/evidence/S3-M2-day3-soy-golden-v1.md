# S3 M2 Day 3 — SOY golden outcomes and product lookup

## Candidate scenario

This versioned golden describes the proposed change “Chocolate Base Spec V2 adds Soy Lecithin.” The current database has released `spec_chocolate_v1` for supplier material `mat_chocolate_base`; it does not have a released Chocolate Base Spec V2. The golden therefore records the change as a proposed scenario and does not invent a Spec V2 identifier or claim that it has been released.

The candidate component is the existing canonical ingredient `ing_soy_lecithin`, whose allergen relation maps to `SOY`. The baseline chocolate specification does not already contain that component.

## M1 product lookup contract

For a changed supplier material, start from each product’s `current_formula_version_id`, join the matching `formula_version` for that product, and accept only `lifecycle_status = RELEASED` and `is_current_released = Y`. Join `formula_item` through that exact formula version and select rows whose `supplier_material_id` is the changed material. Retain the row’s `specification_version_id` and verify it belongs to the same supplier material. This yields products from actual formula history and uses no auxiliary product-to-material mapping.

For `mat_chocolate_base`, this lookup returns the 40 products listed under `NO_ACTION` and `REVIEW_REQUIRED` in [the executable golden CSV](../../backend/src/test/resources/golden/s3-m2-soy-spec-v2-impact-v1.csv). The other 20 products listed under `EXCLUDED_NO_FINDING` have current released formulas but no FormulaItem referencing the changed material.

## Expected outcomes

The 20 `NO_ACTION` products have an existing SOY-bearing supplier material in their current formulas and their current published labels already declare SOY. The 20 `REVIEW_REQUIRED` products reference Chocolate Base but have neither a SOY-bearing item in their current formula nor SOY in the published declaration; adding Soy Lecithin to Chocolate Base therefore introduces the missing `SOY` declaration. The excluded controls do not reference Chocolate Base and produce no finding for this change.

This is an M2 data oracle. It does not implement impact classification or ReviewTask behavior owned by M1.

## Executable verification

`S3SoyGoldenRelationshipMySqlTest` reads the same versioned JSON as the consumer and checks the current product/formula/formula-item/specification/material/label/allergen relationships against the MySQL schema loaded by Flyway. It compares product-ID sets, verifies current released and published pointers, checks the SOY source and declaration for each outcome, and confirms that negative controls lack the changed supplier material.

Preflight on 2026-10-02: `origin/main` was `e53f7b0f0fb7c9e0038dbbbe4193f953edab8e35`, its CI run `36822786343` was green, and no pull requests were open. The Day 3 scheduled attempt at 09:25 exited before Jira access because the Windows task has no `JIRA_*` environment credentials; the authenticated Jira connector transitioned SCRUM-54 to In Progress before this implementation work.
