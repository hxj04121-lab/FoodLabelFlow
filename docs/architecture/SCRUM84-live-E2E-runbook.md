# SCRUM-84 real browser E2E: safe execution and acceptance

## What is implemented

- `scrum84-returned-revision-ui.spec.ts` verifies the V2 editor with mocked API responses.
- `ReturnedDraftRevisionHttpMySqlTest` exercises real HTTP and an owned MySQL Testcontainers database; it seeds only task/finding inputs and verifies exact V2 validation, approval, audit and publication persistence.
- `scripts/scrum84-prepare-v1.mjs` creates the V1 prerequisite with real HTTP
  adoption/change/analysis/draft/validation/submission commands after checking
  the released specification and golden input. It can write an exact resource
  receipt using `SCRUM84_BOOTSTRAP_OUTPUT`.
- `scrum84-returned-revision-live.spec.ts` consumes that receipt or an explicit
  task ID, edits declarations and performs REQUEST_CHANGES through corrected
  publication. Read-only MySQL checks use the explicitly named owned Compose
  project. It must not be counted as passing when skipped.

## Mandatory prerequisites for the browser write test

1. A disposable MySQL database (not the regular `foodlabelflow-mysql-1` volume). Use a separate Compose project name and disposable volume, or Testcontainers. Confirm the target backend is connected to it.
2. Backend with demo identity enabled **only** in a non-production environment; browser frontend proxy pointing at that backend.
3. A genuinely Maker-created V1 with `PENDING_REVIEW`, an original ReviewTask in `IN_REVIEW`, and a real `PASSED` validation for V1. Use supported HTTP business commands for V1/validation/submit; SQL may only seed prerequisite task/finding inputs, never PASS/approval/publication output.
4. Set `SCRUM84_LIVE_TASK_ID` to the exact task, or `SCRUM84_BOOTSTRAP_OUTPUT` to its bootstrap JSON. Set `SCRUM84_COMPOSE_PROJECT` to the verified owned stack. Do not use a task from the shared local course database.
5. Only after independently verifying isolation, opt in using **both** `SCRUM84_DISPOSABLE_DB=YES` and `SCRUM84_LIVE_WRITES=1`.

Example PowerShell (execute only after prerequisites are satisfied):

```powershell
cd C:\Users\35853\source\FoodLabelFlow\frontend
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8081'
$env:SCRUM84_LIVE_TASK_ID = '<disposable-task-id>'
$env:SCRUM84_COMPOSE_PROJECT = '<verified-owned-compose-project>'
$env:SCRUM84_DISPOSABLE_DB = 'YES'
$env:SCRUM84_LIVE_WRITES = '1'
npx.cmd playwright test scrum84-returned-revision-live.spec.ts --workers=1
Remove-Item Env:SCRUM84_LIVE_WRITES,Env:SCRUM84_DISPOSABLE_DB,Env:SCRUM84_LIVE_TASK_ID -ErrorAction SilentlyContinue
```

**Important:** Playwright's `reuseExistingServer` can reuse a Vite process with the wrong proxy. Stop the old Vite process or ensure its `/api` target is the disposable backend. Check `/api/identity/demo-options` before running. Never use Jenkins on port 8080 as the API target.

## Acceptance criteria

- Original task ID preserved; V2 ID distinct and version incremented.
- V1 content unchanged after revision **and** after V2 publication.
- V2 validation is fresh; V1 PASSED is not accepted for V2.
- Checker and Publisher use distinct authorized identities.
- Final task CLOSED and V2 PUBLISHED/current.
- Back-end MySQL tests verify approval/publication/audit records and zero-write denial paths.

## Honest status reporting

- PASS: test ran with real browser and disposable backend and all assertions passed.
- SKIPPED: explicit write flags or fixture absent; **not** an E2E pass.
- BLOCKED: disposable backend/fixture unavailable.
- FAIL: test executed but an assertion failed.

The containers workflow now provisions a separate `s3-revision-ci` database,
bootstraps V1 through HTTP, then explicitly enables the browser write test only
against that stack. Its loopback ports are MySQL 13308, backend 18081 and frontend
15174. No business outputs are seeded in SQL. The earlier S3 stack and its
history proof remain separate. The new stage requires its own actual main-run
receipt; the 9 October local pass is recorded in M3's evidence document.
