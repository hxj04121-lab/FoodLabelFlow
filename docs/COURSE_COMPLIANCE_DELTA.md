# SWE5006 course compliance delta

Historical Stage 0 record. For the frontend target selected on 2026-09-07 (React + TypeScript + Tailwind CSS + shadcn/ui), see [Frontend stack decision](FRONTEND_STACK_DECISION.md). The original stack named below describes the baseline at that time, not completed migration evidence.

The supplied PM package is treated as the controlling course specification.
This stop preserves the required Java 21, Spring Boot, React/TypeScript,
Ant Design, MySQL, Flyway, Testcontainers, ArchUnit, CI/security, Jira, and
evidence/control-plane targets.

Completed or recorded:

- exact portable DB v3 was runtime-audited and passed;
- canonical Flyway V1/V2/V3 files were copied unchanged;
- modular-monolith and web-shell baseline was generated;
- five S1 handoff work orders were created without starting feature work.

Not claimed yet:

- shared staging and SonarQube execution;
- assignment of M2–M5 to named Jira accounts (the authenticated picker exposed
  only the current user and Unassigned);
- independent human review/approval, merge, or browser acceptance;
- Sprint 1 feature completion.
