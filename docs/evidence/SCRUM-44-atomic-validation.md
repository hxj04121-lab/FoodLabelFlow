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

`NegativeGoldenValidationPersistenceMySqlTest` adds four cases using the exact M2
negative SQL in a separate container (the positive and negative resources reuse
canonical allergen codes). The real service, identity bridge and repositories
persist missing-declaration, unmapped and ambiguous inputs as completed FAILED
runs, with exact golden rule IDs, codes, severity, flags and messages. Input-level
guards retain their component/status attribution and null rule ID. The exact
retired rule-set remains a 422 with no run, result or audit rows.

| Requirement | Executable evidence |
| --- | --- |
| Successful atomic commit | PASSED run, all findings, exact IDs, trusted actor, UTC time, label provenance and attributable audit payload round-trip |
| Complete evaluated failure | Every blocking failure persists with FAILED; warning failures remain nonblocking PASSED; input findings retain a null rule ID |
| Reviewed M2 negative truth | ALLERGEN_DECLARATION_MISSING, INGREDIENT_UNMAPPED and INGREDIENT_AMBIGUOUS findings round-trip with their full golden fields and an attributable audit event |
| Fresh run per request | Repeated validation creates independent run/result IDs and audit events |
| Audit failure rolls back everything | Injection after the real audit insert observes staged run/results/audit, throws, then verifies all three tables are empty |
| Partial result write rolls back | A real duplicate-key failure after the first result insert leaves neither run nor results and never calls audit |
| Current-label and exact rule-set guards | Historical/rejected/superseded labels, stale formula, missing label, different requested version, DRAFT/RETIRED rule set, invalid dates/jurisdiction and zero active definitions produce no writes |
| Trusted identity and permission | Unknown/inactive identities and an actor without LABEL.VALIDATE produce no writes |
| Protection through commit | A second connection cannot lock seven captured row types with FOR UPDATE NOWAIT while audit is paused, sees no partial commit, then sees complete persisted state after audit completes |

Verification after synchronizing main on 2026-09-18 with Java 21 and MySQL 8.4.11:

- SCRUM-44 MySQL suites within the full run: **24 tests, zero failures/errors/skips**.
- Full backend verify and executable JAR build: **164 tests, zero failures/errors/skips; BUILD SUCCESS**.
- The full run includes PR #29's newly merged executable fixture binding test,
  which accounts for the increase from the prior 163-test verification.

The full backend command is:

```sh
mvn -B -ntp -f backend/pom.xml verify
```

The first full run exposed existing shared-fixture pollution: the HTTP formula
lifecycle test committed a new current formula and left it selected, causing the
later allergen and workflow-publication tests to fail. Its cleanup now restores
the original formula selection and removes only the formula/items/audit rows it
created. The HTTP test still verifies real commits before that cleanup.
That cleanup is now in main through PR #27 and is no longer part of the
remaining SCRUM-44 diff.

## Dependency and review scope

[PR #27](https://github.com/hxj04121-lab/FoodLabelFlow/pull/27) was merged into
main on 2026-09-17 as `7d8dd20`. SCRUM-43's orchestrator and reviewed-contract
fix `a9bcd6c` are therefore already on main, not a pending merge dependency.
Its stale-formula 409 mapping, explicit null-target unresolved ingredient rules,
and shared CONTAINS/missing-declaration semantics are documented in
[SCRUM-43 regression evidence](SCRUM-43-review-regressions.md).

On 2026-09-18 this branch explicitly merged `origin/main@58f381d`, including
the merged [PR #29](https://github.com/hxj04121-lab/FoodLabelFlow/pull/29) and
its positive-fixture no-output regression check. The merge is conflict-free;
PR #29's executable fixture test and evidence file are retained unchanged.

The final diff against `58f381d` is limited to eight SCRUM-44 files: the
transactional application service, its Spring wiring, row locks in the three
owner adapters, the two persistence test classes, and this evidence document.
There are no additional evaluator, fixture SQL, schema, frontend or HTTP changes.
Final HTTP controllers and end-to-end API wiring remain SCRUM-45's scope.
Automated verification does not represent human review or a Jira Done transition.
