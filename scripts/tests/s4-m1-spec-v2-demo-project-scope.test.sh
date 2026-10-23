#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
demo="$repo_root/scripts/s4-m1-spec-v2-demo.sh"
tmp_root="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
tmp="$(mktemp -d "$tmp_root/spectrace-s4-scope.XXXXXX")"
cleanup() {
  [[ "$tmp" == "$tmp_root"/spectrace-s4-scope.* && -d "$tmp" && ! -L "$tmp" ]] || return 1
  rm -rf -- "$tmp"
}
trap cleanup EXIT
mkdir -p "$tmp/bin"
cat > "$tmp/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_CALL_LOG"
if [[ "$1" == volume || "$1" == network ]]; then
  kind="$1"
  [[ "${MOCK_DOCKER_FAIL_KIND:-}" != "$kind" ]] || exit 78
  if [[ "$kind" == volume ]]; then rows="${MOCK_VOLUME_ROWS:-}"; else rows="${MOCK_NETWORK_ROWS:-}"; fi
  if [[ "$2" == ls && "${3:-}" == --format ]]; then
    if [[ "$4" == *'{{.Name}}|'* ]]; then
      [[ -z "$rows" ]] || printf '%s\n' "$rows"
    elif [[ "$4" == '{{.Label "com.docker.compose.project"}}' ]]; then
      while IFS='|' read -r name owner key; do [[ -z "$owner" ]] || printf '%s\n' "$owner"; done <<< "$rows"
    else
      echo "Unsupported fake formatter: $4" >&2; exit 79
    fi
  elif [[ "$2" == ls && "${3:-}" == -q && "${4:-}" == --filter ]]; then
    project="${5#label=com.docker.compose.project=}"
    while IFS='|' read -r name owner key; do
      [[ -z "$name" || "$owner" != "$project" ]] || printf '%s\n' "$name"
    done <<< "$rows"
  else
    echo "Unsupported fake Docker resource query: $*" >&2; exit 79
  fi
elif [[ "$1" == ps ]]; then
  [[ "${MOCK_DOCKER_FAIL_PS_QUERY:-}" != "${2:-}" ]] || exit 78
  if [[ "${2:-}" == -aq ]]; then
    [[ -z "${MOCK_PROJECT_CONTAINERS:-}" ]] || printf '%s\n' "$MOCK_PROJECT_CONTAINERS"
  elif [[ "${2:-}" == --format || "${2:-}" == -a ]]; then
    [[ -z "${MOCK_RUNNING_PROJECTS:-}" ]] || printf '%s\n' "$MOCK_RUNNING_PROJECTS"
  else
    echo "Unsupported fake Docker container query: $*" >&2; exit 79
  fi
fi
exit 0
DOCKER
chmod +x "$tmp/bin/docker"
export PATH="$tmp/bin:$PATH"
export DOCKER_CALL_LOG="$tmp/docker.calls"

expect_rejected_before_docker() {
  local name="$1"
  shift
  : > "$DOCKER_CALL_LOG"
  local status=0
  if "$@" > "$tmp/output" 2>&1; then status=0; else status=$?; fi
  if [[ "$status" -ne 64 ]]; then
    cat "$tmp/output" >&2
    echo "FAIL: $name exited $status, expected 64" >&2
    exit 1
  fi
  if [[ -s "$DOCKER_CALL_LOG" ]]; then
    cat "$DOCKER_CALL_LOG" >&2
    echo "FAIL: $name called Docker before rejecting the project name" >&2
    exit 1
  fi
}

expect_rejected_before_docker "unsupported configured project" \
  env COMPOSE_PROJECT_NAME=my-review bash "$demo" reset
expect_rejected_before_docker "unsupported down target" \
  env COMPOSE_PROJECT_NAME=spectrace-s4-test bash "$demo" down my-review
expect_rejected_before_docker "unsupported purge target" \
  env COMPOSE_PROJECT_NAME=spectrace-s4-test bash "$demo" purge my-review

fail() { cat "$tmp/output" >&2; echo "FAIL: $1" >&2; echo "--- docker calls:" >&2; cat "$DOCKER_CALL_LOG" >&2; exit 1; }
run_demo() { : > "$DOCKER_CALL_LOG"; status=0; "$@" > "$tmp/output" 2>&1 < /dev/null || status=$?; }

run_demo env COMPOSE_PROJECT_NAME=spectrace-s4-test bash "$demo" reset
[[ "$status" -eq 0 ]] || fail "supported reset exited $status"
grep -q 'up -d --build --wait' "$DOCKER_CALL_LOG" || fail "supported reset did not reach compose up"

expect_no_mutation() {
  if grep -Eq '(^| )(up|stop|down)( |$)' "$DOCKER_CALL_LOG"; then fail "$1 mutated Docker resources"; fi
}
expect_refused() {
  local name="$1" expected="$2"
  shift 2
  run_demo "$@"
  [[ "$status" -eq "$expected" ]] || fail "$name exited $status, expected $expected"
  expect_no_mutation "$name"
}

# The real default names matter even when a label-filtered query would see nothing.
expect_refused 'unlabelled same-name volume' 73 env COMPOSE_PROJECT_NAME=spectrace-s4-test \
  MOCK_VOLUME_ROWS='spectrace-s4-test_spectrace_mysql_data||' bash "$demo" reset
expect_refused 'foreign same-name volume' 73 env COMPOSE_PROJECT_NAME=spectrace-s4-test \
  MOCK_VOLUME_ROWS='spectrace-s4-test_spectrace_mysql_data|another-project|spectrace_mysql_data' bash "$demo" reset
expect_refused 'unlabelled same-name network' 73 env COMPOSE_PROJECT_NAME=spectrace-s4-test \
  MOCK_NETWORK_ROWS='spectrace-s4-test_default||' bash "$demo" reset
expect_refused 'foreign same-name network' 73 env COMPOSE_PROJECT_NAME=spectrace-s4-test \
  MOCK_NETWORK_ROWS='spectrace-s4-test_default|another-project|default' bash "$demo" reset
expect_refused 'existing differently named project volume' 73 env COMPOSE_PROJECT_NAME=spectrace-s4-test \
  MOCK_VOLUME_ROWS='kept-old-volume|spectrace-s4-test|spectrace_mysql_data' bash "$demo" reset
expect_refused 'existing project containers' 73 env COMPOSE_PROJECT_NAME=spectrace-s4-test \
  MOCK_PROJECT_CONTAINERS='owned-container-id' bash "$demo" reset
for kind in volume network; do
  expect_refused "$kind discovery failure during reset" 78 env COMPOSE_PROJECT_NAME=spectrace-s4-test \
    MOCK_DOCKER_FAIL_KIND="$kind" bash "$demo" reset
done
for query in -aq --format; do
  expect_refused "container discovery failure ($query) during reset" 78 env COMPOSE_PROJECT_NAME=spectrace-s4-test \
    MOCK_DOCKER_FAIL_PS_QUERY="$query" bash "$demo" reset
done

# Only valid earlier demo projects are stopped; unrelated stacks remain untouched.
run_demo env COMPOSE_PROJECT_NAME=spectrace-s4-test \
  MOCK_RUNNING_PROJECTS=$'spectrace-s4-earlier\nunrelated-project\nspectrace-s4-test\nspectrace-s4-UPPERCASE' bash "$demo" reset
[[ "$status" -eq 0 ]] || fail "reset with an earlier project exited $status"
grep -Fq 'compose -f docker-compose.yml -p spectrace-s4-earlier stop' "$DOCKER_CALL_LOG" || fail 'earlier demo was not stopped'
[[ "$(grep -c ' stop$' "$DOCKER_CALL_LOG")" -eq 1 ]] || fail 'reset stopped an unrelated or current project'

# An environment-selected file must not change the resources after the safety check.
run_demo env COMPOSE_PROJECT_NAME=spectrace-s4-test COMPOSE_FILE=/unrelated/compose.yaml bash "$demo" reset
[[ "$status" -eq 0 ]] || fail "pinned-file reset exited $status"
grep -Fq 'compose -f docker-compose.yml up -d --build --wait' "$DOCKER_CALL_LOG" || fail 'reset did not pin the reviewed base Compose file'

# API-only commands and list never touch Compose, so an exported everyday project name must not block them.
cat > "$tmp/bin/curl" <<'CURL'
#!/usr/bin/env bash
exit 7
CURL
chmod +x "$tmp/bin/curl"
run_demo env COMPOSE_PROJECT_NAME=foodlabelflow bash "$demo" spec-v2
[[ "$status" -ne 64 ]] || fail "spec-v2 was refused because of COMPOSE_PROJECT_NAME"
run_demo env COMPOSE_PROJECT_NAME=foodlabelflow bash "$demo" list
[[ "$status" -eq 0 ]] || fail "list exited $status with an everyday COMPOSE_PROJECT_NAME"

# down keeps the database volume.
run_demo bash "$demo" down spectrace-s4-keep
[[ "$status" -eq 0 ]] || fail "down exited $status"
grep -q 'down --remove-orphans' "$DOCKER_CALL_LOG" || fail "down did not remove the project's containers"
if grep -q 'down -v' "$DOCKER_CALL_LOG"; then fail "down deleted the database volume"; fi

# purge deletes data only with the exact confirmation.
run_demo bash "$demo" purge spectrace-s4-keep
[[ "$status" -eq 65 ]] || fail "unconfirmed purge exited $status, expected 65"
if grep -q 'down' "$DOCKER_CALL_LOG"; then fail "unconfirmed purge called compose down"; fi
run_demo bash "$demo" purge spectrace-s4-keep --confirm=spectrace-s4-other
[[ "$status" -eq 65 ]] || fail "purge with another project's confirmation exited $status, expected 65"
if grep -q 'down' "$DOCKER_CALL_LOG"; then fail "mismatched purge confirmation called compose down"; fi
for rows in \
  'spectrace-s4-keep_spectrace_mysql_data||' \
  'spectrace-s4-keep_spectrace_mysql_data|another-project|spectrace_mysql_data' \
  'spectrace-s4-keep_spectrace_mysql_data|spectrace-s4-keep|another-logical-volume'; do
  expect_refused 'purge of an unowned volume' 73 env MOCK_VOLUME_ROWS="$rows" \
    bash "$demo" purge spectrace-s4-keep --confirm=spectrace-s4-keep
done
expect_refused 'purge of an unowned network' 73 env MOCK_NETWORK_ROWS='spectrace-s4-keep_default|another-project|default' \
  bash "$demo" purge spectrace-s4-keep --confirm=spectrace-s4-keep
expect_refused 'down with an unowned volume' 73 env MOCK_VOLUME_ROWS='spectrace-s4-keep_spectrace_mysql_data||' \
  bash "$demo" down spectrace-s4-keep
for kind in volume network; do
  expect_refused "$kind discovery failure during purge" 78 env MOCK_DOCKER_FAIL_KIND="$kind" \
    bash "$demo" purge spectrace-s4-keep --confirm=spectrace-s4-keep
done
run_demo env MOCK_VOLUME_ROWS='spectrace-s4-keep_spectrace_mysql_data|spectrace-s4-keep|spectrace_mysql_data' \
  MOCK_NETWORK_ROWS='spectrace-s4-keep_default|spectrace-s4-keep|default' \
  bash "$demo" purge spectrace-s4-keep --confirm=spectrace-s4-keep
[[ "$status" -eq 0 ]] || fail "confirmed purge exited $status"
grep -q -- '-p spectrace-s4-keep down -v --remove-orphans' "$DOCKER_CALL_LOG" || fail "confirmed purge did not delete the volume"

echo "PASS: actual-name collisions and foreign ownership refuse mutations; reads fail closed; base Compose is pinned; purge keeps exact confirmation"
