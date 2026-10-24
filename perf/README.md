# Scripted performance tests (k6)

| Script | What it measures | Writes data |
|---|---|---|
| `read-mix.js` | Change Manager read mix: catalog list 35 %, product detail 30 %, impact report 20 %, label history 15 % | no |
| `write-path.js` | Data Admin batch adoption of a released Spec V2 + Change Manager impact run, timed separately | yes (disposable stacks only) |

`run.sh` prepares the data through the public API with `scripts/s4-m1-spec-v2-demo.sh`
(`fresh`, then `spec-v2` / `adopt` / `change` / `impact`, no SQL) and passes the impact analysis ID
to k6 through `IMPACT_ANALYSIS_ID`.

```bash
perf/run.sh read-mix                                    # new isolated Compose stack, baseline profile
PROFILE=smoke SPECTRACE_API=http://127.0.0.1:8080 perf/run.sh read-mix   # reuse a running disposable stack
ITERATIONS=3 SPECTRACE_API=http://127.0.0.1:8080 perf/run.sh write-path
THRESHOLDS=off PROFILE=capacity SPECTRACE_API=... perf/run.sh read-mix   # measure only
python3 perf/summarize.py perf/results/*/summary.json   # Markdown table per run
```

Profiles (`PROFILE`): `smoke` 2 VUs × 30 s (CI), `baseline` 10 VUs × 2 min with 0.1–0.3 s think time,
`capacity` 20 VUs × 1 min without think time, `ramp` 0 → `VUS` (40) over 1 min, hold `DURATION` (4 min),
used by `scripts/s4-autoscale-demo.sh`.

Each run writes `perf/results/<UTC>-<script>-<sha>/` (git-ignored): `summary.json` (k6 summary export),
`report.html` (k6 web dashboard export), `k6.log`, `context.env` (commit, IDs, k6 version).
CI runs the smoke profile last in the `containers` job and uploads it as artifact `perf-smoke-<sha>`.

Thresholds (error rate < 1 %, per-endpoint p95) are derived from the measured baseline in
[`docs/evidence/S4-performance-and-autoscaling.md`](../docs/evidence/S4-performance-and-autoscaling.md).

The scripts send the development header identity (`X-Auth-Provider: DEV_EXTERNAL`).
Point them only at a loopback stack, the CI stack or a `kubectl port-forward` on 127.0.0.1 —
never at the hosted course domain, which keeps its own Compose deployment.
