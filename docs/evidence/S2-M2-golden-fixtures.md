# S2 M2 Day 3 — positive golden fixtures

Evidence date: 2026-09-15 (Asia/Shanghai)

Baseline: `origin/main@8aecc731a91929607ad8451412120fb85274ead0`

Jira parent/child: `SCRUM-13` / `SCRUM-23`

## Fixture contract

`PositiveGoldenFixtures` is the typed, immutable test builder. Its companion
`backend/src/test/resources/fixtures/s2-m2-positive-golden-fixtures.sql` contains
the same identifiers and complete relational inputs. The SQL runs after Flyway
V1–V2 in an isolated MySQL 8.4 Testcontainer, so V3 seed data is neither loaded
nor used as a fallback. It is a test resource, not a migration, repository
adapter, rule evaluator, validation orchestrator, or persistence implementation.

The SQL explicitly supplies provenance, actor, supplier, ingredients, materials,
released specifications, exact matched components, allergens, active RuleSet and
definitions, ingredient-to-allergen mappings, products, released formulas,
formula items, current draft label snapshots, and structured declarations.

## Stable fixture IDs and expected outputs

| Fixture ID | Exact source components (formula order) | Expected facts (fact order) | Structured declaration | Expected finding codes | Result |
| --- | --- | --- | --- | --- | --- |
| `S2M2-POS-SOY-001` | `ing_s2_m2_soy_lecithin` / `component_s2_m2_soy_v1` / `Soy lecithin` | `SOY` / `CONTAINS` | `Contains: Soy` | `SOY_DECLARATION_PRESENT` | `PASSED` |
| `S2M2-POS-MILK-001` | `ing_s2_m2_milk_powder` / `component_s2_m2_milk_v1` / `Whole milk powder` | `MILK` / `CONTAINS` | `Contains: Milk` | `MILK_DECLARATION_PRESENT` | `PASSED` |
| `S2M2-POS-WHEAT-001` | `ing_s2_m2_wheat_flour` / `component_s2_m2_wheat_v1` / `Enriched wheat flour` | `WHEAT` / `CONTAINS` | `Contains: Wheat` | `WHEAT_DECLARATION_PRESENT` | `PASSED` |
| `S2M2-POS-MULTI-001` | wheat item `01`, milk item `02`, soy item `03` | `MILK`, `SOY`, `WHEAT`, each `CONTAINS` | `Contains: Milk, Soy, Wheat` | milk, soy, wheat `*_DECLARATION_PRESENT` | `PASSED` |

Every expected fact retains the exact formula item, specification version,
component, ingredient, ingredient-allergen mapping, evidence rule, and provenance
ID. Every expected finding is `INFO`, `passed=true`, and `blocking=false`.
The expected derivation has no unresolved component.

## Declaration semantics

The typed fixture contract keeps `CONTAINS` and `MAY_CONTAIN` as distinct enum
values. The current approved V2 database constraint permits only `CONTAINS`, and
the current owner-side label snapshot retains declaration type as a string for
explicit evaluator handling. Therefore this positive SQL pack contains only
truthful `CONTAINS` rows; it does not weaken the constraint or fabricate a
`MAY_CONTAIN` persistence mapping. A future reviewed schema/contract change can
add separate `MAY_CONTAIN` cases without changing these IDs.

## Focused drift checks

- `PositiveGoldenFixtureContractTest` pins the four fixture IDs, source/component
  order, allergen fact order, declaration text/type, PASS finding codes, immutable
  builder output, and distinct declaration enum values.
- `PositiveGoldenFixtureMySqlTest` loads the SQL in a dedicated container with
  `spring.flyway.target=2`, proves only V1–V2 are present, and compares the current
  label, formula, and allergen owner-port values directly with the typed golden
  expectations.
- No V1–V3 migration, production adapter, validation persistence type, `ApiError`,
  HTTP surface, frontend file, or adjacent-day negative fixture is changed.

## Verification

Verification commands and the final commit/PR/CI evidence are recorded after the
focused suite and the one Day-3 PR complete.
