# Sprint 3 M2 Day 2 — impact and handoff contract candidate

**Observed:** 2026-09-28, Asia/Shanghai
**Owner:** Cai Runchen / M2
**Jira:** SCRUM-48 / SCRUM-53
**State:** executable candidate; cross-module acceptance and contract freeze remain pending.

## Live baseline and ownership

- Re-fetched `origin/main` at `0745fcd7b3c2c18292c78e9cb4f35674dcdf8ef9`. The Day 2 branch is based on this commit.
- Live Jira reads show SCRUM-48 and SCRUM-53 assigned to RunChen Cai and In Progress. Jira progress comment [10090](https://hxj04121.atlassian.net/browse/SCRUM-53?focusedCommentId=10090) records the candidate, review requests, CI limitations, and current blocker.
- Day 1 PR [#46](https://github.com/hxj04121-lab/FoodLabelFlow/pull/46) is merged, but GitHub reports no submitted reviews. M1 PR [#47](https://github.com/hxj04121-lab/FoodLabelFlow/pull/47) is open; its description gives concrete proposed fields and still-open integration decisions, not a submitted review or acceptance of PR #48.
- Main CI run [36366295460](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36366295460) failed in OWASP Dependency-Check after Maven Central returned HTTP 429. Backend, frontend, containers and SonarQube jobs passed on that run.
- Scope in this PR is limited to M2-owned contract/schema artifacts and their contract test. No M1 impact engine, M3 UI, M4 workflow/publication behavior, or M5 persistence was implemented.

## Candidate artifacts

- `docs/contracts/s3-impact-review-publication-api-v1.yaml`: OpenAPI 3.1.0, version 1.0.0, explicitly marked `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`. It defines the change-request create/read and impact-analysis trigger/query boundaries, outcome variants, ReviewTask linkage, and publication handoff identifiers.
- `docs/contracts/s3-impact-api-error-matrix-v1.md`: negative-path meanings mapped to the exact Sprint 2 four-field `ApiError` schema. Existing M1 codes such as `CURRENT_FORMULA_CHANGED`, `SPECIFICATION_MATERIAL_MISMATCH`, `SPECIFICATION_NOT_RELEASED`, `SPECIFICATION_NOT_EFFECTIVE`, and `CATALOG_INTEGRATION_UNAVAILABLE` are preserved.
- `backend/src/test/java/com/spectrace/S3ImpactApiContractTest.java`: executable checks for route/response shape, explicit RuleSet version on the trigger, outcomes and handoff fields, S2 compatibility, error-code matrix alignment, and local/external `$ref` resolution.

The candidate incorporates the field proposals in M1 PR #47: explicit `ruleSetVersionId`, current and proposed FormulaVersion references, `draftLabelVersionId`, and proposed ChangeRequest status values. The M4-owned required ReviewTask assignee rule remains unresolved and is recorded for review. These are candidate contract choices, not accepted cross-module decisions.

## Verification

| Check | Result |
| --- | --- |
| PyYAML parse and assertions for 4 routes, 11 schemas, version and frozen S2 `ApiError` fields | PASS |
| `git diff --cached --check` | PASS |
| Local focused Maven run | Environment-blocked: installed JDK is 17; the project requires Java 21 (`release version 21 not supported`). |
| First PR CI [36369413128](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36369413128), head `93a289d` | Failed test compilation due to two AssertJ wildcard assertions; corrected in `35be056`. |
| Second PR CI [36369644455](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36369644455), head `35be056` | Found two inaccurate schema assertions (list navigation and stale 503 example); corrected in `83e059d`. |
| Final code PR CI [36369993455](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36369993455), head `83e059d` | PASS — backend 301 tests with 0 failures/errors/skips; frontend, security, SonarQube, and containers all passed. |

## Review record and remaining gate

PR [#48](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48) requests review from the actual M1/M3/M4/M5 GitHub accounts: `hxj04121-lab`, `codingbychatgpt`, `zhuwenyu04`, and `SHJ-SHJ0128`. The live PR snapshot shows four review requests, zero submitted reviews, and no review decision.

Required decisions still include M1 confirmation of RuleSet/replay and status semantics, M3 payload/pagination needs, M4 identity/permission and assignee/publication handoff rules, and M5 idempotency/503 boundaries. A review request, an open PR, or a green CI run is not reviewer acceptance. Therefore SCRUM-53 remains In Progress, SCRUM-48 remains In Progress, and no `DAY_2_DONE` marker is emitted. Do not advance to Day 3 on this calendar date.

The Windows triple-daily task is enabled for 09:00, 15:00 and 20:00 Asia/Shanghai. Its 09:00 run exited 20 before starting Codex because scheduled `JIRA_*` environment credentials were absent; a live connector write was used for today's Jira comment. The scheduled runner still needs its secure Jira credentials before a later automatic retry can write Jira.
