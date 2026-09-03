# CI and security baseline

## Defined paths

- `.github/workflows/ci.yml` runs backend Maven verification, frontend build,
  Compose configuration validation, and container image builds.
- The `security` job contains Trivy filesystem scanning. It is enabled only
  when the repository variable `SECURITY_SCANS_ENABLED` is explicitly set to
  `true`.
- The `dependency-review` job uses GitHub Dependency Review on pull requests
  and fails when a changed dependency is high severity or above.
- `.github/dependabot.yml` monitors Maven, npm, and GitHub Actions updates on a
  weekly schedule.
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
| GitHub dependency security workflow | READY | Dependency Review checks changed PR dependencies; Dependabot monitors Maven, npm, and GitHub Actions without a local NVD database |
| SonarQube workflow | CONFIGURED | `SONARQUBE_ENABLED=true`, `SONAR_HOST_URL`, and `SONAR_TOKEN` are configured; the next pushed workflow run will execute analysis |
| Shared staging | DEFERRED | Explicitly deferred by the user |
| Jira assignment | USER_MANAGED | Explicitly left for the user |

The security variable `SECURITY_SCANS_ENABLED=true` and the SonarQube
configuration are set on the repository. The token is stored as a GitHub
Actions secret and must never be committed or pasted into the repository or
chat.
