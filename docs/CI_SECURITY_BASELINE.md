# CI and security baseline

## Defined paths

- `.github/workflows/ci.yml` runs backend Maven verification, frontend build,
  Compose configuration validation, and container image builds.
- The `security` job contains Trivy filesystem scanning and OWASP
  Dependency-Check. It is enabled only when the repository variable
  `SECURITY_SCANS_ENABLED` is explicitly set to `true`.
- `sonar-project.properties` records source/test mapping for a later connected
  SonarQube run.

## Truthful status at this stop

| Check | Status | Reason |
|---|---|---|
| Backend local verification | PASS | `mvn -B -ntp -f backend/pom.xml verify`, Java 21, Testcontainers MySQL |
| Frontend local build | PASS | `npm ci && npm run build` |
| Compose build | PASS | `docker-compose config --quiet` and `docker-compose build` |
| GitHub workflow upload | PASS | Workflow is pushed at commit `c636bf18aa77905161b5e35e71b3ea93d6f90fb7` |
| Remote frontend job | PASS | PR-bound GitHub Actions run `33737625412` |
| Remote backend job | PASS | PR-bound GitHub Actions run `33737625412` |
| Remote Compose/container job | PASS | PR-bound GitHub Actions run `33737625412` |
| OWASP Dependency-Check | IN_PROGRESS | PR-bound security job `100591836623`; first NVD data update is still running |
| Trivy | PASS | PR-bound security job `100591836623` completed the filesystem scan |
| SonarQube | NOT_CONFIGURED | server/token/project binding not supplied |

The security variable `SECURITY_SCANS_ENABLED=true` is configured on the
repository. The current security run is bound to the pushed PR head, not an
accepted commit; independent review and merge remain pending.
