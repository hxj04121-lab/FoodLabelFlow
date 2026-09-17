# SCRUM-43 — review regression evidence

Review follow-up on 2026-09-17 for [PR #27](https://github.com/hxj04121-lab/FoodLabelFlow/pull/27#issuecomment-5707038481),
also required by the dependent [PR #30](https://github.com/hxj04121-lab/FoodLabelFlow/pull/30).
The branch incorporates `origin/main@85b34c3`, including M2's negative fixtures and
M5's exact active-rule-set lookup.

## Corrections

- A non-current formula snapshot returns `409 LABEL_VERSION_NOT_CURRENT`, even
  when the label snapshot reports current and the formula is also incomplete.
  Current but incomplete/mismatched composition remains a 422 precondition failure.
- `INGREDIENT_TO_ALLERGEN` supports the explicit null-target `UNMAPPED` and
  `AMBIGUOUS` patterns used by M2. Each matching component produces the exact
  attributed `INGREDIENT_UNMAPPED`/`INGREDIENT_AMBIGUOUS` finding and message.
  Other null-target patterns still fail as invalid configuration. A status rule
  with no matching components reports an explicit successful `*_ABSENT` finding.
- Both targeted rule strategies use one declaration policy: only a `CONTAINS`
  declaration for the exact derived allergen satisfies the rule. Missing required
  declarations use `ALLERGEN_DECLARATION_MISSING`; successful targeted findings
  match M2's allergen-specific result codes and messages. Generic declaration-shape
  rules retain their shape checks and use the same missing-declaration code.

The orchestrator still evaluates every active definition. Rules for non-derived
allergens emit attributable successful `*_NOT_DERIVED` findings. Every unresolved
component also retains its input-level blocking ERROR, independently of rule
coverage/severity; the rule-specific finding does not replace that safety guard.
No HTTP, migration, fixture truth, persistence contract or Jira state is changed.

## Verification

`RuleEvaluatorTest` and `ValidationOrchestratorTest` cover stale/incomplete formula
precedence, wrong-allergen/MAY_CONTAIN declarations, invalid null-target rules,
multiple unresolved components, absent statuses, and severity/attribution.

`PositiveGoldenFixtureMySqlTest` and `NegativeGoldenFixtureMySqlTest` now execute
the real registry/orchestrator using owner ports and exact SQL-loaded definitions,
on separate MySQL 8.4.11 containers with Flyway V1–V2. All four positive and four
negative fixtures are exercised. Actual attributable findings are compared with
the golden records, including messages and flags; input guards and non-applicable
rules are checked separately. The retired exact rule-set remains a precondition
failure, and the orchestration slice writes no run/results/audit.

- Focused evaluator/orchestrator/golden suite: **25 tests passed**.
- Full `mvn -B -ntp -f backend/pom.xml verify` on Java 21: **139 tests passed,
  zero failures/errors/skips**, executable JAR built successfully.

The shared formula-lifecycle fixture cleanup from SCRUM-44 is also included so
the full suite restores its original current-formula selection after the HTTP
lifecycle test. Automated checks are evidence for review, not human approval.
