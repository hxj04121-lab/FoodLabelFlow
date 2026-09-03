# CI and security baseline

## Defined paths

- `.github/workflows/ci.yml` runs backend Maven verification, frontend build,
  Compose configuration validation, and container image builds.
- The `security` job contains Trivy filesystem scanning and OWASP
  Dependency-Check. It is enabled only when the repository variable
  `SECURITY_SCANS_ENABLED` is explicitly set to `true`.
- The backend now uses Spring Boot `3.5.16`, which centrally resolves the
  Spring Framework, Jackson, and Tomcat dependency line.
- `sonar-project.properties` records source/test mapping, and the workflow has
  a SonarQube job gated by `SONARQUBE_ENABLED=true`.

## Truthful status at this stop

| Check | Status | Reason |
|---|---|---|
| Backend compile/package | PASS | `mvn -B -ntp -f backend/pom.xml -DskipTests package` |
| Backend full verification | BLOCKED_LOCALLY | Testcontainers integration test needs a running Docker environment; non-container CI remains the authoritative check |
| Resolved security dependencies | PASS | Boot `3.5.16` resolves Spring `6.2.19`, Jackson `2.21.4`, and the explicitly patched Tomcat `10.1.59` |
| OWASP Dependency-Check workflow | PENDING_REMOTE | Next PR run will use scanner `12.2.2`, CVSS threshold `7`, an optional `NVD_API_KEY`, and a cached NVD data directory |
| SonarQube workflow | READY_NOT_ENABLED | Enable only after adding `SONAR_HOST_URL` repository variable and `SONAR_TOKEN` repository secret |
| Shared staging | DEFERRED | Explicitly deferred by the user |
| Jira assignment | USER_MANAGED | Explicitly left for the user |

The security variable `SECURITY_SCANS_ENABLED=true` is configured on the
repository. SonarQube is intentionally fail-closed: the job is skipped until
`SONARQUBE_ENABLED=true` is added together with the host URL and token. Never
commit or paste the token into the repository or chat.
