# SCRUM-84 acceptance and live E2E boundary

**Status:** Partially implemented; full-browser revision/publication E2E is **NOT RUN**. Do not close SCRUM-84 based on mocked browser regressions.

## Evidence levels

| Evidence | What it establishes | What it does not establish |
| --- | --- | --- |
| `scrum84-identity-switch.spec.ts` | UI switch and stale response handling with HTTP fixtures | Real server authorization |
| `scrum84-returned-revision-ui.spec.ts` | Maker revision editor calls expected API and resets displayed validation | Real database transaction |
| `scrum84-identity-live.spec.ts` | Real demo-options, identity lookup and browser actor switching on isolated Compose | Authorization of every workflow write |
| `ReturnedDraftRevisionHttpMySqlTest` | Real HTTP/MySQL returned task revision, version binding, validation, approval and publication | Browser completion of entire workflow |

## Full browser E2E prerequisites

1. Use a disposable database/container with explicit demo authentication enabled, never production or a shared course database.
2. Prepare a real V1 ReviewTask in `IN_REVIEW` through supported APIs; SQL is permitted only for *input fixtures* such as task/finding setup, never to fabricate approval, validation or publication outcomes.
3. Record V1 declarations, validation IDs, task ID, and approval history before the revision.
4. In the browser, Checker requests changes; Maker opens the same task, creates V2 and edits declarations; Maker runs a fresh V2 validation and submits; independent Checker approves; Publisher publishes.
5. Assert unchanged V1 snapshot and approval history, unchanged ReviewTask ID, new V2 ID and version, exact V2 PASSED validation, exact V2 APPROVE record, one publication record and consistent product pointer/audit.
6. Check unauthorized writes and stale V1 publication produce no committed mutations. Clean up the disposable database only.

**Do not set `LIVE_WRITES=1` on a non-disposable database.** The existing `formula-lifecycle-fullstack.spec.ts` changes a product's current formula and is not a substitute for this test.

## CI wiring

- `frontend` job: compile and run deterministic identity/race and returned-revision UI tests.
- `backend` job: `mvn clean verify` with Testcontainers; preserve Surefire reports.
- `containers` job: launch disposable full stack and run real identity read/switch smoke; keep existing live catalog/validation/S3 coverage.
- Full browser V1→V2 publication remains a **blocking follow-up** until a self-contained isolated fixture and executable browser test are implemented. Never label CI green as proof of that missing case.
