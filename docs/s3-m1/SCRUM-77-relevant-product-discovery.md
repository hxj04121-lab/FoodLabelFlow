# SCRUM-77 — relevant-product discovery via the current FormulaVersion

Parent: SCRUM-47 / S3-M1.3. Estimate: **3 story points**. Baseline: `origin/main@e53f7b0`.

## What a relevant product is

For a changed supplier material, a product is relevant exactly when its **current released**
FormulaVersion has at least one FormulaItem whose `supplier_material_id` is that material.
"Current released" means all of:

- `product.current_formula_version_id` names the version, and the version belongs to the product;
- `formula_version.lifecycle_status = 'RELEASED'`;
- `formula_version.is_current_released = 'Y'`.

Superseded releases (`RELEASED` + `N`), drafts and retired versions never match, even when they
still contain the material. There is no product-to-material mapping table; `fixture_group` is
never read.

## Ownership

| Piece | Module | Notes |
| --- | --- | --- |
| `RelevantProductLookupPort` | catalog (M2, SCRUM-48) | Unchanged. |
| `JdbcRelevantProductLookupAdapter` | catalog | Implements the lookup M2 documented in its SOY golden ([PR #56](https://github.com/hxj04121-lab/FoodLabelFlow/pull/56)). Added here because no adapter existed and M1 needs it to meet the done criteria; same precedent as `JdbcSpecificationVersionLookupAdapter` in SCRUM-76. **Needs M2 review.** |
| `RelevantProductDiscovery`, `RelevantProductTarget` | impact (M1) | Applies the no-label rule below and hands SCRUM-78/79 a target that always has a label. |

The adapter groups every matching item of one product (ordered by `sequence_no`), so a product
appears once even if the material is used twice. A FormulaItem whose specification belongs to a
different supplier material is corrupt data: the read fails, and the product is not silently
dropped.

## Rule: relevant product without a current published label

`impact_finding.current_label_version_id` is NOT NULL, so such a product cannot be stored as a
finding. Following the frozen error matrix
([`s3-impact-api-error-matrix-v1.md`](../contracts/s3-impact-api-error-matrix-v1.md)):

- The lookup **reports** the product with `currentPublishedLabelVersionId = null`. It does not
  drop it. A pointer to a label that is not `PUBLISHED` + `is_current_published = 'Y'` also
  counts as no published label.
- Discovery then fails the whole run with **422 `PUBLISHED_LABEL_MISSING`** before any write.
  The message lists the affected product IDs in order.
- There is no partial run, and the product is never skipped. Skipping would make a NO_ACTION
  summary look complete when it is not.

A material that no current formula uses gives an empty list. That is a completed negative
result, not missing data.

## Verification

- `RelevantProductDiscoveryTest` (6, unit): ordering, empty result, the 422 rule with every
  unlabelled product named, a duplicate product from the port treated as a server fault, and the
  invariants of the target record.
- `RelevantProductDiscoveryMySqlTest` (6, Flyway V3 seed on MySQL 8.4.11, each test rolled back):
  - `mat_chocolate_base` returns exactly the 40 `NO_ACTION_BASELINE_SOY` +
    `REVIEW_REQUIRED_BASELINE_NO_SOY` products, with their current formula, published label
    and chocolate items;
  - none of the 20 `NEGATIVE_CONTROL_NO_CHOCOLATE` products are returned, even after superseded,
    draft and retired formula versions that use chocolate are added to one of them;
  - a relevant product whose new current formula dropped the material stops being relevant
    (39 left);
  - a null or superseded label pointer is reported by the lookup and makes discovery return 422
    with no impact rows written;
  - two chocolate items in one formula are both returned;
  - an unused material returns an empty list.
- `mvn -B -ntp -f backend/pom.xml verify` (JDK 25 targeting 21, Colima), 2026-10-03:
  **411 tests, 0 failures/errors/skips, BUILD SUCCESS**.
