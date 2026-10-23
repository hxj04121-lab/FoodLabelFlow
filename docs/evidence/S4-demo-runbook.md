# Sprint 4 SOY demo runbook

Owner: M5 (SCRUM-89), with the M3 browser story (SCRUM-87).

## 1. Start an isolated environment

Requires Docker with Compose v2. From the repository root:

```bash
scripts/s4-m1-spec-v2-demo.sh fresh-ui
```

`fresh-ui` creates a new, uniquely named Compose project and its own MySQL
volume. Before starting, it stops any earlier `spectrace-s4-*` demo project so the
loopback ports are free; stopped projects keep their containers and volume for
inspection. `scripts/s4-m1-spec-v2-demo.sh list` shows them, and
`scripts/s4-m1-spec-v2-demo.sh down PROJECT` deletes one, including its
database volume, once its evidence is recorded. The script prints its
project and a unique `DEMO_PREFIX`. You may provide a validated `DEMO_PREFIX`
before invoking it.

The script starts MySQL, API and web, waits for their health checks, and writes
no S4 business records. Before the live flow, verify the Compose project and
volume labels, 60 baseline products, five released V1 specifications, zero
Change Requests and zero review tasks. A requested Compose project that already
has containers, volumes or networks is refused. Ports bind to `127.0.0.1` only.
Override numeric `MYSQL_HOST_PORT`, `BACKEND_HOST_PORT`, and
`FRONTEND_HOST_PORT` (defaults 3307, 8080 and 5173).

Use `fresh` when rehearsing the M5 fixture: it resets and creates/releases Spec
V2 through the public API. Use `fresh-ui` for the full browser story below.

Open the URL printed after `Demo ready`.

## 2. Live story (browser)

Use the **Demo identity** switcher in the top bar for each step.

| # | Identity | Page | Action | Expected |
|---|---|---|---|---|
| 1 | Data Admin | Materials & specs → Supplier specifications | Create Chocolate Base's next specification. The form starts with V1's components; add Soy Lecithin, include the printed `DEMO_PREFIX` in its matching evidence, save the draft, inspect its ID/components, then explicitly release it. | A new RELEASED V2 and its full ID are shown. S4 exposes catalog maintenance through Data Admin; there is no separate Supplier demo login. |
| 2 | Data Admin | Change impact → batch adoption panel | Select the V2 ID from step 1; choose **Adopt for current products**. | 40 adopted, 0 skipped, 0 failed, each product named. A replay returns the same 40 FormulaVersion IDs. |
| 3 | Change Manager | Change impact | Create the `INGREDIENT_SPEC` request `spec_chocolate_v1` → the V2 ID. Put the printed `DEMO_PREFIX` in its description, then **Run impact analysis** with `ruleset_us_falcpa_demo_v1`. | 40 findings · 20 no action · 20 review required. **Filter findings → Review required** shows 20 named products. |
| 4 | Maker | Finding → **Prepare replacement label** | Keep prior declarations, add `SOY CONTAINS`, create the draft, run validation and submit. | Validation passes; the task waits for review. The Maker cannot approve it. |
| 5 | Checker | Review workspace | Approve the exact validated label. | The label is approved by an independent identity. |
| 6 | Publisher | Review workspace | Publish. | New label is PUBLISHED; prior label is SUPERSEDED. |
| 7 | Change Manager or Data Admin | Label history | Select the same product. | V1 SUPERSEDED and V2 PUBLISHED show creator, approver, publisher, timestamps and audit events. Maker cannot read audit history. |

Negative paths: Change Manager's **Adopt for current products** action is
disabled without `DATA.MAINTAIN` and `FORMULA.RELEASE`; a failed validation
blocks submission; a Maker cannot approve its own pending label or open Label
history; repeated publication is rejected.

Business writes in the browser flow go through the UI. The live test uses
read-only API requests to verify persisted state and IDs; it does not create
business records through direct API requests or SQL.

## 3. Separate API-only shortcut

This path writes workflow data through the API. Run it only against a fresh
isolated stack:

```bash
scripts/s4-m1-spec-v2-demo.sh all
```

Expected: `candidateCount=40 adoptedCount=40 skippedCount=0 failedCount=0`,
then `relevantProductCount=40 noActionCount=20 reviewRequiredCount=20`.

## 4. Release-candidate record

Record the final source SHA, its matching CI run (backend, frontend, security,
containers and Sonar), the `fresh-ui` browser run, saved screenshots/logs/IDs,
and the name of another member who independently rehearsed the final build.
Keep failed first attempts with their cause.
