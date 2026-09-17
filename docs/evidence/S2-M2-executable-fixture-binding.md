# S2-M2 Day 5 — Executable fixture binding

This Day-5 slice binds the existing M2 positive fixtures and the Day-4 negative
fixtures to one executable MySQL test. The test consumes the owner application
ports (`FormulaCompositionPort`, `LabelSnapshotPort`, `AllergenFactsPort`, and
`RuleSetVersionRepository`) and reuses M5's `MySqlIntegrationTestSupport`.

## Scope

- Positive SOY, MILK, WHEAT, and multi-item fixtures are loaded from the existing
  SCRUM-23 SQL resource and checked through the real owner adapters.
- Missing declaration, no active rule set, unmapped ingredient, and ambiguous
  ingredient are loaded from the SCRUM-24 SQL resource and checked through the
  same ports.
- The exact requested rule-set ID is preserved. The retired-rule-set case must
  remain unavailable through `findActiveById`; an active alternative is not a
  substitute.
- Missing fixture lookups remain empty, and no validation run/result rows are
  created by fixture loading. The test contains no seed import, upsert, migration,
  or duplicate M5 harness.

## Dependency and execution evidence

Day 4 / SCRUM-24 PR #26 is merged into `main`. The Day-5 PR is based on `main`
and its diff contains only this binding test and this evidence note; it does not
copy or modify the Day-4 fixture files.

The test uses the repository's canonical MySQL Testcontainers pattern in its own
isolated container. The M5 shared JVM container persists across full-suite
contexts and can already contain V3 seed rows; isolating this fixture test keeps
the `@Sql` resources deterministic without changing shared M5 infrastructure or
duplicating its harness. Flyway is limited to V1–V2. Local Docker availability is
environment-dependent; the authoritative MySQL execution must be observed in the
Day-5 GitHub Actions backend job.
