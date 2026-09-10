# M3 English frontend and integrated formula lifecycle

Date: 2026-09-10. Local integration on XFY of origin/main 29601a4 and the English/read-only checkpoint c87e4ec. Not pushed; no new PR or approval implied.

## Integration

Retained the English UI, Tailwind/shadcn theme, API-backed catalogs, validation and unsaved navigation guards. Incorporated main's M1 Catalog, M4 identity/audit, M5 integration code and M2 four-field error alignment. Conflict resolution retained English UI tests; main's real write scenario was updated to English rather than removed.

FormulaPersistence separates server actions from local editing. Saving returns the server-assigned version and prevents further edits to that saved snapshot. Publishing requires a separate confirmation and expectedCurrentFormulaId. Closing a saved form reloads catalog data. A successful publish stays successful even if subsequent history reads fail. Writes are guarded against overlapping clicks; network/5xx ambiguity locks further writes in the current form until manually reconciled, with no automatic mutation retries. 409 CURRENT_FORMULA_CHANGED requires history refresh and explicit reconfirmation. Read-only requests can be retried.

Local writes require a visible opt-in checkbox and localhost. This uses main's fixed DEV_EXTERNAL / dev-external-admin mapping with prov_project_seed for course fixtures. This is not secure production authentication; do not expose this development backend as a public deployment.

## Verification performed

- TypeScript and Vite build passed (existing non-blocking large-bundle warning remains).
- 17 UI/HTTP-fixture tests passed with two live tests skipped in the ordinary run.
- Opted-in live run: three tests passed (real catalog trace, no-fallback failure state, real create/publish/history). Across both runs all 19 tests were exercised successfully; fixture tests are explicitly distinguished from live evidence.
- Browser created draft V2 for prod_usda_1106285, published it, refreshed server history, and reloaded the product page.
- New formula ID: a044955b-b9e7-4fe4-b4f7-dca5606ce589. Old formula item content and released_at remained unchanged; current product pointer changed to the new version.
- Direct HTTP checks: no identity -> 401 AUTHENTICATION_REQUIRED; auditor -> 403 AUTHORIZATION_DENIED; repeated publication -> 409 VERSION_IMMUTABLE. All returned the four-field shared ApiError.
- MySQL read confirmed FORMULA_CREATED and FORMULA_RELEASED audit rows for that new ID with user_admin.
- Mocked 503 test proved retained preview and disabled repeat-create; mocked stale-pointer test proved refresh/reconfirmation without silent write retry. No live concurrent change was manufactured.

## Local runtime

Docker project: foodlabelflow-s1-integrated. Separate database volume; backend 8080, MySQL 3307, Vite 5173. The earlier foodlabelflow-m1-local containers were stopped, not deleted. Initial MySQL initialization raced with backend start; starting backend again after MySQL readiness succeeded. No shared/staging data was altered; the new V2 and audit rows remain in this isolated local database.

Run `docker compose -p foodlabelflow-s1-integrated up -d --build mysql backend`, then start Vite with supported Node 22.12+ (this run used bundled Node 24). Set PLAYWRIGHT_CHANNEL=msedge, LIVE_CATALOG=1 and LIVE_WRITES=1 for the explicitly mutating local suite. Without LIVE_WRITES the mutation test skips.

## Remaining gates

Independent human acceptance, actual contribution/hour confirmation, final PR review and remote CI for this integrated XFY revision are outstanding. Main CI results and earlier PR #5 checks do not certify this local revision. Production authentication is out of this local demo's evidence scope. Sprint 1 is not declared Done by these local checks alone.
