# M1 read-only integration

2026-09-09. Local changes only; not pushed to XFY or PR #5.

Backend verified at M1 commit 3c4fc442ac5c81020fc57d91628e9335e9d3d754 in a separate detached worktree, FoodLabelFlow-M1-local. No M1 source was merged into the frontend branch. Docker project foodlabelflow-m1-local has its own MySQL volume.

## Reproduce

In the M1 checkout, run `docker compose -p foodlabelflow-m1-local up -d --build mysql backend`. This uses host ports 8080/3307; ensure they are free, without removing another project's data. Run the XFY Vite frontend on port 5173, which proxies /api to localhost:8080. These are local addresses, not a remote deployment. Both containers became healthy during this run; Flyway initialized the separate project DB.

## Frontend

API-backed suppliers, materials, specifications/components, products, formula histories and items replace bundled preview reads. Offset lists are fetched at limit 100 until a short page. The small S1 dataset is loaded completely (up to 10,000 list records, with an explicit error above that), then searched/paginated locally. Detail requests run in bounded batches of six. This is a small-dataset integration, not an optimized large-catalog service; histories and detail retrieval can be made lazy later.

Loading and timeout errors have retry UI and never silently fall back to seed data. Product current_formula_version_id determines current formula. Version/history/status values come from responses. Chart counts derive from fetched records. Fixture group labels still represent demo groups, not completed impact analyses. Refresh reloads the API snapshot; no real-time push updates are claimed.

M1 code/message errors are handled without fabricated trace IDs; the extra nullable keys required by M2 remain a backend contract alignment item. Quantity/unit pairing and positive decimal rules follow M1's current implementation, pending M2 confirmation. Only released and locally date-effective specifications are selectable; server date/authorization remains authoritative once writes open.

## Validation and limits

TypeScript/Vite builds passed. 14 Playwright checks passed, including one real M1 read-only integration that compares product and trace endpoint responses to browser detail/history, one 503/no-fallback test, and 12 existing UI tests with explicitly mocked Catalog HTTP responses. No test substitutes mock results for live-write evidence.

Run `LIVE_CATALOG=1 npm run test:e2e` with backend running (PowerShell: `$env:LIVE_CATALOG='1'`). Without this variable the real integration test is skipped. Live screenshot is generated in ignored frontend/test-results/live-catalog.png.

No POST create/release was invoked. M4 identity/audit integration, callable writes, no-duplicate-write handling, authorization and real create/release/history acceptance remain pending. Backend Maven tests/remote CI were reported by M1, not rerun or independently verified here; Docker image packaging skipped tests as defined by its Dockerfile.
