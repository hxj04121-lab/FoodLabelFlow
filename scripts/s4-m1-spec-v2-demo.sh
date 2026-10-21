#!/usr/bin/env bash
# SCRUM-85 demo path for the first half of the SOY story, over the public API only (no SQL).
#
#   reset           fresh Compose stack: drop the demo MySQL volume, rebuild, wait until healthy
#   spec-v2         Data Admin creates and releases Chocolate Base Spec V2 (V1 + Soy lecithin);
#                   this is the only seeded input — no adoption, finding, task or publication
#   adopt SPEC      Data Admin adopts SPEC into FormulaVersion N+1 for every current product
#   change SPEC     Change Manager records the INGREDIENT_SPEC ChangeRequest V1 -> SPEC
#   impact CR       Change Manager runs impact analysis for CR
#   all             spec-v2, adopt, change, impact; prints the summary counts
#
# Uses the local development header identities, so only run it against a loopback demo stack.
set -euo pipefail

API="${SPECTRACE_API:-http://127.0.0.1:8080}"
DATA_ADMIN="dev-external-admin"
CHANGE_MANAGER="dev-external-change-manager"
RULE_SET="ruleset_us_falcpa_demo_v1"
# A separate project, so a reset never drops the everyday development volume.
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-spectrace-s4-demo}"
cd "$(dirname "$0")/.."

call() {
  local subject="$1" method="$2" path="$3" body="${4:-}"
  local args=(-sS --fail-with-body -X "$method" "$API$path"
    -H 'Content-Type: application/json' -H 'Accept: application/json'
    -H 'X-Auth-Provider: DEV_EXTERNAL' -H "X-External-Subject: $subject")
  if [[ -n "$body" ]]; then args+=(--data "$body"); fi
  curl "${args[@]}"
}

field() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

spec_v2() {
  local v1 body id
  v1="$(call "$DATA_ADMIN" GET /api/catalog/specifications/spec_chocolate_v1)"
  body="$(python3 -c '
import datetime, json, sys
v1 = json.load(sys.stdin)
components = [{"ingredientId": c["ingredient_id"], "rawPhrase": c["raw_phrase"], "matchRule": c["match_rule"]}
              for c in v1["components"]]
components.append({"ingredientId": "ing_soy_lecithin", "rawPhrase": "Soy lecithin",
                   "matchRule": "Supplier Spec V2 declaration"})
print(json.dumps({"materialId": "mat_chocolate_base", "provenanceId": "prov_project_seed",
                  "effectiveDate": datetime.datetime.now(datetime.timezone.utc).date().isoformat(),
                  "components": components}))' <<<"$v1")"
  id="$(call "$DATA_ADMIN" POST /api/catalog/specifications "$body" | field specification_version_id)"
  call "$DATA_ADMIN" POST "/api/catalog/specifications/$id/release" >/dev/null
  echo "$id"
}

adopt() {
  call "$DATA_ADMIN" POST "/api/catalog/specifications/$1/formula-adoptions"
}

change() {
  call "$CHANGE_MANAGER" POST /api/v1/change-requests "$(printf '{"changeType":"INGREDIENT_SPEC",
    "supplierMaterialId":"mat_chocolate_base","previousSpecificationVersionId":"spec_chocolate_v1",
    "targetSpecificationVersionId":"%s","description":"Chocolate Base Spec V2 adds Soy Lecithin"}' "$1")" \
    | field changeRequestId
}

impact() {
  call "$CHANGE_MANAGER" POST "/api/v1/change-requests/$1/impact-analyses" "{\"ruleSetVersionId\":\"$RULE_SET\"}"
}

summary() {
  python3 -c 'import json,sys; d=json.load(sys.stdin); print(" ".join(f"{k}={d[k]}" for k in sys.argv[1:]))' "$@"
}

case "${1:-}" in
  reset)
    docker compose down -v --remove-orphans
    docker compose up -d --build --wait
    ;;
  spec-v2) spec_v2 ;;
  adopt) adopt "${2:?specification id}" ;;
  change) change "${2:?specification id}" ;;
  impact) impact "${2:?change request id}" ;;
  all)
    spec="$(spec_v2)"; echo "Spec V2 released: $spec"
    adopt "$spec" | summary candidateCount adoptedCount skippedCount failedCount
    cr="$(change "$spec")"; echo "ChangeRequest: $cr"
    impact "$cr" | summary impactAnalysisId relevantProductCount noActionCount reviewRequiredCount
    ;;
  *) sed -n '2,12p' "$0"; exit 64 ;;
esac
