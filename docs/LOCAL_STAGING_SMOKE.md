# Local staging smoke

Status: PASS; final health recheck at 2026-09-03T17:32:23+08:00
(Asia/Shanghai).

The original build smoke used:

1. `docker-compose up -d --build`
2. `curl --fail --silent --show-error http://localhost:8080/api/health`
3. `curl --fail --silent --show-error http://localhost:5173`
4. `docker-compose ps`
5. Open `http://localhost:5173/` in the Codex In-app Browser and inspect the
   visible DOM.

The final recheck repeated Compose configuration, API health, frontend HTML,
and service status against the running containers. Observed API response:
`{"status":"ok","database":"ok"}`. MySQL and backend were healthy and
frontend was running. The browser DOM showed the SpecTrace platform baseline
and both Application/Database values as `ok`. Detailed machine-readable
evidence is in `.stage0/evidence/LOCAL_STAGING_SMOKE.json`.
No shared staging result is implied by this local check.
