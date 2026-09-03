# FoodLabelFlow / SpecTrace

This repository contains the Java 21 Spring Boot modular-monolith baseline and
React/TypeScript web shell for SpecTrace. The canonical database baseline is
the supplied `SpecTrace_PORTABLE_DATABASE_PACKAGE_v3` and is installed through
Flyway migrations under `backend/src/main/resources/db/migration`.

## Local run

```bash
docker-compose up --build
```

Then open <http://localhost:5173>. The local database is exposed on port 3307;
the application API is exposed on port 8080.

For a backend-only build, use `mvn -B -ntp -f backend/pom.xml verify`. For the
frontend, run `npm ci && npm run build` from `frontend/`.

Stage 0 control-plane state and Sprint 1 handoff work orders are under
`.project-control/`. The five Sprint 1 domain slices are intentionally not
implemented in the Stage 0 bootstrap.
