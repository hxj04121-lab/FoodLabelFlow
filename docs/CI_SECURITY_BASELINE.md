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
| OWASP Dependency-Check | DEFINED_NOT_RUN | no real CI run yet |
| Trivy | DEFINED_NOT_RUN | no real CI run yet |
| SonarQube | NOT_CONFIGURED | server/token/project binding not supplied |

No security scan result is claimed until a real run is bound to an accepted
commit.
