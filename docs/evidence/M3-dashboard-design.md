# M3 dashboard design and local validation

Date: 2026-09-07. Delivery branch: XFY (created from the local feature/m3-dashboard-design work). Base: 97fd46ebb27830a7bce5a2bee2f214519a37f622. This record accompanies the frontend delivery commit; remote CI and merge approval are not implied.

## Design

References inspected visually:
- Pinterest Goboos dashboard: https://pin.it/1MEwUFYki (resolved Pin 239887117643595356).
- Yehor Haiduk / takasho, AI web dashboard: https://dribbble.com/shots/14510797-AI-web-dashboard.

Use the first reference's navy horizontal header, quiet sidebar and data hierarchy; combine the second reference's rounded white panels, purple accents and selective cyan/violet gradients. No reference artwork, logo, or fabricated analytics were embedded. Icons are Lucide. Components were installed using the official shadcn registry (new-york style), then adapted to local aliases and theme.

## Implemented scope

- Removed Ant Design and its icon dependency; retained React, TypeScript, Vite and React Router.
- Added Tailwind Vite integration, shadcn Button/Input/Badge/Dialog/Tabs, aliases and custom theme.
- Navigable overview, suppliers, materials, products, formulas, labels, impact, reviews and health routes.
- Seed product search, group filter, pagination, CSV export and product/formula/specification traceability dialogs.
- Details include baseline version history and provenance. Later workflow routes explain unavailable functionality.
- Desktop sidebar, mobile overlay navigation, keyboard focus styles, reduced-motion support and scrollable tables.

## Data truth and integration boundary

`frontend/scripts/build-seed-preview.py` mechanically exports the repository's V3 seed into `frontend/src/data/seed-preview.json`. The snapshot embeds the SQL SHA256. Counts: 60 products, 2 suppliers, 5 materials, 60 formula versions, 160 formula items. This is not a live query. Group classifications are fixture groups, not completed impact findings. Supplier/formula relationships are project-seeded rather than actual brand supply chains.

Only `/api/health` remains a real HTTP integration. Its success and failure rendering were tested using explicitly mocked HTTP responses. A direct live health check during this task timed out; no current backend availability is claimed. The user can retry from the health page after starting backend services.

No real create/release/history business smoke, write API, authentication, approval/publication, remote CI, human acceptance, or Sprint completion is claimed.

## Validation

- TypeScript build and Vite production build passed using Node 24.19.0. Installed system Node 20.15 is below Vite's supported engine requirement; use Node 22.12+ or the existing bundled Node 24.
- Five Playwright tests passed on headless Microsoft Edge: overview/chart/CSV, search/filter/pagination/detail tabs, all module routes/material detail, 390px navigation and overflow, mocked health success/error/retry.
- Mobile overflow was reproduced by the test and fixed by positioning the visually hidden table heading relative to its cell.
- Desktop overview, formula table and mobile screenshots inspected. Screenshots are generated locally in `frontend/test-results/` and excluded from Git.
- `git diff --check` passed. No package audit vulnerabilities were reported at install time; this is not a substitute for team CI/security gates.

Reproduce from frontend with a supported Node version: `npm ci`, `npm run build`, `npm run test:e2e`. Playwright defaults to installed Edge; set PLAYWRIGHT_CHANNEL=chromium and install the matching Playwright browser if Edge is unavailable. This Windows run used the bundled Node executable to invoke TypeScript, Vite and the Playwright CLI directly.

## Remaining work

Integrate accepted M1/M2 Catalog and Formula contracts, build the real create/release flow with permissions and validation, replace preview snapshot access with API adapters, then gather live integration and human acceptance evidence. Local design implementation does not change PM hashes or existing historical evidence.
