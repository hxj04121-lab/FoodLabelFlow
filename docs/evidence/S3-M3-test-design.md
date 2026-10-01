# S3 M3 test design — SCRUM-71 / SCRUM-74

Status: design draft; S3 live acceptance is pending. Owner: Xu Feiyang / M3.
Baseline reviewed: `0745fcd` (main, 2026-09-29).

## Scope and current delivery

- SCRUM-71: `/impact` now has specification-change context, a disabled change
  selector/action, an unavailable-results region and outcome explanations. The
  materials link opens the existing catalog workflow.
- The foundation makes no impact API calls, does not read preview findings and
  does not treat unavailable data as an empty successful analysis.
- SCRUM-74: this document defines test cases, dependencies and evidence gates.
  It does not claim that the S3 end-to-end tests are implemented or passing.
- SCRUM-72/73 remain future work. The review route is still an Upcoming page.

The merged [M2 candidate](S3-M2-day1-contract-diff-freeze-candidate.md) is not a
frozen OpenAPI contract. No S3 endpoint, wire DTO, error code or new workflow
status is invented by this change. UI state names below describe presentation,
not backend enum values.

## Runnable foundation checks

From `frontend/`, with the repository dependencies and Playwright Chromium
installed, use Node 22.12+ (matching or exceeding the Vite runtime requirement):

```sh
npm run build
npx playwright test tests/impact-foundation.spec.ts tests/dashboard.spec.ts tests/english-ui.spec.ts
```

`impact-foundation.spec.ts` covers:

1. Direct load and reload keep the unavailable state, disable selection and
   analysis, send no `/api/` requests and render no finding rows or result enums.
   The outcome glossary is explicitly separate from the results region.
2. Keyboard activation of the materials link reaches the existing catalog page.
3. Sidebar navigation works at 1440px and 390px, closes the mobile navigation,
   and introduces no document-wide horizontal overflow.

The existing `catalog-fixture.ts` supplies explicit HTTP fixtures only for the
materials navigation check and collects coverage when `VITE_COVERAGE=true`.
These checks do not establish backend availability or S3 acceptance. Screenshot
outputs are under ignored `frontend/test-results/`.

### Local validation record — 29 Sep 2026

- Node 24.19.0: production TypeScript/Vite build passed. Vite reports the
  existing application bundle above its 500 kB advisory threshold.
- The command above passed all 10 selected Playwright checks (three new impact
  checks plus seven existing dashboard/English-UI regressions).
- Desktop 1440px and mobile 390px captures were inspected; no page-wide overflow
  or material visual issues were found within this foundation's scope.
- `git diff --check` and local Markdown-link checks passed.
- These are local results before user approval to push. No new PR/main CI run,
  live S3 integration, Sonar gate or backend acceptance is claimed.

## Integration prerequisites and owners

| Required input | Owner | Consumer / release gate |
| --- | --- | --- |
| Versioned OpenAPI for change, run, finding, task, submission, decision and publication; error-code matrix and response examples | M2, reviewed by M1/M3/M4/M5 | API clients and response mapping; SCRUM-71/72/73 |
| Create/read ChangeRequest, trigger/read impact runs and findings; task creation for REVIEW_REQUIRED | M1 | Real impact and finding-to-task flow |
| Spec V2 adoption into released FormulaVersion N+1 and current supplier-material lookup; SOY golden expectations including negative controls | M2 | Data preparation and independent result oracle |
| Task queries and replacement-draft creation/binding boundary, agreed with M1; submission/decision/publication operations | M4 with M1/M2 | Exact task-to-label navigation and review flow |
| Login decision, actor/permission read mechanism, distinct maker/checker and restricted test identities | M4 | Permission-aware actions and negative tests |
| Durable impact/task data, idempotency, audit, rollback, reproducible Compose environment and full-path containers CI | M5 with module owners | Reload assertions and main CI evidence |

Before wiring clients, resolve the task target's nullable state, resource lookup
and list behavior, run completion/failure representation, version-conflict
tokens, retry semantics, and whether approval publishes immediately or requires
a separate command. A disabled local button is never evidence of a backend
permission check.

## Test data and oracle

- Use an isolated local/CI course database; prepare Specification V1/V2,
  ChangeRequest and adopted FormulaVersion N+1 through owner-supported APIs or
  an agreed test setup. Do not write ad hoc SQL into browser tests.
- Keep exact IDs for the change, run, finding, task, old/new formula, old/new
  label, rule set, jurisdiction and validation run. Derive generated IDs from
  actual responses; never replace them with whichever version is current later.
- M2 supplies the expected per-product outcome map and excluded controls for
  Chocolate Base V2 + Soy Lecithin. Compare product-ID sets and outcomes, not
  only counts; do not use displayed UI text as the expected classification.
- Maker A creates the replacement label, Checker B approves it (B differs from
  the label creator), and Restricted C lacks the relevant permission. M4 must
  provide supported identities and an exact permission map. Switching the
  visible preview avatar does not authenticate anyone.
- Isolate/reinitialize scenarios according to the agreed M5 setup. Repeat a
  single change deliberately only for the idempotency case. Preserve older
  formula/label versions for history assertions.

## Planned UI and HTTP-fixture cases

Implement these after their contracts are agreed. HTTP fixtures test frontend
handling; they must be labeled separately from real integration evidence.

| ID | Trigger / setup | Observable assertions | Task |
| --- | --- | --- | --- |
| UI-01 | Load the change list, select a change, start analysis | Loading is visible; selection context identifies material and before/after specs; a pending write cannot be submitted twice | 71 |
| UI-02 | Load a run with NO_ACTION and REVIEW_REQUIRED findings | Product/run/finding IDs remain associated; no task link for NO_ACTION; REVIEW_REQUIRED opens the task returned for that finding | 71/72 |
| UI-03 | Successful run with no relevant products | Explicit empty result appears only after a successful response; no fabricated rows, task or classification | 71 |
| UI-04 | Read fails, times out or returns invalid JSON/shape | Visible failure and safe read retry; no seed fallback; stale previous findings are not presented as the new run | 71/72 |
| UI-05 | Change/run/task A responds after the user selects B | B remains selected; A cannot overwrite data, error or busy state for B; test both late success and late failure | 71/72 |
| UI-06 | Task target is null, then becomes bound to a draft | Explain missing draft; disable target-dependent actions; after binding, read that exact label and verify context | 72 |
| UI-07 | Validation result has a different label/rule-set ID | Show a mismatch error; never display it as this replacement label's successful validation | 72 |
| UI-08 | Switch actor while a read or write is pending | Clear identity-sensitive data/actions; ignore old actor responses; reread authoritative state before allowing a new action | 73 |
| UI-09 | Command fails with agreed 401/403/409/422 code | Correct recovery message; no success toast, local publication or new task resolution | 73 |
| UI-10 | Command response is lost or 5xx leaves outcome uncertain | No blind write retry; reread server state or use the frozen idempotency/reconciliation contract | 71/73 |
| UI-11 | Long product names/IDs; keyboard; 390px viewport | Context remains readable, labels/focus work, controls fit and no page-wide horizontal scroll | 71–73 |

## Planned real-API acceptance cases

| ID | Scenario | Required assertions / evidence |
| --- | --- | --- |
| E2E-01 | V2 released → explicit formula adoption → change analysis → findings → task → replacement validation → independent approval → publication | Use the frozen trigger order; compare findings to M2's golden map; excluded controls have no findings; NO_ACTION has no task; task binds the expected replacement; exact-version validation is shown; B approves A's label; reread old SUPERSEDED and new PUBLISHED labels, current pointer and resolved task after reload. Adoption must not happen as a side effect of publication. |
| E2E-02 | A attempts to approve A's own label | UI clearly explains rejection/unavailability; an actual backend attempt made through the supported authenticated test client is rejected with the agreed code; task/labels/current pointer stay unchanged. |
| E2E-03 | Restricted C attempts a protected operation | UI disables/hides it as specified; supported direct request is rejected by the backend; no success feedback or persisted transition. |
| E2E-04 | Latest validation for the same label/rule set has blocking ERROR, or required validation is absent | Submission is unavailable/rejected; direct backend attempt also fails; no review submission or publication. A passing run for another version cannot satisfy the gate. |
| E2E-05 | B requests changes or rejects instead of approving | Show the server-returned state and next permitted actions; preserve after reload; do not publish or resolve the task as if approval succeeded. Exact post-decision states come from M4. |
| E2E-06 | Another actor changes the current version before submission/decision/publication | Stale action is rejected; show conflict and reread context; never silently retarget to the newer version. |
| E2E-07 | Repeat analysis trigger for the same change / repeat an uncertain command | Observe the frozen idempotency behavior; no duplicate run/findings/tasks. Backend transaction-level verification belongs to M1/M5. |

For negative cases, browser assertions prove user feedback, and authenticated
API attempts prove server enforcement. Test-side negative requests do not create
a UI bypass. Rollback and concurrent-publication internals remain M4/M5 tests;
M3 checks externally visible results without recreating backend business rules.

## CI and evidence gate

Current `.github/workflows/ci.yml` builds the frontend, runs fixture browser
tests in the Sonar job, and runs `validation-live.spec.ts` against Compose in the
containers job. The new foundation spec fits the existing fixture suite. This
change does not alter the CI workflow or claim new live coverage.

After integration, coordinate with M5 to add a real change-to-publication spec
to the containers job using `PLAYWRIGHT_BASE_URL`, with an explicit local/CI
write opt-in following the existing live-test convention. No route interception
or seed fallback may replace business API responses in that acceptance run.

Record for each acceptance run:

- Contract revision, merged main SHA, workflow URL/run ID and exact test names.
- Fixture version and actor roles; resource IDs needed to reproduce the flow.
- Browser trace/screenshots showing findings, target validation, independent
  approval, publication and the three mandatory negative paths.
- Persisted API rereads, including after browser refresh; no credentials in
  artifacts. Screenshots alone do not prove durable state.
- A07 updated to match the implementation and Sprint Review steps/limitations.

| Gate | Current status |
| --- | --- |
| SCRUM-71 foundation | Implemented locally; see runnable checks above |
| Frozen S3 contract and real impact integration | Pending M2/M1 and supporting M2/M5 work |
| SCRUM-72/73 integrated | Pending |
| Full-path and mandatory negative tests passing on merged main | Pending; no S3 run ID claimed |
| A07 final implementation alignment | Draft only |

Target checkpoints from SCRUM-49: first integration review on 2 Oct, full main
CI path by 8 Oct, Sprint Review on 9 Oct. Neither SCRUM-71 nor SCRUM-74 is Done
on the strength of this foundation.
