#!/usr/bin/env bash
# Prepares S4 demo data through the public API and runs a k6 script against it.
#
#   perf/run.sh read-mix   [k6 args...]   catalog / product / impact report / label history mix
#   perf/run.sh write-path [k6 args...]   timed batch adoption + impact run (writes data)
#
# Target stack:
#   default            starts a new isolated Compose demo stack (scripts/s4-m1-spec-v2-demo.sh fresh)
#   SPECTRACE_API=URL  reuse a running disposable stack, e.g. http://127.0.0.1:8080 (CI) or a
#                      kubectl port-forward; the Spec V2 / adopt / change / impact chain is added to it
#   IMPACT_ANALYSIS_ID with SPECTRACE_API, skip all writes and read this existing analysis (read-mix)
#
# Results go to perf/results/<UTC time>-<script>-<short SHA>/: summary.json (k6 summary export),
# report.html (k6 web dashboard export), k6.log and context.env (IDs and environment).
set -euo pipefail
cd "$(dirname "$0")/.."

script="${1:?read-mix or write-path}"; shift
[[ "$script" == read-mix || "$script" == write-path ]] || { echo "unknown script $script" >&2; exit 64; }
command -v k6 >/dev/null || { echo "k6 is required (brew install k6, or grafana/setup-k6-action in CI)" >&2; exit 69; }

demo=scripts/s4-m1-spec-v2-demo.sh
field() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

if [[ -z "${SPECTRACE_API:-}" ]]; then
  log="$(mktemp)"
  "$demo" fresh | tee "$log"
  spec="$(sed -n 's/^Spec V2 released: //p' "$log")"
  export SPECTRACE_API="http://127.0.0.1:${BACKEND_HOST_PORT:-8080}"
  rm -f "$log"
fi
export SPECTRACE_API="${SPECTRACE_API%/}"

if [[ "$script" == read-mix && -z "${IMPACT_ANALYSIS_ID:-}" ]]; then
  spec="${spec:-$("$demo" spec-v2)}"
  adoption="$("$demo" adopt "$spec")"
  echo "Adoption: $(field adoptedCount <<<"$adoption") adopted, $(field failedCount <<<"$adoption") failed"
  cr="$("$demo" change "$spec")"
  impact="$("$demo" impact "$cr")"
  IMPACT_ANALYSIS_ID="$(field impactAnalysisId <<<"$impact")"
  echo "Impact $IMPACT_ANALYSIS_ID: $(field relevantProductCount <<<"$impact") relevant," \
    "$(field noActionCount <<<"$impact") NO_ACTION, $(field reviewRequiredCount <<<"$impact") REVIEW_REQUIRED"
fi
export IMPACT_ANALYSIS_ID="${IMPACT_ANALYSIS_ID:-}"

sha="$(git rev-parse --short HEAD 2>/dev/null || echo nogit)"
out="${PERF_RESULTS_DIR:-perf/results/$(date -u +%Y%m%dT%H%M%SZ)-$script-$sha}"
mkdir -p "$out"
{
  echo "commit=$(git rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "script=$script profile=${PROFILE:-default} base_url=$SPECTRACE_API"
  echo "impact_analysis_id=$IMPACT_ANALYSIS_ID"
  echo "k6=$(k6 version | head -1)"
  echo "host=$(uname -sm) started=$(date -u +%FT%TZ)"
} > "$out/context.env"

# The dashboard export needs no listening port beyond the run; keep it on loopback.
K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_HOST=127.0.0.1 K6_WEB_DASHBOARD_OPEN=false \
K6_WEB_DASHBOARD_EXPORT="$out/report.html" \
  k6 run --quiet --summary-export "$out/summary.json" -e BASE_URL="$SPECTRACE_API" "$@" "perf/$script.js" 2>&1 | tee "$out/k6.log"
status="${PIPESTATUS[0]}"
echo "Results: $out"
exit "$status"
