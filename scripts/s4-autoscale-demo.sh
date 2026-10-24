#!/usr/bin/env bash
# S4 scalability demo: SpecTrace on local k3s with a CPU HorizontalPodAutoscaler.
# One set of commands for macOS (Colima k3s) and Windows (WSL2 Ubuntu + Docker Desktop + k3d).
#
#   cluster   use the current kubectl context if it is `colima` or `k3d-*`; with no context,
#             create one (k3d cluster create spectrace, or Colima with k3s)
#   images    build spectrace-backend:local and spectrace-frontend:local; on k3d also
#             import them into the cluster (k3d image import)
#   deploy    create the MySQL Secret if missing, apply deploy/k8s/overlays/local,
#             wait for MySQL, backend, frontend and metrics-server
#   load      port-forward the frontend to 127.0.0.1:$FORWARD_PORT, run the k6 read-mix ramp
#             through it (nginx -> backend Service) and record the HPA and pods every 15 s
#   watch     print the HPA and backend pods every 15 s
#   demo      cluster + images + deploy + load
#   status    kubectl get hpa,deploy,pods,svc
#   delete    delete the spectrace namespace including the MySQL volume (asks first;
#             non-interactive: delete --confirm)
#
# The UI and API are reached only through kubectl port-forward on 127.0.0.1, so the development
# header identity never leaves this machine. Open http://127.0.0.1:$FORWARD_PORT while `load`
# runs, or run `kubectl -n spectrace port-forward svc/frontend 18080:80` yourself.
#
#   FORWARD_PORT   local port for the frontend (default 18080)
#   VUS, DURATION  k6 ramp peak virtual users (default 100) and hold time (default 4m)
#   COOLDOWN       seconds to keep recording after k6 ends, for scale-down (default 150)
set -euo pipefail
cd "$(dirname "$0")/.."

NS=spectrace
FORWARD_PORT="${FORWARD_PORT:-18080}"
BASE_URL="http://127.0.0.1:$FORWARD_PORT"
k() { kubectl -n "$NS" "$@"; }

# Sets PLATFORM (colima | k3d) and K3D_CLUSTER from the current kubectl context.
detect() {
  local ctx
  ctx="$(kubectl config current-context 2>/dev/null || true)"
  case "$ctx" in
    colima) PLATFORM=colima ;;
    k3d-*) PLATFORM=k3d; K3D_CLUSTER="${ctx#k3d-}" ;;
    "") PLATFORM="" ;;
    *)
      echo "kubectl context '$ctx' is neither colima nor k3d-*; this demo only deploys to a local cluster." >&2
      echo "Switch with: kubectl config use-context k3d-spectrace (or colima)" >&2
      exit 64 ;;
  esac
}

cluster() {
  detect
  if [[ -z "$PLATFORM" ]]; then
    if command -v k3d >/dev/null; then
      k3d cluster create spectrace --wait
    elif command -v colima >/dev/null; then
      # A running Colima VM gains k3s without a restart; a stopped one starts with it.
      if colima status >/dev/null 2>&1; then colima kubernetes start; else colima start --kubernetes; fi
    else
      echo "Install k3d (Windows/WSL2) or Colima (macOS) first; see deploy/k8s/README.md" >&2; exit 69
    fi
    detect
  fi
  kubectl get nodes -o wide
  echo "Platform: $PLATFORM ($(kubectl config current-context))"
}

images() {
  detect
  [[ -n "$PLATFORM" ]] || { echo "No kubectl context; run: $0 cluster" >&2; exit 64; }
  docker build -t spectrace-backend:local backend
  docker build -t spectrace-frontend:local frontend
  # Colima's k3s uses the same Docker daemon; k3d nodes are containers with their own containerd.
  if [[ "$PLATFORM" == k3d ]]; then
    k3d image import spectrace-backend:local spectrace-frontend:local -c "$K3D_CLUSTER"
  fi
}

deploy() {
  detect
  [[ -n "$PLATFORM" ]] || { echo "No kubectl context; run: $0 cluster" >&2; exit 64; }
  kubectl apply -f deploy/k8s/base/namespace.yaml
  if ! k get secret spectrace-mysql >/dev/null 2>&1; then
    # Random per cluster and never written to the repository.
    k create secret generic spectrace-mysql --from-literal=password="$(openssl rand -hex 16)"
  fi
  kubectl apply -k deploy/k8s/overlays/local
  k rollout status statefulset/mysql --timeout=300s
  k rollout status deployment/backend --timeout=300s
  k rollout status deployment/frontend --timeout=180s
  echo "Waiting for backend pod metrics (the HPA shows <unknown> until metrics-server reports)..."
  for _ in $(seq 1 60); do k top pods -l app=backend >/dev/null 2>&1 && break; sleep 5; done
  k get hpa,pods -o wide
}

watch_once() {
  echo "=== $(date -u +%FT%TZ)"
  k get hpa backend -o jsonpath='hpa: cpu={.status.currentMetrics[0].resource.current.averageUtilization}%/60% current={.status.currentReplicas} desired={.status.desiredReplicas}{"\n"}' 2>&1
  k get pods -l app=backend --no-headers 2>&1
  k top pods -l app=backend --no-headers 2>/dev/null || true
}

watch_loop() {
  while true; do watch_once; sleep 15; done
}

load() {
  local out pf_pid="" watch_pid=""
  detect
  [[ -n "$PLATFORM" ]] || { echo "No kubectl context; run: $0 cluster" >&2; exit 64; }
  out="perf/results/$(date -u +%Y%m%dT%H%M%SZ)-autoscale-$PLATFORM-$(git rev-parse --short HEAD)"
  mkdir -p "$out"
  cleanup() {
    [[ -n "$watch_pid" ]] && kill "$watch_pid" 2>/dev/null || true
    [[ -n "$pf_pid" ]] && kill "$pf_pid" 2>/dev/null || true
  }
  trap cleanup EXIT
  # kubectl itself, not the k() wrapper, so that $! is the process cleanup has to stop.
  kubectl -n "$NS" port-forward --address 127.0.0.1 svc/frontend "$FORWARD_PORT:80" >"$out/port-forward.log" 2>&1 &
  pf_pid=$!
  for _ in $(seq 1 30); do curl -fsS "$BASE_URL/api/health" >/dev/null 2>&1 && break; sleep 1; done
  kill -0 "$pf_pid" 2>/dev/null || { cat "$out/port-forward.log" >&2; echo "port-forward failed; is $FORWARD_PORT in use?" >&2; exit 69; }
  curl -fsS "$BASE_URL/api/health" >/dev/null
  { kubectl version 2>/dev/null; kubectl get nodes -o wide; } > "$out/cluster.txt"
  watch_loop > "$out/hpa-timeline.log" 2>&1 &
  watch_pid=$!
  PERF_RESULTS_DIR="$out/k6" SPECTRACE_API="$BASE_URL" PROFILE=ramp \
    perf/run.sh read-mix || echo "k6 finished with thresholds failing; see $out/k6" >&2
  echo "Recording ${COOLDOWN:-150}s more for the HPA to scale back down..."
  sleep "${COOLDOWN:-150}"
  cleanup; trap - EXIT
  k describe hpa backend > "$out/hpa-describe.txt" 2>&1 || true
  k get events --sort-by=.lastTimestamp > "$out/events.txt" 2>&1 || true
  echo "HPA timeline: $out/hpa-timeline.log"
  grep -E 'SuccessfulRescale' "$out/events.txt" || true
}

delete_namespace() {
  if [[ "${1:-}" != --confirm ]]; then
    [[ -t 0 ]] || { echo "delete removes namespace $NS and its MySQL volume; re-run with --confirm" >&2; exit 65; }
    read -r -p "Delete namespace $NS and its MySQL data? Type $NS to confirm: " answer
    [[ "$answer" == "$NS" ]] || { echo "Not confirmed; nothing was deleted." >&2; exit 65; }
  fi
  detect
  kubectl delete namespace "$NS"
}

case "${1:-}" in
  cluster) cluster ;;
  images) images ;;
  deploy) deploy ;;
  load) load ;;
  watch) detect; watch_loop ;;
  demo) cluster; images; deploy; load ;;
  status) detect; k get hpa,deploy,pods,svc -o wide ;;
  delete) delete_namespace "${2:-}" ;;
  *) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 64 ;;
esac
