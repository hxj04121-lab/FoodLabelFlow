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

Day 5 is a stacked PR on Day-4 PR #26 because SCRUM-24's fixture artifacts are
not yet merged into `main`. The Day-5 PR diff contains only this binding test and
this evidence note; it does not copy or modify Day-4 fixture files.

The test uses `MySqlIntegrationTestSupport`, the canonical shared Testcontainers
runtime already present in the repository, with Flyway limited to V1–V2. Local
Docker availability is environment-dependent; the authoritative MySQL execution
must be observed in the Day-5 GitHub Actions backend job.
