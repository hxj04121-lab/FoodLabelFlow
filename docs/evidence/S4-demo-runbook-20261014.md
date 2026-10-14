# Sprint 4 product change demo

## Reset

From the repository root, run:

```powershell
./scripts/s4-demo-reset.ps1
```

The script resets only the Compose project `foodlab-s4-demo`, then rebuilds and
waits for its services. It defaults to loopback-only host ports 13309 (MySQL),
18082 (API) and 15175 (web). To choose other ports, set `MYSQL_HOST_PORT`,
`BACKEND_HOST_PORT` or `FRONTEND_HOST_PORT` to numeric port values before
running it; the script continues to bind them to `127.0.0.1`. The default
Compose project and its MySQL volume are left alone. Open
`http://127.0.0.1:15175` after the script reports readiness.

## Demo path

1. Select **Data Admin** in the demo identity switcher. In Materials & specs,
   create a new specification for `mat_chocolate_base` with a changed component
   set, then release it. Keep the prior released version available for comparison.
2. In Change impact, use the new specification as the target and choose the
   prior version. Select **Adopt for current products**. Show the per-product
   adopted, skipped and failed counts. Run the same request again to show that
   recorded products replay their saved FormulaVersion IDs.
3. Select **Change Manager**. Create an ingredient-specification change request
   from the prior version to the new target and run impact analysis. Filter the
   results to `REVIEW_REQUIRED`; use the readable product name and exact task
   link to open one affected product.
4. Select **Maker**. The first replacement draft for that task preloads the
   prior published label's user-entered declarations. Review the declarations,
   create the draft, run fresh validation and submit it for review.
5. Select **Checker** and approve the exact validated label. Select **Publisher**
   and publish the approved version. Keep the maker and checker identities
   separate so the server-side maker-checker rule remains visible.
6. Select **Change Manager**, **Data Admin** or an Auditor with `AUDIT.READ`.
   Open Label history for the same product and inspect label versions, stored
   declarations, approval records, publication details and attributable audit
   events.

## Contract and release review

- Check the HTTP route, request/response fields and error cases in the S4
  contract before comparing consumers.
- Record the branch commit SHA with backend, frontend, browser, security and
  static-analysis results. Use a fresh reset for the final end-to-end rerun.
- A local candidate does not establish independent teammate acceptance. Record
  that acceptance against the exact candidate SHA when a second reviewer runs
  the scenario.

## Candidate limitations

The batch operation applies one released target specification to every current
released product formula using that material. Each product commits on its own;
the returned summary can contain both successful and failed items. A replay of a
committed product returns its saved version. Products whose formula no longer
qualifies are reported as skipped or failed and need operator review.

The reset script has not been run in this environment because Docker Engine is
unavailable. The full-browser rehearsal and independent reviewer acceptance
remain release gates until recorded against a commit SHA.
