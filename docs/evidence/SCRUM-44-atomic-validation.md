# SCRUM-44 — atomic validation persistence

Jira: [SCRUM-44](https://hxj04121.atlassian.net/browse/SCRUM-44),
“S2-M1.4 Persist validation results and audit atomically”.
The description was checked on 2026-09-17. Its acceptance criteria require complete
rollback, an effective exact rule-set/current-label guard, and MySQL Testcontainers
proof that a failed operation leaves no partial validation run.

## Application boundary

`ValidationApplicationService.validate(labelVersionId, ruleSetVersionId)` is the
transactional entry point. It invokes SCRUM-43's `ValidationOrchestrator` inside the
same transaction, so trusted identity, permission, currentness, exact version,
effective-date and jurisdiction checks precede persistence. The caller cannot
submit an already evaluated outcome or supply an actor, timestamp or persistence ID.

After complete evaluation, the service generates independent UUIDs for the run and
each finding, saves M5's existing `ValidationRun` and all `ValidationResult` records,
and calls `ValidationIntegration.auditValidation` synchronously before returning.
Both PASSED and completed FAILED evaluations are recorded. Nonblocking failures and
input-level findings with a null rule-definition ID are preserved. The run uses the
trusted actor and the label snapshot's existing provenance ID; no new provenance or
audit identity is invented. `ranAt` is generated in UTC at the canonical MySQL
`DATETIME` column's whole-second precision. The optional summary remains absent.

The existing M4 bridge invokes `AuditEventPort` with MANDATORY transaction
propagation. A repository or audit exception propagates and rolls back the run,
every result and the audit event. The service does not catch and downgrade failures.

The owner adapters lock the label root, formula root, declarations, formula items,
specification components, exact rule-set and definitions through commit. All SQL
stays within the owning module; validation consumes existing application ports.
Flyway V1–V3, the HTTP contract and repository signatures are unchanged.

## Executable acceptance evidence

`ValidationApplicationServiceMySqlTest` runs 20 cases on an isolated MySQL 8.4.11
Testcontainer using Flyway V1–V2 and M2's positive fixture SQL, with explicit fixture
identity permissions. It has no test-managed transaction: queries after a service
call observe its committed or rolled-back database state.

| Requirement | Executable evidence |
| --- | --- |
| Successful atomic commit | PASSED run, all findings, exact IDs, trusted actor, UTC time, label provenance and attributable audit payload round-trip |
| Complete evaluated failure | Every blocking failure persists with FAILED; warning failures remain nonblocking PASSED; input findings retain a null rule ID |
| Fresh run per request | Repeated validation creates independent run/result IDs and audit events |
| Audit failure rolls back everything | Injection after the real audit insert observes staged run/results/audit, throws, then verifies all three tables are empty |
| Partial result write rolls back | A real duplicate-key failure after the first result insert leaves neither run nor results and never calls audit |
| Current-label and exact rule-set guards | Historical/rejected/superseded labels, stale formula, missing label, different requested version, DRAFT/RETIRED rule set, invalid dates/jurisdiction and zero active definitions produce no writes |
| Trusted identity and permission | Unknown/inactive identities and an actor without LABEL.VALIDATE produce no writes |
| Protection through commit | A second connection cannot lock seven captured row types with FOR UPDATE NOWAIT while audit is paused, sees no partial commit, then sees complete persisted state after audit completes |

Verification on 2026-09-17 with Java 21 and MySQL 8.4.11:

- Focused SCRUM-44 suite: **20 tests, zero failures/errors/skips**.
- Full backend verify and executable JAR build: **145 tests, zero failures/errors/skips; BUILD SUCCESS**.

The full backend command is:

```sh
mvn -B -ntp -f backend/pom.xml verify
```

The first full run exposed existing shared-fixture pollution: the HTTP formula
lifecycle test committed a new current formula and left it selected, causing the
later allergen and workflow-publication tests to fail. Its cleanup now restores
the original formula selection and removes only the formula/items/audit rows it
created. The HTTP test still verifies real commits before that cleanup.

## Dependency and review scope

This branch starts at `origin/main@85b34c3` and includes the existing SCRUM-43
orchestrator commit `a52e71c` from [PR #27](https://github.com/hxj04121-lab/FoodLabelFlow/pull/27).
That PR was still open when this work began. Its commit is retained as an ancestor
so the dependency is reviewable. Review/merge PR #27 first, then synchronize this
branch with main if needed to show only the SCRUM-44 changes.
SCRUM-44 adds the transactional service, snapshot locks and persistence tests.
Final HTTP controllers and end-to-end API wiring remain SCRUM-45's scope.
Automated verification does not represent human review or a Jira Done transition.
