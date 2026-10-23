#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
demo="$repo_root/scripts/s4-m1-spec-v2-demo.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"
cat > "$tmp/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_CALL_LOG"
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
grep -q 'compose up -d --build --wait' "$DOCKER_CALL_LOG" || fail "supported reset did not reach compose up"

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
run_demo bash "$demo" purge spectrace-s4-keep --confirm=spectrace-s4-keep
[[ "$status" -eq 0 ]] || fail "confirmed purge exited $status"
grep -q -- '-p spectrace-s4-keep down -v --remove-orphans' "$DOCKER_CALL_LOG" || fail "confirmed purge did not delete the volume"

echo "PASS: names are checked only where Compose is used; down keeps data; purge needs the exact confirmation"
