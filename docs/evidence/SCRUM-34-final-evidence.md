# SCRUM-34 — S2-M5.8 CI, Sonar/staging and evidence visibility

Jira: SCRUM-34
Scope: S2-M5.8
Observed: 2026-09-24 (Asia/Singapore)

## Verification binding

- Verified code SHA: `870e9fe6fe05d61c5b9ce40e05cc7aaff712e010`.
- Base SHA after rebase: `f2b31db39f16b55bbb69fc5a522b06ebdc6848c5` (`origin/main`), including merged PRs #33 and #36.
- PR: https://github.com/hxj04121-lab/FoodLabelFlow/pull/34
- Final CI Run for the rebased code: https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568
- The CI Run completed successfully for the verified code SHA; the follow-up evidence-only commit is not presented as a different code verification target.

## Dependency status

- SCRUM-29 / M5.6: READY — its merge commit `8aecc731a91929607ad8451412120fb85274ead0` is an ancestor of the verified main snapshot.
- SCRUM-33 / M5.5: SUPERSEDED ON MAIN — the attempted `493ff2f` API commit was not replayed because main already contains the later SCRUM-45 API integration (`5b6b172`); this avoids duplicate controllers and DTOs.
- SCRUM-35 / M5.7: IMPLEMENTED / MERGED — PR #36 (`test(architecture): guard validation module boundaries`) merged as `bb7b153dd14d423894cf806038c5a7a5dc8b8ca5`; its head commit was `9692f44f3e40b72b0d99d79d14db1cc7272d1b40` and is an ancestor of the verified main snapshot. Jira currently remains `正在进行`; the Jira activity records implementation complete and the status was not changed.

## Existing CI and changes

The workflow runs backend, frontend, containers, security, and conditional
SonarQube analysis jobs. The backend job runs the canonical
`mvn -B -ntp -f backend/pom.xml verify` command; the frontend job runs
`npm ci && npm run build`; the containers job validates Compose, builds the
images, and runs the live browser validation. None uses `continue-on-error: true`.

SCRUM-34 adds an `always()` `backend-test-reports` artifact upload covering
Surefire and Failsafe report directories, while ignoring absent directories
without hiding the verify exit status.

## Backend

Remote CI result from the verified code SHA:

- PASS — `Tests run: 227, Failures: 0, Errors: 0, Skipped: 0`.
- PASS — `BUILD SUCCESS`.
- The backend job executed the MySQL Testcontainers verification path and uploaded `backend-test-reports`.

The suite includes validation domain/API/golden/persistence tests, MySQL
integration, OpenAPI and shared API error contracts, identity/workflow tests,
and the current ArchUnit boundary suites.

## Frontend and browser evidence

- `npm ci` and `npm run build`: PASS in the final CI Run.
- Validation-run UI: PRESENT on main after PR #33; the previous `NOT_APPLICABLE` statement is obsolete.
- Live Playwright browser validation: PASS in the containers job for both the positive and blocking-negative paths.
- Browser evidence artifact: [validation-browser-evidence](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568/artifacts/10796814981).
- Artifact files: `validation-live-pass.png` shows a completed `PASSED` run with four attributable results; `validation-live-fail.png` shows a completed `FAILED` run with blocking `ALLERGEN_DECLARATION_MISSING` results.

## Containers and local/shared staging

- `docker compose config --quiet`: PASS in the final CI Run.
- `docker compose build`: PASS in the final CI Run.
- Full-stack container smoke plus live browser validation: PASS in the containers job.
- Shared staging: NOT_CONFIGURED
  - Owner: deployment/staging owner.
  - Evidence: no shared deployment URL, credentials, or deployment workflow was available; local Compose and GitHub Actions containers are not shared staging.
  - Next step: provide the shared deployment target and run the validation smoke against the deployed verified code SHA.

## Sonar

- SonarQube analysis job: PASS — https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568/job/107554741353
- SonarCloud Code Analysis: PASS — https://sonarcloud.io/dashboard?id=hxj04121-lab_FoodLabelFlow&pullRequest=34

## Remote CI and artifacts

- Final CI Run: https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568
- Backend job: PASS — https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568/job/107554008476
- Frontend job: PASS — https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568/job/107554008633
- Security job: PASS — https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568/job/107554008669
- Containers/browser job: PASS — https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568/job/107554741200
- Backend report artifact: [backend-test-reports](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/35975225568/artifacts/10797133988)

## Persistence / scope checks

- JDBC decision record: `docs/architecture/JDBC_VS_JPA_DECISION.md`.
- JPA dependency added: NO.
- Hibernate added: NO.
- V1-V3 modified: NO.
- New migration: NO.
- Unrelated persistence refactor: NO.

## Final status

SCRUM-34 evidence is updated against the rebased code SHA and the completed
final CI Run. Remote CI, Sonar, containers, and browser evidence are verified;
the validation UI is present and SCRUM-35 is implemented/merged. Shared staging
remains explicitly `NOT_CONFIGURED`. PR #34 remains OPEN and has not been
merged in this evidence-update pass.
