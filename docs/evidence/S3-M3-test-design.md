# M3 Sprint 3 implementation, verification and assistance — 8 October 2026

Owner: Xu Feiyang / M3. Tasks: SCRUM-49/71–74.
Local integration: main `36f52bf`, PR76 `6d5d897` and owner PR87 `417133e` on XFY.
Current UI/CI completion is locally verified; team review, publication of these
local changes and the combined source's merged-main CI remain pending.
Earlier 8 October receipts below retain their original source scope.

## Safe reconciliation

All nine 7 October local files were copied to a local backup and saved by a
scoped Git stash before integration. The stash is retained. New main's workflow,
API and identity integration was preserved. Valuable earlier behavior was
reimplemented there: complete paging/deduplication, exact URL context,
cancellation/timeout, honest read failures and regressions. The obsolete
read-only page/fixed identity was not restored over the full workflow.
Original evidence remains recoverable; it does not qualify this later source.
The pre-existing deployment document and personal handoff remain outside commits.

## Completion map

9 October review follow-up: M1 accepts the existing impact integration. PR76 now
adds defensive classification validation and captured same-create retries.
Main's PR81 A07 and PR82 failure-trace/persisted-FAIL assertions were integrated;
the corresponding revert PR84/85 were closed without merging. Historical
execution receipts below remain scoped to their original sources.

PR86's identity/revision implementation and PR87's recovery/current-validation
integration have now been reused locally. Their remote PRs remain unmerged.
PR86's exact-head CI failed Sonar;
PR87 `417133e` has successful [PR CI37872081151](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37872081151),
including real identity switching and existing live flows. Its workflow still
did not run `scrum84-returned-revision-live.spec.ts`, and its original acceptance
document recorded the full correction path as NOT RUN. The current local change
preserves PR76's URL/timeouts, combines actor-generation guards, fixes stored
Publisher selection on reload and cancellation during response-body reads, and
runs the expanded correction browser path in a fresh owned stack. CI now has a
separate disposable revision stack and API bootstrap. Do not mark tasks Done
from predecessor checks or local receipts alone.

| Task | Actual implementation | Remaining acceptance |
| --- | --- | --- |
| 71 | Actual impact integration, defensive classification, captured retries, URL/reload/back and actor isolation | Review/merge and matching main CI |
| 72 | Exact task/replacement/validation context, URL/reload/back/timeout retained with identity guards; legacy target handoff | Review/merge and matching main CI |
| 73 | Controlled Maker/Checker/Publisher, immutable corrected version, fresh validation, independent approval/publication and history verified locally | M4/team acceptance of the integrated demo choice and matching main CI |
| 74 | Updated M3 A07, full correction browser/API/DB proof, zero-write denial checks and executable CI bootstrap | User A07 assessment and combined merged-main CI/artifacts |

## Verified merged-main predecessor

Main `0dc1737dab1cf5e9152c279197de44b57d306ae4`:
[workflow 37741058672](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37741058672).
Five jobs passed; actual backend log: **618 tests, zero failures/errors/skips**.
Containers executed real validation PASS/blocking FAIL and the full SOY browser
scenario with twenty independent approvals/publications. Browser artifact
`validation-browser-evidence` (11533294183) is bound to that SHA. This is
predecessor evidence, not a pass for later uncommitted changes.

## Latest merged-main supplement

During final checks, PR72 merged as `0b3447d116aa0d47cb5fb4c1967c0e6c319102fa`.
Its production frontend/backend sources are identical to the earlier baseline;
the changes add tests and evidence. XFY integrated it without overwriting any
local repair. [Main workflow 37765740477](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37765740477)
passed all five jobs: **619 backend tests, zero failures/errors/skips**, and
actual catalog, validation, full S3 compound-maker/publication and formula live
opt-ins passed. The new permitted-creator HTTP/database/browser negative is
documented in [the compound-maker supplement](S3-compound-maker-negative-20261008.md).
The updated product-flow UI suite was separately run locally: **7 passed**.
PR73 subsequently merged the documentation-only
[SCRUM-68 staging ADR](../architecture/ADR-SCRUM-68-staging-ci-local-compose.md)
as `73600b8`. It selects CI Compose qualification plus controlled local Compose;
the exact merge/source and verification links still need recording in SCRUM-68.
No shared deployment or new runtime qualification is claimed. Its production
sources are unchanged. M4's login/switch proposal remains PROPOSED; the staging
ADR explicitly does not adopt a caller-switch/login decision.

## Current checks and evidence scope

From frontend, with Node 22.12+ and Playwright Chromium installed:

```sh
npm run build
npx playwright test tests/s3-state-regressions.spec.ts tests/impact-workflow-ui.spec.ts tests/review-workspace-ui.spec.ts
npx playwright test --grep-invert 'captures.*live'
```

| Source | Checks | Scope |
| --- | --- | --- |
| s3-state-regressions.spec.ts | URL/reload/back, GET-only restoration, no repeated POST, mismatch/404, overlapping paging, failed refresh, late task success/error, timeout/retry, creator-permitted UI feedback | Explicit HTTP fixtures; no stored grants changed |
| impact-workflow-ui.spec.ts | Exact nullable/bound task handoff, same-key uncertain replay, refreshed specs | HTTP fixtures |
| review-workspace-ui.spec.ts | Bounded pages, task mismatch, unavailable actor and permissions | HTTP fixtures |
| s3-product-flow-ui.spec.ts | Canonical first declarations, commands and uncertainty | HTTP fixtures |
| validation-live.spec.ts | Actual evaluator PASS/blocking FAIL and exact persisted reads | Real backend/browser |
| s3-product-flow-live.spec.ts | 40 API adoptions/findings, 20 replacements/validations/independent publications, CLOSED tasks and immutable history | Real backend/browser |

## Historical 8 October Docker qualification

Final project `m3-s3-qualified-20261008` uses a fresh database, existing dev-auth
setting and unchanged seeded grants. Loopback ports: frontend 15195, backend
18095, MySQL 13321. Only released specification input was imported from the existing
CI fixture, stopping before INSERT INTO formula_version. No adoption, label,
declaration, PASSED run, task or publication output was seeded.

Run sequentially, validation before SOY adoption:

```powershell
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:15195'
$env:LIVE_VALIDATION = '1'
npx playwright test tests/validation-live.spec.ts --output=test-results/final-validation
$env:LIVE_S3_FLOW = '1'
$env:S3_COMPOUND_MAKER_USER_ID = 'user_test_compound_maker_s3'
$env:S3_COMPOUND_MAKER_SUBJECT = 'dev-external-test-compound-maker-s3'
$env:S3_COMPOUND_MAKER_ALIAS_SUBJECT = 'DEV-EXTERNAL-TEST-COMPOUND-MAKER-S3'
npx playwright test tests/s3-product-flow-live.spec.ts --output=test-results/final-s3
```

The local full-path receipt below used the predecessor's actor arrangement.
For the current PR72 live suite, a fresh disposable database also needs its
merged `s3-compound-maker.sql` test fixture and three environment values:
`S3_COMPOUND_MAKER_USER_ID=user_test_compound_maker_s3`,
`S3_COMPOUND_MAKER_SUBJECT=dev-external-test-compound-maker-s3`, and
`S3_COMPOUND_MAKER_ALIAS_SUBJECT=DEV-EXTERNAL-TEST-COMPOUND-MAKER-S3`.
Only install that fixture in an owned disposable test database. The main
workflow shows the guarded input procedure. Test actor contexts do not implement
an in-product switch or record a human approval. Each full rerun needs a fresh
scenario database because it intentionally changes current versions.

## Required negative paths

| Requirement | Actual available evidence | Remaining assistance |
| --- | --- | --- |
| Self-review | Current PR72 main CI proves permitted-creator HTTP/database/browser rejection; local UI fixtures also exercise the independent guard | M4 confirms the actor arrangement for the actual controlled demo; no duplicate negative implementation is requested |
| Missing permission | Actual caller/permission read, disabled UI and live 403 with no decision | M4 confirms demo identity/permission arrangement |
| Missing/failed validation | Real FAIL scenario, disabled submission before exact PASS and backend submission gate | Retain exact label/rule-set/latest-run assertions |

REQUEST_CHANGES returns the same immutable declaration snapshot. Do not invent
editing or silently rebind a second draft. M4/M2 must agree any required
correction flow. The [identity proposal](../architecture/S3-demo-identity-and-staging-proposals.md)
still needs M4's adoption. The separate merged staging ADR above supplies the
CI/local environment choice and requires its Jira decision record.

## Four teammate follow-ups after local integration

| Member | Concrete assistance | Deliverable |
| --- | --- | --- |
| M1 — Huang Xiangjia | Existing impact semantics and demo inputs have been reviewed in PR76; no replacement backend work is requested | Retain the recorded review and golden links |
| M2 — Cai Runchen | Review reuse of PR87 and its current-run/revision contract; final head includes no additional backend edits | Normal integration review and exact contract/evidence links |
| M4 — Zhu Wenyu | Review the combined controlled switch/revision UI, including the Publisher reload fix, under the authored demo decision | Normal team acceptance of the integrated demo choice; no duplicate login implementation |
| M5 — shj040128shj | Retain final merged-main CI/artifacts and verify the existing staging/Jira record and demo reset procedure | Exact final-main evidence and source-bound local-demo receipt |

M3 retains its consumer review, UI fixes/tests, A07 and demo steps. These requests
are prepared for the user; no teammate messages or Jira changes are made here.

## Sprint Review steps

1. Initialize only released specification input in the agreed isolated environment.
2. Perform supported adoption/change/analysis; compare 20 NO_ACTION, 20 REVIEW_REQUIRED
   and 20 excluded controls with the M2 oracle.
3. Open a required task, capture explicit canonical declarations in its first
   replacement, show rule-level validation and the submission gate.
4. Use M4-agreed checker and publisher contexts; show separate commands, CLOSED
   task, old/new labels and reload persistence.
5. Show permission, validation and correctly scoped self-review negatives,
   preserved history and any unresolved decision/correction boundary.

## Historical 8 October execution receipt

Verified on 8 October against the integrated local working tree, with no backend
source changes from main `0dc1737`:

- Docker Node 22 production TypeScript/Vite build: passed; existing bundle-size
  advisory retained, no dependency/CI/security setting changed.
- Final focused state/workspace suite after independent-review hardening:
  **21 passed**, including late manual-read success/error and old create/run
  completions after a browser-history context change.
- Final complete fixture suite: **105 passed, 3 opt-in skipped**, zero failures. The
  full SOY test is excluded from that fixture run and executed separately below.
- Actual validation browser: **1 passed**, exercising PASS and blocking FAIL.
- Final actual full SOY browser: **1 passed** (1.5 minutes), 40 API adoptions, 40
  findings (20 NO_ACTION/20 REVIEW_REQUIRED), 20 independent publications,
  20 CLOSED tasks and 60 immutable-history checks. Only specification input
  was seeded; declaration and validation/publication outputs used real APIs.
- Extra real read-only browser check: manual task A/B selection, URL/reload/back,
  saved analysis reload/back and desktop/mobile layout all passed, with no
  business POSTs and no page-wide overflow. Captures show actual existing
  seeded request identities, not an in-product switch or human approval.
- Diff and relative documentation links passed. Raw JSON/screenshots and source
  fingerprints are retained outside Git in the local reconciliation evidence
  directory; the older read implementation remains in its backup and stash.

The earlier 101-test/first live run remains a predecessor receipt. An intentional
turn interruption stopped the dev server and Docker engine; the resulting
connection-refused attempts were environment failures, not passing test evidence.
Both services were restored. Final results above use the final repaired source
and a newly initialized qualification database, not a replay over published data.

These results qualified the 8 October source. A new merged-main CI receipt,
owner decisions, exact consumer acceptance and A07 assessment remain separate.
No task was marked Done and no teammate was messaged by that verification.

## PR76 comment-fix execution receipt — 9 October

Verified on XFY after integrating documentation/test-only main `36f52bf` and
applying M1's feedback, before the follow-up commit:

- Node 24 production TypeScript/Vite build: passed; existing 609 kB bundle
  advisory remains. No dependency or build-policy change.
- Focused impact/task/state suites: **28 passed**, including seven new cases
  for contradictory classifications on POST/GET, valid NO_ACTION and captured
  create retries with successful or conflicting responses.
- Complete fixture suite with `--grep-invert 'captures.*live'`:
  **113 passed, 3 explicit live opt-ins skipped**, no failures (3.4 minutes).
- Desktop 1440px and emulated mobile 390px retry/conflict states were captured
  and inspected; no page-wide overflow. These use HTTP fixtures, not a physical
  mobile device or new live-backend workflow qualification.
- Documentation links and diff checks passed.

No new live business execution is claimed for these comment fixes. Keep the
earlier Docker receipts separately scoped. New PR/main checks are required for
the committed follow-up. The identity/revision integration and complete new
browser correction path remain outside PR76 until owner changes are integrated.

## Current integrated completion receipt — 9 October

Source: XFY `6d5d897` plus owner PR87 `417133e`, followed by the M3 integration
fixes recorded above. Backend production/test sources and dependencies remain
identical to that owner head. No V1–V10 migration was rewritten. Source SHA-256
fingerprints and the pre-integration head are retained outside Git.

- Docker production backend/frontend builds passed. Actual runtime is Java 21,
  Node 22-built frontend, MySQL 8.4.11; Flyway V1–V10 all installed successfully.
  The existing 622 kB bundle advisory remains.
- Local backend `clean verify`: **665 tests, 95 fresh XML suites, zero failures,
  errors or skips**, exit 0. Java 25 compiled release 21. A task-only Maven Central
  settings file and local Docker API option avoided unrelated host configuration;
  project/user settings were not changed. Raw XML and logs are retained.
- Final complete frontend fixture suite: **126 passed, 5 explicit live opt-ins
  skipped**, zero failures (4.7 minutes). Final state/identity subset: **21 passed**.
  It includes cancellation while the successful response body is still streaming,
  old-actor isolation, preserved task URL and Publisher reload/permission checks.
  A final two-line guard prevents impact/task pages claiming empty API results
  before identity verification succeeds. After that change and two new regressions,
  the related impact/task/state/identity/revision suites passed **46/46** (1.3m)
  and the final TypeScript/Vite build passed. The 126 full-suite receipt precedes
  only those empty-message guards; backend and revision workflow sources did not
  change after their qualification. No second full-suite result is invented.
- Real expanded returned-revision browser/API/MySQL path: **1 passed** (13.3s)
  against fresh project `m3-revision-accept-20261009`, loopback MySQL 13324,
  backend 18098, frontend 15198. Bootstrap imported only released specification
  input; 40 adoptions, the change, 40 findings, initial replacement, PASS and
  submission used real APIs. Actual findings matched the 20/20 golden partition.
- The browser uses Checker → Maker → Checker → Publisher, edits Soy declaration
  text, creates the new immutable label on the same task, verifies no inherited
  validation, runs fresh PASS and independently approves/publishes. Exact DB
  approval/publication/current-validation records and product pointer agree.
  Old declaration, validation, approval/audit snapshots and original published
  content remain intact. Reload retains Publisher and the published exact ID.
- Missing validation and old-version submission return 409; Maker's missing
  approval permission returns 403; label validation/decision/publication/audit
  evidence is unchanged by those denied commands. Permitted-creator policy
  coverage remains separately exercised by the existing backend/S3 suite.
- Workflow YAML parses with the new separate-stack bootstrap/browser/cleanup
  stages. Relative documentation links and diff checks pass.

Local raw evidence is in `review-artifacts/m3-completion-20261009` outside the
repository. Earlier failed fixture/reload attempts and the interrupted dev-server
run are not counted as passes; final qualification used a new disposable DB.
The older passing no-edit revision run is separate from the final edited-text
receipt. The API seed/bootstrap and read-only DB assertions are test tooling,
never application data fallbacks or fabricated human approval.

The remote PR76 `6d5d897` [CI37878457974](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37878457974)
passed; it qualifies only the earlier remote source. The current local merge
still requires publication, normal team review, and actual combined **main** CI
including the new live stage/artifacts. Tasks 71–74 remain open until their
corresponding acceptance is recorded. No Jira status was changed.
