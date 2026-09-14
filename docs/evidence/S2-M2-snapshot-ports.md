# S2 M2 Day 2 — snapshot DTO and application-port boundary

Evidence date: 2026-09-14 (Asia/Shanghai)
Baseline: `origin/main` at `10a976d` (PR #13 merged)
Jira child: `SCRUM-22` — Day 2 — Snapshot DTOs and application ports

## Boundary delivered

The M2-owned application contract is implemented under
`com.spectrace.validation.application.contract`:

- `LabelValidationSnapshot` is an immutable, version-pinned label input with
  formula items, pinned specification components, jurisdiction, and structured
  declarations.
- `FormulaItem` and `FormulaComponent` keep the formula → specification →
  ingredient/component chain explicit. `UNMAPPED` and `AMBIGUOUS` components keep
  their evidence and are not silently removed or replaced.
- `AllergenFact` carries canonical allergen identity, explicit `CONTAINS` versus
  `MAY_CONTAIN` presence, source ingredient ids, and derivation evidence.
- `ValidationFinding` reuses M5's existing `ValidationSeverity` and mirrors the
  current application/HTTP result fields: nullable `ruleDefinitionId`,
  `resultCode`, `severity`, `passed`, `blocking`, and `message`.
- `LabelSnapshotLookup` makes `FOUND`, `NOT_FOUND`, and `NOT_CURRENT` explicit so
  the future HTTP adapter can retain 404 versus 409 semantics without parsing
  messages.

The new application ports are deliberately adapter-free:

| Port | Owning adapter boundary | Contract |
| --- | --- | --- |
| `LabelSnapshotPort` | label module | load the exact current snapshot by label-version id |
| `AllergenFactsPort` | allergen module | derive immutable facts from the supplied snapshot |
| `AuthorizationPort` | identity/RBAC module | authorize the `VALIDATE_LABEL` permission |
| Existing validation repository ports | M5 persistence boundary | remain the existing `ValidationRun`, `ValidationResult`, and `RuleSetVersion` ports |

No M1 catalog repository, M4 identity adapter, M5 JDBC adapter, SQL, migration,
HTTP endpoint, or frontend code is changed. The OpenAPI contract and the shared
four-field `ApiError` remain unchanged.

## Focused evidence

- `SnapshotPortContractTest` verifies immutable list ownership, formula/component
  flow, explicit unresolved-component states, distinct declaration/fact presence
  semantics, finding shape, and application-port signatures.
- `ValidationBoundaryArchitectureTest` verifies validation application code does
  not depend on any infrastructure adapter or any foreign module package. This is
  a focused boundary assertion, not M5's full architecture/integration harness.
- The contracts are pure Java records/interfaces; no persistence fallback is
  introduced.

## Review and compatibility note

PR #13 was re-read live before this work: it is merged and its backend,
frontend, security, and containers checks passed. Its M1/M4/M5 review requests
were not treated as approvals. Day 2 adds only application contracts and ports;
the M1/M4/M5 owners remain responsible for adapters and behavior. The eventual
contract freeze still requires real cross-module acceptance.
