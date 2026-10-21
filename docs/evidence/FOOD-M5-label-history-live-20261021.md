# FOOD M5 live label-history check — 2026-10-21

## Candidate and isolation

The tested code was the one-file frontend test diff based on `ef2c8e82a7c16f99f037e5d20e16b814cbbc0c98` (the exact parent commit used for the local M5 run). No production Java code changed. The run used the existing `S3ProductFlowBrowserHarness`, which owns a fresh Testcontainers MySQL 8.4.11 database, a random-port Spring server, loopback Vite, and Playwright browser contexts. It added only released specification input before the actual browser created declarations, validation runs, approvals, and publications. No prior FOOD reset/reseed database, volume, or one-time demo data was used.

## Exact run

Date: 2026-10-21, 14:49:34–14:53:32 UTC (22:49:34–22:53:32 +08:00).

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -Dtest=S3ProductFlowBrowserHarness test `
  '-Ds3.checkout=..' `
  '-Ds3.browser.evidence=C:\Users\rcncai\Documents\Codex\2026-10-21\task-2\FOOD-M5-ef2c8e8-history-evidence-r2'
```

Result: Maven harness 1/1 passed, 0 failures/errors/skips (`BUILD SUCCESS`, 237 seconds). The four real browser child specs each passed 1/1:

| Spec | Browser assertion |
| --- | --- |
| `catalog-live.spec.ts` | M1 real read-only product and formula integration |
| `validation-live.spec.ts` | Seeded PASS and new blocking FAIL through browser and persisted API |
| `s3-product-flow-live.spec.ts` | Live S3 SOY flow with twenty independently approved publications and preserved history |
| `formula-lifecycle-fullstack.spec.ts` | Creates and publishes formula while preserving old content |

This run recorded 40 formula adoptions, 40 findings (20 `NO_ACTION`, 20 `REVIEW_REQUIRED`), 20 publications, and 60 preserved prior label histories.

## Added live assertions

Immediately after the first actual browser publication, the same harness database and real backend were queried through two Playwright browser-context request clients:

- The isolated auditor identity had `AUDIT.READ`; `GET /api/audit/products/{productId}/label-history` returned 200 and included both the original and newly published label versions, `SOY`/`WHEAT` declarations, approval for the exact review task, publisher, and `LABEL_DECISION_RECORDED` / `LABEL_PUBLISHED` audit events.
- The actual draft creator had no `AUDIT.READ`; the same endpoint returned 403 `AUTHORIZATION_DENIED`.

The first replacement draft also verifies its actual previous-label declarations are initially checked and the missing `SOY` is initially unchecked. It adds only `SOY`, delays one current-product formula GET and then continues that actual backend request, and confirms the stale-formula warning appears without changing the user's declaration selection. The delay does not supply or alter any API response.

The machine-generated details are in `s3-product-flow-observations.json` in the evidence folder above. In the first S3 product, the auditor returned 200, the creator returned 403, and the history contained original `label_1118468_v1` plus the newly published version with `SOY` and `WHEAT`.

## Failed first attempt retained

An earlier run of this test change reached 11 publications, then failed at the pre-existing checkbox interaction for the next product because the test tried to click before asserting that asynchronous replacement prefill had completed. The new version waits for the actual prefill status and checks initial state before editing. The failed run remains in `FOOD-M5-ef2c8e8-history-evidence`; its history API assertions had already passed for the first product. This failed run is not counted as a pass.

## Limits

The test actors are local DEV_EXTERNAL fixtures, not real human sign-in. This one local run does not substitute for a second team member's independent reset/replay, formal security risk acceptance, or remaining Jira acceptance. The successful CI previously associated with `ef2c8e8` did not run this newly added local browser assertion; this increment still needs CI on its own commit.

## Same-page identity isolation follow-up

The tested tree was based on `621ce7e31f8e9da6de807aad52ebb1eeb75be09c`, with changes to `frontend/tests/scrum84-identity-live.spec.ts` and `backend/src/test/java/com/spectrace/workflow/S3ProductFlowBrowserHarness.java`. The harness was run on 2026-10-21 from 15:04:14 to 15:08:31 UTC with JDK 25.0.4, MySQL 8.4.11 in a fresh Testcontainers container, random-port Spring, loopback Vite (`127.0.0.1:62383`), and a real Playwright Chromium context.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -Dtest=S3ProductFlowBrowserHarness test `
  '-Ds3.checkout=..' `
  '-Ds3.browser.evidence=C:\Users\rcncai\Documents\Codex\2026-10-21\task-2\FOOD-M5-identity-evidence-r3'
```

Result: Maven harness 1/1 passed with 0 failures/errors/skips. All five nested real-browser specs passed 1/1, including the new identity isolation test (7.2 seconds). In one page and browser context, Maker selected unsaved `SOY` and draft consent, then switched to Checker. The route stayed on the same product, the Maker-only fields were cleared, Checker had neither `LABEL.CREATE` nor `AUDIT.READ`, and the real local history endpoint returned 403 `AUTHORIZATION_DENIED`. Switching back to Maker did not restore either unsaved value. No `POST /api/labels/drafts` was sent. The identity test makes no business-record writes; other existing harness cases write only to this run's ephemeral database.

Raw JSON: `../FOOD-M5-identity-evidence-r3/identity-switch-isolation.json` (SHA-256 `06175ff3efbb00654f0eeed7c9f82d3a337993fc44478bf78ebe0f10d618d37f`). This is evidence from the local DEV_EXTERNAL demo identities, not real human authentication. CI for this new increment has not been checked by this task.
