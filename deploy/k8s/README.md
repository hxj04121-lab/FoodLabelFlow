# SpecTrace on local k3s with a backend HorizontalPodAutoscaler

S4 scalability demonstration, run on a laptop. The hosted course site keeps its own Compose
deployment and is not part of this.

- `base/`: MySQL StatefulSet with a PVC, a stateless backend Deployment scaled by an
  `autoscaling/v2` HPA (CPU target 60 % of the 500m request, 1–5 replicas, scale-down window
  shortened to 60 s for the demo), and the nginx frontend that proxies `/api/` to the `backend`
  Service, which spreads requests over every ready replica.
- `overlays/local/`: locally built images only (`imagePullPolicy: Never`).
- The UI and API are reached only through `kubectl port-forward` on 127.0.0.1, so the
  development header identity never leaves the machine.

`scripts/s4-autoscale-demo.sh` picks the platform from the current kubectl context:
`colima` on macOS (Colima k3s shares the Docker daemon, so built images are visible directly)
or `k3d-*` on Windows (k3s nodes run as Docker containers, so images are copied with
`k3d image import`). Any other context is refused.

## Prerequisites

### Windows: WSL2 + Docker Desktop + k3d

Run every command inside **WSL2 Ubuntu**, never in PowerShell or Git Bash.

1. Install WSL2 with Ubuntu (`wsl --install -d Ubuntu` in an administrator PowerShell) and
   Docker Desktop with the WSL2 backend. In Docker Desktop → Settings → Resources →
   WSL integration, enable your Ubuntu distribution.
2. Give Docker at least **4 CPUs / 8 GB**. With the WSL2 backend, Docker uses the WSL VM's
   limits, set in `%UserProfile%\.wslconfig`, then `wsl --shutdown` and restart Docker Desktop:
   ```ini
   [wsl2]
   processors=4
   memory=8GB
   ```
3. In Ubuntu, clone the repository into the Linux file system (`~/`, not `/mnt/c/...`).
   `.gitattributes` keeps `*.sh`, `*.js` and `*.yaml` on LF. A checkout made earlier with
   Git for Windows can fail with `$'\r': command not found`; clone again inside WSL.
4. Install the tools in Ubuntu (`python3` and `openssl` ship with Ubuntu):
   ```bash
   sudo apt-get update && sudo apt-get install -y curl git python3 openssl
   ```
   ```bash
   curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" && sudo install -m 0755 kubectl /usr/local/bin/kubectl && rm kubectl
   ```
   ```bash
   curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
   ```
   For k6, follow the Debian/Ubuntu steps at <https://grafana.com/docs/k6/latest/set-up/install-k6/>.
5. Check:
   ```bash
   docker info --format '{{.NCPU}} CPUs, {{.MemTotal}} bytes'
   ```
   ```bash
   kubectl version --client && k3d version && k6 version
   ```

### macOS: Colima

```bash
brew install colima kubectl k6
```

Colima needs at least 4 CPUs / 8 GB (`colima start --cpu 4 --memory 8 --kubernetes`).

## Run the whole demonstration with one command

```bash
scripts/s4-autoscale-demo.sh demo
```

This creates the cluster if there is no kubectl context (`k3d cluster create spectrace`, or
k3s inside Colima), builds and loads the images, deploys, waits until metrics-server reports
backend CPU, prepares Spec V2 → adoption → ChangeRequest → impact analysis through the API,
then port-forwards the frontend to `127.0.0.1:18080` and runs the k6 `ramp` profile
(0 → 100 VUs in 1 min, hold 4 min, release 30 s) while recording the HPA, the backend pods and
`kubectl top pods` every 15 s, plus 150 s of cool-down. On the Mac rehearsal it took 8 min 46 s
with cached image layers; the first image build adds several minutes.

Results land in `perf/results/<UTC>-autoscale-<platform>-<sha>/`: `hpa-timeline.log`,
`events.txt` (the HPA `SuccessfulRescale` events), `hpa-describe.txt`, `cluster.txt`,
`k6/summary.json`, `k6/report.html` (open it in a browser) and `k6/k6.log`.
Summarise k6 numbers with:

```bash
python3 perf/summarize.py perf/results/*-autoscale-*/k6/summary.json
```

Single steps: `cluster`, `images`, `deploy`, `load`, `watch`, `status`, `delete`.
`FORWARD_PORT`, `VUS`, `DURATION` and `COOLDOWN` override the defaults.

## Three-minute live demonstration (two terminals)

Before recording, run `scripts/s4-autoscale-demo.sh cluster`, `images` and `deploy`, and
check `scripts/s4-autoscale-demo.sh status` shows one backend pod and an HPA target such as
`cpu: 2%/60%` (not `<unknown>`).

Terminal 1 — watch the autoscaler:

```bash
kubectl -n spectrace get hpa -w
```

Terminal 2 — drive load (3 minutes of k6: 1 min ramp, 90 s at 100 VUs, 30 s release; then
90 s more of HPA recording):

```bash
DURATION=90s COOLDOWN=90 scripts/s4-autoscale-demo.sh load
```

What to point out (times from the Mac rehearsal):

1. Terminal 1: about 1 min in, CPU passes 60 % of the request and `REPLICAS` goes 1 → 2 → 3,
   then 5 about 2 min in (the scale-up policy adds at most 2 pods per 15 s, capped at 5).
2. In terminal 2, k6 reports `http_req_failed 0.00%` at the end: new pods receive traffic only
   once `/api/health` is ready, and a removed pod keeps serving for 10 s (`preStop`) while the
   Service drops it.
3. When k6 stops, CPU falls and the replicas return to 1 within about 1.5 min (60 s
   scale-down window).
4. Open `k6/report.html` from the results folder for the latency and throughput charts.

## Clean up

```bash
scripts/s4-autoscale-demo.sh delete
```

This removes the `spectrace` namespace and its MySQL volume. Then remove the cluster:

```bash
k3d cluster delete spectrace
```

On macOS, `colima kubernetes stop` stops k3s and keeps the Colima Docker runtime.

## Troubleshooting

| Symptom | Fix |
|---|---|
| HPA target `<unknown>` | metrics-server needs about a minute after the cluster starts; re-run `status` |
| `ErrImageNeverPull` | run `scripts/s4-autoscale-demo.sh images` again (on k3d this imports the images) |
| `context '...' is neither colima nor k3d-*` | `kubectl config use-context k3d-spectrace` (Docker Desktop's own `docker-desktop` context is not used) |
| `$'\r': command not found` | the checkout has CRLF; clone again inside WSL |
| port 18080 in use | `FORWARD_PORT=18081 scripts/s4-autoscale-demo.sh load` |
| No scale-up | fewer CPUs than the backend can use; raise `VUS` (for example `VUS=80`) |

Recorded results: [`docs/evidence/S4-performance-and-autoscaling.md`](../../docs/evidence/S4-performance-and-autoscaling.md).
