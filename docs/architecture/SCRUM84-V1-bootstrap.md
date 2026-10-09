# SCRUM-84 V1 bootstrap (isolated database only)

`frontend/scripts/scrum84-prepare-v1.mjs` prepares a real V1 task through HTTP, with no SQL business-state manipulation. It reproduces the S3 40-product adoption and impact-analysis prerequisites; this is intentionally a **write-heavy fixture** and cannot run on the shared course database.

## Required preconditions

- A disposable MySQL instance, separately configured Spring Boot instance, and Vite proxy targeting that instance. Never reuse the normal 3307 development database or a shared course database.
- Baseline 60-product S3 dataset, released `spec_chocolate_v2` fixture, and development demo identities enabled **only** on the isolated instance. The fixture is described in `backend/src/test/resources/fixtures/s3-soy-spec-v2-adoption.sql`; do not apply its formula/adoption SQL as a shortcut because the bootstrap intentionally tests the real adoption API.
- Ensure `SCRUM84_API_BASE_URL` is the isolated backend's loopback URL and verify its datasource independently before setting write flags.
- Node.js 20+.

## Run from repository root in PowerShell

```powershell
$env:SCRUM84_API_BASE_URL = 'http://127.0.0.1:8082'
$env:SCRUM84_LIVE_WRITES = '1'
$env:SCRUM84_DISPOSABLE_DB = 'YES'
$env:SCRUM84_BOOTSTRAP_CONFIRM = 'I_UNDERSTAND_THIS_WRITES_DATA'
node .\frontend\scripts\scrum84-prepare-v1.mjs
```

If successful, copy the printed `SCRUM84_LIVE_TASK_ID` into the same PowerShell session. Set `VITE_API_PROXY_TARGET` to the **same isolated backend** and ensure Vite is restarted, not reused with its old proxy. Then run:

```powershell
$env:SCRUM84_LIVE_TASK_ID = '<ID_PRINTED_BY_BOOTSTRAP>'
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8082'
cd frontend
npx.cmd playwright test scrum84-returned-revision-live.spec.ts --workers=1
```

This browser test requires `SCRUM84_LIVE_WRITES=1` and `SCRUM84_DISPOSABLE_DB=YES`, already set above. It performs real REQUEST_CHANGES, V2 revision, validation, resubmission, approval and publication. **Do not run the bootstrap twice against the same DB**; reset only the disposable instance between runs.

## Scope and limits

The bootstrap validates a real V1 `PASSED` and `IN_REVIEW` state. The browser test validates V2 publication and V1 immutability. Existing `ReturnedDraftRevisionHttpMySqlTest` supplies deeper approval, validation and persistence assertions. This does not prove production login or CI execution. Failure after any write may leave partial data in the disposable database; discard that database rather than trying to replay writes.
