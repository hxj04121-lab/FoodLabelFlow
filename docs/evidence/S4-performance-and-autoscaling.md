# S4 quality attributes: scripted performance testing and auto-scaling

Quality attributes for the final demonstration:

- **Performance**: scripted k6 tests with thresholds derived from a measured baseline.
- **Scalability**: a Kubernetes HorizontalPodAutoscaler that adds and removes backend replicas
  under load.
- **Maintainability**: the existing GitHub Actions pipeline now also runs a thresholded k6 smoke
  test and stores its summary per commit.

> **Status: Mac rehearsal.** Every number below was measured on the author's Mac
> (Colima k3s / Compose). The teammate's Windows (WSL2 + Docker Desktop + k3d) reproduction
> goes in [the section reserved for it](#teammate-windows-reproduction-results). The hosted course
> site keeps its existing Compose deployment and was not load-tested.

Nothing here changes product code. Everything lives in `perf/`, `deploy/k8s/`,
`scripts/s4-autoscale-demo.sh`, `.github/workflows/ci.yml` (containers job) and this folder.

| | |
|---|---|
| Branch | `claude/s4-perf-autoscale`, cut from `codex/food-m3-m4-on-pr91` @ `88df3a3` (PR #92 head) |
| Baseline measured at | `0aaacfc` (scripts as committed; run with `THRESHOLDS=off`, so the placeholder thresholds were not used) |
| Write path measured at | `0aaacfc` plus the 3 s iteration pause and thresholds, committed unchanged as `5cf8149` |
| Auto-scaling measured at | `5b97e58` |
| Raw artifacts | [`s4-perf/`](s4-perf/): k6 `summary.json`, `report.html` (k6 web dashboard export), k6 output, HPA timeline, HPA events |

## Environment (Mac rehearsal)

| Item | Value |
|---|---|
| Host | Apple M4, 10 cores, 24 GB, macOS 15.5 |
| Container VM | Colima 0.10.1, aarch64, Virtualization.Framework, virtiofs, **4 CPUs / 8 GiB** |
| Docker | 29.2.1 (Colima docker runtime) |
| Kubernetes | k3s v1.35.0+k3s1 single node inside Colima (`colima kubernetes start`), metrics-server v0.8.0, kubectl 1.37.1 |
| Load generator | k6 v2.3.0 (Homebrew), running on the macOS host |
| Application | Spring Boot backend (Java 21), MySQL 8.4.11, nginx frontend, as in `docker-compose.yml` |

## 1. Performance (k6)

### Method

- `perf/read-mix.js`: Change Manager reads (`X-Auth-Provider: DEV_EXTERNAL`,
  `X-External-Subject: dev-external-change-manager`). The mix is catalog list
  `/api/catalog/products?limit=50` 35 %, product detail 30 %, `GET /api/v1/impact-analyses/{id}`
  20 % (about 28 KB, 40 findings) and `GET /api/audit/products/{id}/label-history` 15 %.
- `perf/write-path.js`: each iteration releases its own Spec V2, then times the Data Admin batch
  adoption `POST /api/catalog/specifications/{id}/formula-adoptions` and the Change Manager impact
  run. It checks 40 adopted and 40 relevant on every iteration.
- `perf/run.sh` starts a fresh isolated Compose stack with `scripts/s4-m1-spec-v2-demo.sh fresh`,
  then runs `adopt`, `change` and `impact` through the API (no SQL). It passes
  `IMPACT_ANALYSIS_ID` to k6. The setup reproduced the golden result: **40 adopted, impact
  40 relevant / 20 NO_ACTION / 20 REVIEW_REQUIRED**.
- Baseline profile: 10 VUs for 2 min with 0.1–0.3 s think time, run 3 times on the same stack.
  The capacity profile is 20 VUs for 1 min with no think time.

### Results: whole run

| Run | Requests | Throughput (req/s) | Error rate | p50 (ms) | p95 (ms) | p99 (ms) |
|---|---|---|---|---|---|---|
| baseline 1 (fresh stack) | 5 826 | 48.4 | 0.00 % | 6.0 | 16.3 | 20.7 |
| baseline 2 | 5 773 | 48.0 | 0.00 % | 5.5 | 15.8 | 17.5 |
| baseline 3 | 5 779 | 48.0 | 0.00 % | 5.4 | 15.8 | 17.7 |
| capacity (20 VUs, no think time) | 59 265 | **986.9** | 0.00 % | 16.5 | 46.5 | 63.3 |

Baseline throughput is set by the think time (10 VUs × about 4.8 iterations/s). The capacity run
shows what one backend replica on a 4-CPU VM can serve.

### Results: per endpoint

| Endpoint | baseline p50 (ms, 3 runs) | baseline p95 | baseline p99 | capacity p50 / p95 / p99 | **threshold p95** |
|---|---|---|---|---|---|
| catalog_list | 5.2–5.7 | 6.8–7.8 | 7.7–10.2 | 14.2 / 28.9 / 37.5 | < 100 ms |
| product_detail | 4.5–4.9 | 5.7–6.8 | 6.6–9.4 | 10.9 / 24.1 / 32.7 | < 75 ms |
| impact_report | 13.8–14.5 | 17.5–19.7 | 19.2–27.0 | 35.3 / 62.4 / 76.9 | < 200 ms |
| label_history | 8.6–8.9 | 10.8–12.3 | 11.9–16.7 | 22.2 / 42.4 / 53.4 | < 150 ms |

| Write path (3 iterations) | min | p50 | p95 | max | **threshold p95** |
|---|---|---|---|---|---|
| batch adoption (40 products) | 238 ms | 267 ms | 284 ms | 286 ms | < 1000 ms |
| impact run (40 products) | 91 ms | 106 ms | 124 ms | 126 ms | < 500 ms |

All write-path checks passed (25/25), and the error rate was 0 %.

### How the thresholds were set

The tests were measured first with `THRESHOLDS=off`, and the limits were set from those results:

- **Error rate** below 1 % (`http_req_failed`) and checks above 99 %, on every profile.
- **Read p95**: about 3× the p95 under the capacity profile, rounded up. That is about 10× the
  baseline p95, which leaves room for a cold JVM and slower shared CI runners.
- **Write p95**: about 3.5–4× the measured p95. CI runs a single cold iteration.
- **Ramp profile** (auto-scaling): this run deliberately overloads one replica until the HPA
  reacts. It keeps the error-rate and checks limits, and every endpoint must stay under a p95 of
  1000 ms.

A smoke run with the thresholds enforced passed locally:

| Endpoint | p95 | limit |
|---|---|---|
| catalog_list | 6.9 ms | 100 ms |
| product_detail | 5.7 ms | 75 ms |
| impact_report | 17.3 ms | 200 ms |
| label_history | 11.1 ms | 150 ms |

The error rate was 0 %. A deliberately broken run (unknown impact ID) made `perf/run.sh` exit
non-zero (107), so a failure fails the CI step.

### CI perf smoke

The perf smoke runs in the `containers` job of `.github/workflows/ci.yml`, after all browser
journeys:

1. It starts a fresh default Compose stack (`mysql backend`). It does not reuse the earlier
   stack, because adopting Spec V2 would change what those journeys see.
2. It runs `perf/run.sh read-mix` with the `smoke` profile (2 VUs, 30 s), then
   `perf/run.sh write-path` with 1 iteration. k6 v2.3.0 comes from `grafana/setup-k6-action@v1`.
   Thresholds are enforced.
3. It writes the per-endpoint table to the job summary and uploads `perf/results`
   (summary JSON, HTML report, logs) as artifact **`perf-smoke-<commit SHA>`**.

CI run: _to be added once the branch is pushed (link to the run and the artifact name)._

## 2. Scalability (HPA on local k3s)

### Set-up

- `deploy/k8s/base` contains:
  - a MySQL StatefulSet with a 5 Gi local-path PVC
  - the backend Deployment: requests 500m CPU / 512Mi, limits 1 CPU / 768Mi, startup, readiness
    and liveness probes on `/api/health`, and the same env as Compose including
    `SPECTRACE_DEV_EXTERNAL_AUTH_ENABLED=true`
  - Services `backend` and `frontend`
  - the frontend Deployment, whose nginx proxies `/api/` to `backend:8080`
  - an `autoscaling/v2` HPA: 1–5 replicas, CPU target 60 %, scale-up up to 2 pods per 15 s,
    scale-down window shortened to 60 s for the demo
- `overlays/local` uses locally built images (`imagePullPolicy: Never`).
- Access is only through `kubectl port-forward --address 127.0.0.1 svc/frontend 18080:80`. k6
  goes through nginx to the `backend` Service, so load spreads over all ready replicas. A
  port-forward straight to the backend would pin every request to one pod.
- **Stateless check**: the backend source has no `HttpSession`, no `@Cacheable`/`@Scheduled`
  and no static mutable maps. Identity is read from headers on every request, and all state is
  in MySQL.
- **Flyway check** (multiple replicas starting at once): a throwaway namespace started 3 backend
  replicas at once against an empty MySQL.
  - All 3 became ready with 0 restarts, about 23 s each.
  - Flyway's MySQL lock serialised the migrations, and the pods took turns:
    - pod A applied V1, V4, V7 and V10
    - pod B applied V2, V5, V8 and V11
    - pod C applied V3, V6, V9 and V12
  - `flyway_schema_history` had exactly one successful row for each of the 12 versions.
  - The namespace was then deleted.

### Results: 100-VU ramp (`VUS=100 scripts/s4-autoscale-demo.sh load`, commit `5b97e58`)

k6 ramp: 0 → 100 VUs in 1 min, 4 min hold, 30 s release, from 02:46:18Z.

| k6 | value |
|---|---|
| Requests | 101 649 |
| Throughput | 308.0 req/s (average over the run) |
| Error rate | **0.00 %** (0 / 101 649 failed checks) |
| Latency, all requests | p50 21.4 ms · p95 353.8 ms · p99 722.3 ms · max 2 813.9 ms |
| p95 by endpoint | catalog 239.7 · detail 221.0 · impact report 575.0 · label history 407.4 ms (ramp limit 1000 ms: passed) |

HPA timeline, sampled every 15 s by the script. CPU is the average over pods, as a percentage
of the 500m request. The full log is
[`hpa-timeline.txt`](s4-perf/mac-autoscale-100vus/hpa-timeline.txt).

| Time (UTC) | HPA CPU | replicas current → desired | ready pods | per-pod CPU |
|---|---|---|---|---|
| 02:46:18 | 0 % | 1 → 1 | 1/1 | 4m |
| 02:47:04 | 58 % | 1 → 1 | 1/1 | 462m |
| 02:47:19 | 92 % | **1 → 2** | 1/2 | 748m |
| 02:47:35 | 149 % | **2 → 3** | 2/3 | 861m |
| 02:48:06 | 75 % | 3 → 3 | 3/3 | 282m 854m 980m |
| 02:48:21 | 141 % | **3 → 5** | 3/5 | 343m 584m 964m |
| 02:48:52 | 86 % | 5 → 5 | **5/5** | 541m 267m 349m 542m 614m |
| 02:50:09 | 61 % | 5 → 5 | 5/5 | 400m 260m 272m 300m 411m |
| 02:51:42 | 50 % | 5 → 5 | 5/5 | 228m 174m 173m 196m 186m |
| 02:52:12 | 16 % | 5 → 5 | 5/5 | k6 finished (about 02:51:50) |
| 02:52:27 | 0 % | **5 → 4** | 5/5 | 4m each |
| 02:52:42 | 0 % | **4 → 2** | 4/4 | |
| 02:52:58 | 0 % | **2 → 1** | 2/2 | |
| 02:53:13 | 1 % | 1 → 1 | 1/1 | 4m |

HPA events ([`events.txt`](s4-perf/mac-autoscale-100vus/events.txt)): `New size: 2`, `3`, `5`
("cpu resource utilization above target"), then `4`, `2`, `1` ("All metrics below target").

Summary:

- **Scale up**: the first decision came 61 s after load started. Replicas went 1 → 5 in 2 min 3 s,
  and all 5 were ready after 2 min 34 s.
- **Scale down**: replicas were back to 1 about 85 s after k6 stopped.
- **Errors**: none, either while pods joined or while they were removed.

The p95/p99 here are much higher than the baseline. A 4-CPU VM ran 5 backend replicas (1 CPU
limit each), MySQL, nginx and the port-forward at once, so the cluster, not the HPA, limits
latency at the top of the ramp.

### Earlier rehearsal runs and the fixes they drove

| Run | Result | What changed |
|---|---|---|
| 40 VUs, `5cf8149` ([files](s4-perf/mac-autoscale-40vus-first/)) | 1 → 2 → 3 at 73–92 % CPU, back to 2 during the hold as the JIT warmed, then 1 after the load. 53 950 requests, 163.4 req/s, 5 failed (0.01 %) exactly when a pod was killed on scale-in. The backend also restarted twice at first deploy because MySQL was not ready yet. | Added a `preStop` sleep of 10 s (grace period 40 s) so a removed pod drains, and a `wait-for-mysql` init container. The default ramp is now 100 VUs. |
| 100 VUs, `4848c0d` ([files](s4-perf/mac-autoscale-100vus-before-port-fix/)) | 1 → 3 → 5 → 1, but 843 / 100 620 requests (0.84 %) were 502s. The nginx log showed `connect() ... failed (99: Address not available)`: nginx opens a new upstream connection per request, and at about 300 req/s TIME_WAIT used up the default 28 k ephemeral ports. A port-forward left over from the previous run was also still listening. | The frontend pod sets the namespaced safe sysctl `net.ipv4.ip_local_port_range=1024 65535` (nginx keeps one connection per request, so every new replica still gets traffic). The script stops `kubectl port-forward` by its own PID and fails if the port is taken. The ramp got its own thresholds. |
| 100 VUs, `5b97e58` (above) | 1 → 5 → 1, 0.00 % errors | — |

### k3d path check on the Mac (the script branch the Windows teammate uses)

The same command ran with an empty `KUBECONFIG` at commit `fceab78`:
`DURATION=60s COOLDOWN=90 scripts/s4-autoscale-demo.sh demo`.

- **Cluster**: the script found no context and ran `k3d cluster create spectrace`, giving
  k3d v5.9.0 with k3s v1.35.5+k3s1 inside Colima's Docker. The context was detected as
  `k3d-spectrace`.
- **Images**: `k3d image import` loaded the two images in 9 s, and the pods started with
  `imagePullPolicy: Never`.
- **Load**: the 2.5-minute ramp sent 42 959 requests at 286.1 req/s with **0.00 % errors**.
  Latency was p50 8.4 ms, p95 187.4 ms and p99 611.3 ms, and all ramp thresholds passed.
- **HPA**: 1 → 2 (03:01:32, 45 s after load started) → 3 → 5 (03:02:19, 1 min 32 s). Back to 1
  at 03:04:37, about 75 s after k6 ended.

Files: [`s4-perf/mac-k3d-path-check/`](s4-perf/mac-k3d-path-check/). Since that run the script
creates the cluster with `--api-port 127.0.0.1:6550`, so the Kubernetes API also stays on
loopback; that variant was checked separately (cluster Ready, server `https://127.0.0.1:6550`).
This check uses k3d on macOS, not Windows/WSL2; the teammate's run below is still needed.

## 3. How to demonstrate it in 3 minutes

Teammate setup (WSL2 + Docker Desktop + k3d, or Colima on macOS), prerequisites and clean-up are
in [`deploy/k8s/README.md`](../../deploy/k8s/README.md). Before recording, run:

```bash
scripts/s4-autoscale-demo.sh cluster && scripts/s4-autoscale-demo.sh images && scripts/s4-autoscale-demo.sh deploy
```

During the 3 minutes, use two terminals:

| Terminal 1 | Terminal 2 |
|---|---|
| `kubectl -n spectrace get hpa -w` | `DURATION=90s COOLDOWN=90 scripts/s4-autoscale-demo.sh load` |
| CPU passes 60 %, then `REPLICAS` goes 1 → 2 → 3 → 5 within about 2 min | k6 runs 3 min (1 min ramp, 90 s at 100 VUs, 30 s release) and ends with `http_req_failed 0.00%` and the thresholds ✓ |
| `REPLICAS` returns to 1 about 1.5 min after k6 ends | the results folder holds `report.html`, `hpa-timeline.log` and `events.txt` for the slides |

Suggested narration:

1. **Performance**: the k6 scripts, thresholds and CI artifact `perf-smoke-<sha>`.
2. **Scalability**: the HPA adds stateless replicas behind the Service.
3. **Maintainability**: the same thresholds run on every pipeline run.

Clean up with `scripts/s4-autoscale-demo.sh delete`, then `k3d cluster delete spectrace` or
`colima kubernetes stop`.

## Teammate Windows reproduction results

_To be filled in by the teammate after running `scripts/s4-autoscale-demo.sh demo` in WSL2 Ubuntu.
Copy the numbers from `python3 perf/summarize.py perf/results/*-autoscale-*/k6/summary.json` and
from `hpa-timeline.log` / `events.txt` in the same results folder._

| Item | Value |
|---|---|
| Date / tester | |
| Commit SHA (`git rev-parse --short HEAD`) | |
| Windows / WSL distribution | |
| Docker Desktop version, CPUs / memory given to WSL | |
| `k3d version` / k3s version (`kubectl version`) | |
| `kubectl`, `k6` versions | |

| k6 ramp | value |
|---|---|
| VUS / DURATION used | |
| Requests, throughput (req/s) | |
| Error rate | |
| p50 / p95 / p99 (ms) | |

| HPA | value |
|---|---|
| Time from load start to first scale-up | |
| Highest replica count reached | |
| Time back to 1 replica after k6 ended | |
| `SuccessfulRescale` events | |

Screenshots / notes (terminal with `kubectl get hpa -w`, k6 end summary, `report.html`):
