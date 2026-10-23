#!/usr/bin/env bash
# SCRUM-85 demo path for the first half of the SOY story, over the public API only (no SQL).
#
#   reset           stop earlier demo stacks (kept for inspection), then start a new isolated one
#   fresh           reset, then spec-v2: the demo starts from released Spec V2 and nothing else
#   fresh-ui        reset to the untouched baseline; create and release Spec V2 from the browser
#   spec-v2         Data Admin creates and releases Chocolate Base Spec V2 (V1 + Soy lecithin);
#                   this is the only seeded input — no adoption, finding, task or publication
#   adopt SPEC      Data Admin adopts SPEC into FormulaVersion N+1 for every current product
#   change SPEC     Change Manager records the INGREDIENT_SPEC ChangeRequest V1 -> SPEC
#   impact CR       Change Manager runs impact analysis for CR
#   all             spec-v2, adopt, change, impact; prints the summary counts
#   list            show earlier demo projects (spectrace-s4-*), their containers and data volume
#   down PROJECT    remove one earlier project's containers and network; its database volume is kept
#   purge PROJECT   delete that project's database volume too; asks you to retype the project name
#                   (non-interactive: purge PROJECT --confirm=PROJECT)
#
# Uses the local development header identities, so only run it against a loopback demo stack.
# reset publishes MySQL, API and web on 127.0.0.1 only (ports from MYSQL_HOST_PORT,
# BACKEND_HOST_PORT and FRONTEND_HOST_PORT; defaults 3307, 8080 and 5173).
set -euo pipefail

API="${SPECTRACE_API:-http://127.0.0.1:${BACKEND_HOST_PORT:-8080}}"
DATA_ADMIN="dev-external-admin"
CHANGE_MANAGER="dev-external-change-manager"
RULE_SET="ruleset_us_falcpa_demo_v1"
# Each reset gets a new project by default. Existing project names are never
# reused because doing so could attach the demo to an earlier database volume.
if [[ -z "${COMPOSE_PROJECT_NAME:-}" ]]; then
  COMPOSE_PROJECT_NAME="spectrace-s4-$(date -u +%Y%m%d%H%M%S)-$$-${RANDOM}"
fi
export COMPOSE_PROJECT_NAME

demo_project_name_supported() {
  [[ "$1" =~ ^spectrace-s4-[a-z0-9][a-z0-9_-]*$ ]]
}

require_demo_project_name() {
  if ! demo_project_name_supported "$1"; then
    echo "only spectrace-s4-* demo projects are supported; see list" >&2
    exit 64
  fi
}

# Only Compose-touching commands check names: reset checks COMPOSE_PROJECT_NAME (in
# assert_fresh_project) and down/purge check their argument, all before any Docker call.
# API-only commands and list ignore COMPOSE_PROJECT_NAME, so an exported everyday name is fine.
DEMO_PREFIX="${DEMO_PREFIX:-FOOD-S4-$(date -u +%Y%m%d%H%M%S)-$$-${RANDOM}}"
export DEMO_PREFIX
if [[ ! "$DEMO_PREFIX" =~ ^[A-Za-z0-9][A-Za-z0-9_-]*$ ]]; then
  echo "DEMO_PREFIX must contain only letters, digits, underscores or hyphens and start with a letter or digit" >&2
  exit 64
fi
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
                   "matchRule": __import__("os").environ["DEMO_PREFIX"] + " Supplier Spec V2 declaration"})
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
    "targetSpecificationVersionId":"%s","description":"%s: Chocolate Base Spec V2 adds Soy Lecithin"}' "$1" "$DEMO_PREFIX")" \
    | field changeRequestId
}

impact() {
  call "$CHANGE_MANAGER" POST "/api/v1/change-requests/$1/impact-analyses" "{\"ruleSetVersionId\":\"$RULE_SET\"}"
}

compose() {
  if docker compose version >/dev/null 2>&1; then docker compose "$@"; else docker-compose "$@"; fi
}

assert_fresh_project() {
  local containers volumes networks
  require_demo_project_name "$COMPOSE_PROJECT_NAME"
  containers="$(docker ps -aq --filter "label=com.docker.compose.project=$COMPOSE_PROJECT_NAME")"
  volumes="$(docker volume ls -q --filter "label=com.docker.compose.project=$COMPOSE_PROJECT_NAME")"
  networks="$(docker network ls -q --filter "label=com.docker.compose.project=$COMPOSE_PROJECT_NAME")"
  if [[ -n "$containers$volumes$networks" ]]; then
    echo "Compose project '$COMPOSE_PROJECT_NAME' already has containers, volumes or networks; choose a new project name. Existing data was not changed." >&2
    exit 73
  fi
}

# A project still counts after `down` while its data volume exists.
demo_projects() {
  { docker ps -a --format '{{.Label "com.docker.compose.project"}}'
    docker volume ls --format '{{.Label "com.docker.compose.project"}}'; } | grep '^spectrace-s4-' | sort -u || true
}

# Earlier stacks hold the same loopback ports. Stopping keeps their containers and
# volume for inspection; only `purge PROJECT` deletes data.
stop_previous() {
  local project
  for project in $(docker ps --format '{{.Label "com.docker.compose.project"}}' | grep '^spectrace-s4-' | sort -u); do
    [[ "$project" == "$COMPOSE_PROJECT_NAME" ]] && continue
    echo "Stopping earlier demo project $project (containers and volume are kept)" >&2
    compose -p "$project" stop >&2
  done
}

reset() {
  local port
  for port in MYSQL_HOST_PORT BACKEND_HOST_PORT FRONTEND_HOST_PORT; do
    if [[ -n "${!port:-}" && ! "${!port}" =~ ^[0-9]{1,5}$ ]]; then
      echo "$port must be a bare port number" >&2; exit 64
    fi
  done
  export MYSQL_HOST_PORT="127.0.0.1:${MYSQL_HOST_PORT:-3307}" BACKEND_HOST_PORT="127.0.0.1:${BACKEND_HOST_PORT:-8080}" \
    FRONTEND_HOST_PORT="127.0.0.1:${FRONTEND_HOST_PORT:-5173}"
  assert_fresh_project
  stop_previous
  compose up -d --build --wait
}

summary() {
  python3 -c 'import json,sys; d=json.load(sys.stdin); print(" ".join(f"{k}={d[k]}" for k in sys.argv[1:]))' "$@"
}

case "${1:-}" in
  reset) reset ;;
  fresh)
    reset
    spec="$(spec_v2)"; echo "Spec V2 released: $spec"
    echo "Compose project: $COMPOSE_PROJECT_NAME"
    echo "Demo prefix: $DEMO_PREFIX"
    echo "Demo ready: http://$FRONTEND_HOST_PORT"
    ;;
  fresh-ui)
    reset
    echo "Fresh browser baseline ready; no S4 specification or business records were written by this script."
    echo "Compose project: $COMPOSE_PROJECT_NAME"
    echo "Demo prefix: $DEMO_PREFIX"
    echo "Demo ready: http://$FRONTEND_HOST_PORT"
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
  list)
    for project in $(demo_projects); do
      containers="$(docker ps -a --filter "label=com.docker.compose.project=$project" --format '{{.State}}' | sort | uniq -c | xargs)"
      volumes="$(docker volume ls -q --filter "label=com.docker.compose.project=$project" | xargs)"
      echo "$project containers: ${containers:-none} · data volume: ${volumes:-none}"
    done
    ;;
  down)
    project="${2:?project name from list}"; require_demo_project_name "$project"
    compose -p "$project" down --remove-orphans
    echo "Removed the containers of $project; its database volume is kept. Delete it with: $0 purge $project" >&2
    ;;
  purge)
    project="${2:?project name from list}"; require_demo_project_name "$project"
    if [[ "${3:-}" != "--confirm=$project" ]]; then
      if [[ ! -t 0 ]]; then
        echo "purge deletes the database volume of $project. Re-run with --confirm=$project to proceed." >&2; exit 65
      fi
      read -r -p "This permanently deletes the database volume of $project. Type the project name to confirm: " answer
      if [[ "$answer" != "$project" ]]; then echo "Not confirmed; nothing was deleted." >&2; exit 65; fi
    fi
    compose -p "$project" down -v --remove-orphans
    ;;
  *) sed -n '2,21p' "$0"; exit 64 ;;
esac
