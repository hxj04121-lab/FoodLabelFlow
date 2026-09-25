# SCRUM-18: derived allergen display integration

Implementation base: main `e7a4285` (PRs #38, #41 and #42). Work continues on `XFY`.

The Labels page now reads the authenticated, label-bound derived-allergen API
after a draft is created or loaded. The client validates the nested response and
matches label, formula, rule-set and jurisdiction to the selected draft before
displaying facts. Switching versions/products remounts the read panel and aborts
the previous request; late responses cannot replace the current selection.

The panel shows allergen IDs/codes, expandable derivation evidence, and explicit
UNMAPPED/AMBIGUOUS component details. Empty facts never imply an allergen-free
label. Historical/version-bound display facts are distinguished from persisted
validation results. Loading, timeout, connection, 401/404/422/500, invalid contract
and mismatched binding states are visible and support explicit read retry.
Canonical-allergen catalogue reads now send the same existing identity headers.

The same page independently reads M4's exact-version structured declaration
resource. Its response is checked against the selected draft on all four
bindings, so both M1 and M4 data must match the same label, formula, rule set
and jurisdiction before display. Declarations and derived facts remain separate;
neither is presented as a validation verdict. The UI accepts nullable/empty
display text, identifies each declaration's legal source, shows a truthful
empty state, and makes 401/404/500, malformed responses, binding mismatch,
timeout and connection failures visible with explicit read retry. Unique panel
keys and request cleanup prevent old data surviving a version/product switch.

Verification on 2026-09-25:

- `npm run build`: passed (local Node 20.15 emitted a Vite engine warning; CI uses Node 22).
- The combined declarations and derived-allergen browser suite passed 31 tests.
- Full non-live frontend regression: 64 passed, 3 live scenarios skipped by opt-in flags.
- Mobile screenshot reviewed; no horizontal overflow. A regression also verifies
  that each version-bound panel appears exactly once after a draft switch.
- The existing `validation-live.spec.ts` now asserts real SOY/WHEAT facts,
  evidence and declarations before its PASS/FAIL validation flow. This updated
  live scenario must be verified by the PR containers job; Docker Desktop's
  Linux engine was unavailable for a local isolated Compose run.

SCRUM-18 acceptance remains subject to the PR's full browser, containers and
Sonar checks, code review and merge. Jira Done is a separate owner decision.
