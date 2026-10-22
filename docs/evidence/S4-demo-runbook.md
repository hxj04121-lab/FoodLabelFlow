# Sprint 4 SOY demo runbook

Owner: M5 (SCRUM-89), with the M3 demo script (SCRUM-87) layered on top.
Everything below runs through the browser or the public API; no SQL.

## 1. Reset

Requires Docker with Compose v2, `curl` and `python3`. From the repository root:

```bash
scripts/s4-m1-spec-v2-demo.sh fresh
```

`fresh` does two things:

1. `reset` — drops and rebuilds the separate Compose project `spectrace-s4-demo`
   (its own MySQL volume; the everyday development volume is untouched), lets
   Flyway apply the baseline V1–V12, and waits until MySQL, API and web are healthy.
   Every port is published on `127.0.0.1` only. Override with numeric
   `MYSQL_HOST_PORT`, `BACKEND_HOST_PORT`, `FRONTEND_HOST_PORT` (defaults 3307,
   8080, 5173).
2. `spec-v2` — as Data Admin, creates and releases Chocolate Base Spec V2
   (V1 plus Soy lecithin) and prints its id. This is the only demo input: no
   adoption, ChangeRequest, finding, task or publication exists yet.

Open `http://127.0.0.1:5173` once it prints `Demo ready`.

## 2. Live story (browser)

Use the **Demo identity** switcher in the top bar for every step.

| # | Identity | Page | Action | Expected |
|---|---|---|---|---|
| 1 | Data Admin | Change impact → batch adoption panel | **Released specification** = the Spec V2 id printed by `fresh`; **Adopt for current products** | 40 adopted, 0 skipped, 0 failed, each product named. Running it again replays the same 40 FormulaVersion ids. |
| 2 | Change Manager | Change impact | Create the `INGREDIENT_SPEC` request `spec_chocolate_v1` → Spec V2, then **Run impact analysis** (rule set `ruleset_us_falcpa_demo_v1`) | 40 findings · 20 no action · 20 review required. **Filter findings → Review required** shows 20 named products. |
| 3 | Maker | Finding → **Prepare replacement label** | Declarations prefill from the previous published label; add `SOY CONTAINS`, create the draft, run validation, submit | Validation PASS; task is waiting for review. |
| 4 | Checker | Review workspace | Approve the exact validated label | Approved. Approving as the Maker is refused (self-approval). |
| 5 | Publisher | Review workspace | Publish | New label PUBLISHED, previous SUPERSEDED. |
| 6 | Change Manager or Data Admin | Label history | Select the same product | V1 SUPERSEDED and V2 PUBLISHED with creator, approver, publisher, timestamps and audit events. A Maker sees "This identity cannot read audit history". |

Negative paths worth showing: Change Manager's **Adopt for current products**
button is disabled (needs `DATA.MAINTAIN` + `FORMULA.RELEASE`); a FAIL
validation blocks submission; Maker cannot open Label history.

## 3. API-only shortcut

For a quick check without the browser, after `reset`:

```bash
scripts/s4-m1-spec-v2-demo.sh all
```

Expected: `candidateCount=40 adoptedCount=40 skippedCount=0 failedCount=0`, then
`relevantProductCount=40 noActionCount=20 reviewRequiredCount=20`.

## 4. Release-candidate record

For the final run, record in the evidence index: the main commit SHA, its CI run
(backend, frontend, security, containers, Sonar), this runbook executed after a
`fresh` reset, and the name of the member who ran it other than the author.
Keep failed first attempts, with their cause.
