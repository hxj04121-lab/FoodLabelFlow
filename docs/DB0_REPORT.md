# DB0 runtime report

## Result

`SpecTrace_PORTABLE_DATABASE_PACKAGE_v3` was executed against a real MySQL
8.4.11 container on 2026-09-03. The package-provided PowerShell self-review
returned `overall_status: PASS`.

Evidence: `.stage0/evidence/DB0_RUNTIME_VALIDATION.json`.

## Verified facts

- Flyway candidate order is V1 schema, V2 constraints/indexes, V3 baseline seed.
- Baseline contains 60 products.
- Baseline change/impact/review tables are empty before scenario inputs.
- Schema contract, data integrity, and scenario recompute validations passed.
- Scenario recompute produced 40 relevant products split into 20 `NO_ACTION`
  and 20 `REVIEW_REQUIRED`, with 20 negative controls excluded.
- All five workflow guardrail checks passed: self-approval, draft
  publication, submit-without-validation, failed-validation submission, and
  duplicate-current-published-label blocking.

## Integration decision

The exact supplied V1/V2/V3 files were copied into
`backend/src/main/resources/db/migration` without schema invention or v2
fallback. The application baseline must use Flyway against these migrations.
