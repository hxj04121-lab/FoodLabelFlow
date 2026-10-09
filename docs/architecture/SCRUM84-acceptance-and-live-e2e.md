# SCRUM-84 acceptance and live E2E boundary

**Status:** Full browser correction/publication locally **PASS** on the 9 October
M3 integration. Team review and matching merged-main CI remain pending. This is
M3's source-bound execution supplement, not M4 signoff or Jira closure.

The original NOT RUN assessment below was resolved by an isolated API bootstrap
and expanded browser/API/DB assertions. The test edits Soy declaration text,
retains the same task, verifies the new ID has no inherited validation, runs a
fresh PASS, uses a distinct checker and publisher, reloads the published label
under the preserved Publisher selection, and checks exact approval/publication,
current-validation association and immutable historical records. Missing PASS,
old-version submission and Maker's missing approval permission cause no new
business/audit records. The Maker ACL denial is separate from the permitted
creator policy negative in the existing S3 suite.

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
3. Record V1 declarations, validation IDs, task ID, and approval history before the revision. Set `SCRUM84_COMPOSE_PROJECT` to the exact owned project for read-only MySQL assertions.
4. In the browser, Checker requests changes; Maker opens the same task, creates V2 and edits declarations; Maker runs a fresh V2 validation and submits; independent Checker approves; Publisher publishes.
5. Assert unchanged V1 snapshot and approval history, unchanged ReviewTask ID, new V2 ID and version, exact V2 PASSED validation, exact V2 APPROVE record, one publication record and consistent product pointer/audit.
6. Check unauthorized writes and stale V1 publication produce no committed mutations. Clean up the disposable database only.

**Do not set `LIVE_WRITES=1` on a non-disposable database.** The existing `formula-lifecycle-fullstack.spec.ts` changes a product's current formula and is not a substitute for this test.

## CI wiring

- `frontend` job: compile and run deterministic identity/race and returned-revision UI tests.
- `backend` job: `mvn clean verify` with Testcontainers; preserve Surefire reports.
- `containers` job: launch disposable full stack and run real identity read/switch smoke; keep existing live catalog/validation/S3 coverage.
- Containers now create a separate `s3-revision-ci` stack on loopback ports,
  import only released specification inputs, and run the API bootstrap followed
  by the full correction browser test. Its data is independent of the earlier
  twenty-publication scenario. Bootstrap JSON, raw observations, screenshots
  and traces are retained in the browser artifact before both owned CI stacks
  are disposed. A successful local replay or earlier PR87 checks are not a
  successful execution of this newly wired stage on merged main.
