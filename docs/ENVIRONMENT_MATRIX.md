# Stage 0 environment matrix

Generated: 2026-09-03 (Asia/Shanghai)

| Capability | Local observation | Stage 0 status | Evidence / note |
|---|---|---|---|
| OS / architecture | macOS, arm64 | AVAILABLE | local host |
| Git | 2.39.5 | AVAILABLE | local host |
| GitHub CLI | 2.99.0, authenticated as `hxj04121-lab` | AVAILABLE | authenticated local CLI; token not recorded |
| Java | 21.0.12.1 installed at `/opt/homebrew/opt/openjdk@21` | AVAILABLE | selected for baseline |
| Maven | 3.9.16 | AVAILABLE | backend build |
| Node / npm | Node 23.11.0 / npm 10.9.2 | AVAILABLE | frontend build; Docker uses Node 22 |
| MySQL client | 26.7.0 | AVAILABLE | local runtime audit |
| MySQL server | 8.4.11 in Colima Docker | PASS | DB0 evidence |
| Docker | Docker CLI 29.3.1 with Colima context | AVAILABLE | local image/compose smoke |
| Compose | `docker-compose` 5.1.1 | AVAILABLE | CLI plugin not installed; legacy command used locally |
| PowerShell | 7.6.5 | AVAILABLE | executed package self-review |
| Jira | authenticated UI at `https://hxj04121.atlassian.net`, project SCRUM/TEAM 16 | PARTIAL_SYNC | SCRUM-6 through SCRUM-11 created; M2–M5 Unassigned; see `JIRA_DELTA.md` |
| Shared staging | URL/credentials not configured | NOT_CONFIGURED | no remote staging claim |
| SonarQube | server/token not configured | NOT_CONFIGURED | path documented; no result claimed |
| Independent developer review | not yet available | HUMAN_GATE | required before merge |
