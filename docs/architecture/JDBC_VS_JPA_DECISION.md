# JDBC vs JPA Decision

Status: Accepted

Date: 2026-09-21

## Context

Sprint 2 validation persistence needs a reviewable persistence decision while
keeping the existing schema, application ports, and transaction boundary.

## Decision

Continue using Spring JDBC with `JdbcTemplate`. JDBC is an infrastructure
implementation detail behind the validation application ports.

## Evidence

- `backend/pom.xml` declares `spring-boot-starter-jdbc`, Testcontainers MySQL,
  and ArchUnit; it does not declare Spring Data JPA or Hibernate.
- Validation repositories and the owner-module adapters are implemented with
  `JdbcTemplate` and parameter-bound SQL.
- Flyway V1-V3 remain the canonical schema owner.
- `ValidationApplicationService` is the transaction seam used by the
  persistence integration tests.
- `MySqlIntegrationTestSupport` and the validation MySQL suites execute
  against the canonical `mysql:8.4.11` Testcontainers image.

## Why not JPA now

The current system is already standardized on JDBC, and the validation ports
and adapters align with that choice. Introducing an ORM is not required by
SCRUM-34 and would add unrelated entity mapping, migration, and refactoring
risk.

## Boundaries

- Domain and application packages must not depend on `JdbcTemplate`.
- JDBC remains an infrastructure concern.
- This decision does not add `spring-boot-starter-data-jpa`, Hibernate,
  `@Entity`, or `JpaRepository`.

## Reconsideration conditions

Re-evaluate JPA only if a future requirement needs ORM-specific capabilities
that materially reduce complexity across multiple aggregates, and after a
separate schema/migration and performance review authorizes that migration.
