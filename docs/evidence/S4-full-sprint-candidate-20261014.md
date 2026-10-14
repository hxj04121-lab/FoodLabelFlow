# Sprint 4 M1–M5 implementation candidate and A07 index

Date: 14 October 2026. Repository: `hxj04121-lab/FoodLabelFlow`.
Branch: `codex/s4-full-demo-hardening-20261014`, based on
`80297ae89d825bd937002fecb912ee4cfc9378d8`.

This report maps the full Sprint 4 scope to the implementation candidate and
available evidence. The candidate is ready for PR review. The full PR CI and
enabled security/quality gates have recorded passing results below. This is not
a Sprint acceptance statement: running the S4 reset script itself and an
independent member rehearsal still need recorded results.

## Sprint scope and evidence

| Work item | Candidate implementation | Evidence and present status |
| --- | --- | --- |
| SCRUM-85 · M1 adoption and impact read model | `POST /api/catalog/specifications/{id}/formula-adoptions` selects products using the target material and adopts one locked current formula per product in an independent transaction. A unique `(product_id,target_specification_version_id)` marker makes committed results replayable. The response reports adopted, skipped and failed items. Every impact finding carries the current catalog `productDisplayName`; the page shows names, aggregate totals, an outcome filter and the exact review-task handoff. | Service tests cover replay, current-formula selection, missing permission and rollback; a Testcontainers test covers the 40-product V3 golden, a formula switched after discovery, a deliberately failing product and the retry. The MySQL/Testcontainers backend suite passes in PR CI; this could not run locally because Docker Engine is unavailable. Golden live analysis also passes in the isolated CI stack. |
| SCRUM-86 · M2 contract freeze and A07 consolidation | Added the batch-adoption OpenAPI contract; updated the adoption API contract, impact API to 1.1.0 with the product display-name field, label-product-flow OpenAPI to 1.3.0, and the error matrix. Contract alignment tests cover routes, response fields, identity options and permissions. | The current contract candidate describes the new demo endpoints; producer/consumer acceptance and merge to `main` remain pending PR review. The linked A07 materials and Sprint 1–4 pattern decisions are indexed below. |
| SCRUM-87 · M3 demo UX, script and deck | Impact filters and names, the batch adoption entry point and review-task link are in the UI. A fresh task-bound label draft preselects only `USER_ENTERED` declarations from that task's exact current published label; the maker can edit the selection before creation. Added a six-step demo runbook and a seven-slide deck. | The latest S4 demo UI file passes all 8 browser tests, covering filter/name/task handoff, Data Admin adoption and denial, editable declaration prefill, and authorized/denied/empty/error-recovery label history. The earlier full local Playwright run passed 134 tests; five live-only tests skipped without a configured backend. Deck passed the 7-slide geometry/heading/bullet validator with Aptos explicitly approved; no native PowerPoint application was available for an additional open/save inspection. **A second member has not rehearsed the runbook.** |
| SCRUM-88 · M4 identities, label history and security | Added Change Manager and Data Admin to the local demo identity options, with `AUDIT.READ` assigned to their existing roles. Added the permission-checked, read-only product label-history endpoint/page with version declarations, reviewer decisions, publication identity/time/channel and attributable label/task audit events. | Identity and history HTTP/service tests assert the permission sets, database mapping, safe errors, and denial before unauthorized reads. Batch adoption tests deny a non-admin before candidate reads/transactions. The history query is read-only. Development header identity remains an explicitly local/demo seam, not production authentication; details below. The latest PR CI Trivy and OWASP Dependency-Check jobs both pass. |
| SCRUM-89 · M5 reset, regression and release evidence | Added `scripts/s4-demo-reset.ps1` with a fixed Compose project name, project-scoped volume reset and loopback-only host-port bindings. Added the operator runbook and this candidate-to-evidence index. | The full PR CI run [38524627355](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/38524627355) passes backend, frontend, containers, SonarQube/SonarCloud and security. Its isolated Compose stack completed the live twenty-publication SOY flow, preserved all sixty product histories and passed formula-lifecycle and returned-revision browser regressions. The PowerShell reset script was parsed/config-checked but not itself invoked; a second-member runbook rehearsal remains outstanding. |

## A07 use cases, diagrams and pattern decisions

The following links preserve the member use cases, analysis/design diagrams and
design-problem records in one Sprint 4 index. The S4 extension is described in
the work-item table above; original dated evidence remains linked as history.

| Member | Use case, class/sequence diagrams and design problem |
| --- | --- |
| M1 | [Run Change Impact Analysis A07](S3-M1-A07-run-change-impact-analysis.md); [Impact Strategy design problem](S3-M1-A07-impact-strategy-draft.md); [S3 impact strategy decision](../s3-m1/SCRUM-78-impact-strategy.md). |
| M2 | [S3 M2 A07 index](S3-M2-day6-a07-evidence.md) links the [adoption SAD](../architecture/S3-M2-adoption-sad.md), [analysis class](../architecture/diagrams/s3-m2-adoption-analysis-class.mmd), [analysis sequence](../architecture/diagrams/s3-m2-adoption-analysis-sequence.mmd), [design class](../architecture/diagrams/s3-m2-adoption-design-class.mmd), [design sequence](../architecture/diagrams/s3-m2-adoption-design-sequence.mmd) and [adoption pattern decision](../architecture/S3-M2-adoption-pattern-decision.md). Earlier [S1 analysis/design](../architecture/S1-M2-analysis-design.md) records the evaluator Strategy registry. |
| M3 | [Impact, review and publication A07](S3-M3-A07-draft.md), with the [consumer review](S3-M3-contract-consumer-review-2026-09-30.md); S4 demo steps are in the [runbook](S4-demo-runbook-20261014.md) and [presentation](presentations/S4-demo-flow-20261014.pptx). |
| M4 | [SCRUM-84 state and sequence diagrams](../architecture/SCRUM84-A07-state-and-sequences.md) and the [login/demo-switch ADR](../architecture/ADR-SCRUM84-login-demo-switch.md) record the State/Transition Policy boundary. S4 history/auth behavior and permission cases are covered by the linked endpoint implementation and tests above. |
| M5 | [SCRUM-70 atomic persistence A07](../s3-m5/SCRUM-70-A07-atomic-persistence.md) records the transaction/failure design problem; the S4 isolated reset and release checklist are in the [demo runbook](S4-demo-runbook-20261014.md). |

## Security review notes

- The Change Manager/Data Admin header identities use the existing
  `DEV_EXTERNAL` resolver. `ExternalActorResolver` rejects that provider when
  development auth is disabled and when the runtime/profile is production.
  Compose explicitly enables it only for the development demo. The reset script
  binds database/API/web ports to `127.0.0.1`; do not expose this stack to an
  untrusted network or treat its header as real authentication.
- Batch adoption requires both `DATA.MAINTAIN` and `FORMULA.RELEASE`; the server
  checks them before discovering products. Each product's formula, audit rows
  and replay marker share one transaction. The history endpoint requires
  `AUDIT.READ`, has no write operations, and returns only one selected product.
- The UI permission checks are usability controls; the server remains the
  authority. Negative tests verify server-side denial before history SQL or
  batch candidate reads. Maker/Checker separation remains enforced by the
  existing workflow service.
- `npm audit` reports six moderate dependency paths from one advisory
  (`GHSA-hp3w-g68c-fv3c`) through the development/test instrumentation chain
  `vite-plugin-istanbul → nyc → js-yaml → argparse → sprintf-js@1.0.3`. The
  vulnerable formatter is not in the production dependency tree;
  `npm audit --omit=dev` reports zero vulnerabilities and the high-severity
  threshold reports no high/critical findings. Accepted for this candidate as
  a development-tooling-only risk; `npm audit fix --force` proposes a breaking
  Istanbul plugin change, so it was not applied without coverage-tool migration
  review.
- Trivy filesystem/container scan and OWASP Dependency-Check were not available
  locally (`trivy`/`dependency-check` are not installed and Docker Engine is not
  running). The latest enabled PR CI ran Trivy and OWASP Dependency-Check
  successfully, and SonarCloud/SonarQube quality gates passed. Repository CI
  runs security and Sonar checks only when their respective repository flags
  are enabled. Record any future findings/remediations here before release
  acceptance.

## Candidate checks

| Check | Result |
| --- | --- |
| Backend targeted contract, identity, batch, history HTTP/service and impact tests | **PASS — 65 tests, 0 failures/errors/skips**, JDK 25, offline Maven. Full PR backend/Testcontainers job also passes in [run 38524627355](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/38524627355). |
| Frontend TypeScript/Vite production build | **PASS.** Vite reports the existing 632.6 kB minified entry chunk advisory. |
| Playwright local UI checks | **PASS — full suite 134 passed, 5 live-only tests skipped; latest S4 demo UI file 8/8 passed.** Local fixtures are not live-stack evidence. |
| MySQL Testcontainers backend suite | **PASS in PR CI**, including batch-adoption and migration integration tests. Docker Engine is unavailable locally. |
| `docker compose config --quiet` and reset-script PowerShell parse | **PASS.** This validates Compose/PowerShell configuration only; it does not start containers. |
| Docker Compose fresh live SOY/revision stories | **PASS in PR CI**: twenty actual independent publications and sixty immutable histories, followed by formula-lifecycle and returned-revision browser regressions. |
| `scripts/s4-demo-reset.ps1` execution and independent member rehearsal | **Not run.** The script syntax/configuration passes; local Docker Engine is unavailable and another member has not rehearsed the runbook. |
| `git diff --check` and evidence Markdown relative links | **PASS.** |
| Full PR CI / SonarCloud / SonarQube / Trivy / Dependency-Check | **PASS** on candidate SHA `aba479ebcf2174663dd8eb519bb2b762b37d9f67` in [run 38524627355](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/38524627355). |

## Release-candidate checklist

1. Run the reset script with Docker Engine available and record its health result
   plus the exact candidate SHA.
2. On the fresh database, create and release the V2 input through the UI; run
   adoption for 40 candidates, then confirm 40 relevant findings, 20
   `NO_ACTION`, 20 `REVIEW_REQUIRED` and 20 negative controls; complete Maker → Checker →
   Publisher; confirm prior/current labels and audit history.
3. Have another member follow only the runbook and record their acceptance
   against that SHA.
4. Link the SHA to completed CI, browser artifacts, Sonar/security scans and
   this report; record findings and decisions before calling S4 accepted.
