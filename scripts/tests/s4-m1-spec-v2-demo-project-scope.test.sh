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

: > "$DOCKER_CALL_LOG"
if ! env COMPOSE_PROJECT_NAME=spectrace-s4-test bash "$demo" reset > "$tmp/output" 2>&1; then
  cat "$tmp/output" >&2
  echo "FAIL: supported project name should reach the Docker stub" >&2
  exit 1
fi
if [[ ! -s "$DOCKER_CALL_LOG" ]]; then
  echo "FAIL: supported project name did not reach the Docker stub" >&2
  exit 1
fi

echo "PASS: unsupported project names are rejected before Docker; supported spectrace-s4-* names remain accepted"
