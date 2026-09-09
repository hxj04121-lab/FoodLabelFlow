# S1-M1 supplier/material/specification/formula

Work order: `.project-control/work-orders/S1/M1-supplier-material-formula.yaml`.
Owner: M1 Huang Xiangjia. Base: `97fd46ebb27830a7bce5a2bee2f214519a37f622`.
Status: implementation for review; **not an integrated Sprint Done claim**.

## Baseline and scope

The PM hash matches `2aafb403c3e437dd9f4d896c74504348f0aec50b7bdd0b171185605a0a1fdb41`.
PR #1 is merged, DB0 is PASS in the committed marker, and main CI run
33864119086 passed backend, frontend, containers, Trivy and Dependency-Check.
The stale pre-merge bootstrap marker is not rewritten. Main currently has no
Sonar job; earlier Stage 0 branch Sonar evidence is not current-main evidence.

M1 implements catalog APIs, draft/released specifications, append-only formula
content and traceability. M3 owns UI; M4 owns identity and audit; M5 owns integration.
No migrations, shared workflow/build configuration, or other members' files change.
No label publication, maker-checker workflow, allergen derivation or QR scope is added.

## Use case and transition

UC-M1-01: maintain supplier and material, create a specification with explicit
component evidence, release it, create a formula referencing exact specification
versions, release against the expected current product formula, retrieve trace.

Normal flow: HTTP boundary -> validated command -> CatalogService transaction ->
CatalogStore -> canonical MySQL tables -> audit adapter -> committed response.
Exceptional flows: invalid input 400, absent reference 404, duplicate/stale/immutable
version 409, mismatched/draft/future specification 422, unauthorized actor 403,
missing identity/audit integration 503. An audit failure rolls back all writes.

Analysis supplier/material/specification/formula entities become validated command
records and immutable response snapshots. The analysis control becomes CatalogService;
boundary becomes CatalogController/CatalogErrors; persistence becomes CatalogStore.
M2's `code`/`message` error convention is preserved; no fictional evidence IDs emitted.

## Design decision: transaction script and repository

The concrete problem is publishing one current version while retaining historical
formula content under concurrent requests. Updating the old formula's items in place
would break BR-01. A new ORM/schema or generic workflow engine is unnecessary here.
CatalogService coordinates one transaction and a repository isolates parameterized SQL.
Parent row locks serialize version allocation and release. Locking reads of version
numbers avoid stale repeatable-read snapshots after authentication queries. Specification
locks are acquired in ID order. The expected current formula ID rejects stale releases.

Only current-selection metadata changes on the previous version; its content, lifecycle,
release actor/time and item references remain intact, matching the V2 helper semantics.
No PUT/PATCH/DELETE operation mutates historical formula/specification content.
New versions are created with a new ID and server-assigned version number.

The accepted main uses Spring JDBC, not JPA. This implementation retains that executable
baseline without changing the shared build. The PM snapshot's JPA requirement therefore
remains an explicit architecture alignment item for M2/manager review; JPA compliance
is not claimed. V1-V3 remain byte-for-byte unchanged; tests apply them to empty MySQL.

## Identity/audit integration required from M4

`CatalogIntegration` is a catalog-owned application port. A catalog integration bridge
should implement it by calling M4-owned identity and audit application APIs. Do not put
an implementation that depends on catalog inside identity: existing architecture rules
forbid identity -> business-module dependencies.

- `requireActor(permission)` returns a verified active database user ID from authenticated
  server context. Never trust an actor ID in JSON or an unverified header.
- Existing canonical permissions: `DATA.MAINTAIN` for creation/spec release;
  `FORMULA.RELEASE` for formula release.
- `audit(...)` must persist a real audit event synchronously in the same datasource and
  transaction (REQUIRED/MANDATORY; not REQUIRES_NEW or asynchronous). Throw on failure.
- Until that bridge exists, writes explicitly return 503. There is no permissive
  production fallback. TestAdapter lives only in test sources and is not deployable.
- Catalog GET endpoints currently expose the existing non-secret reference dataset.
  M4 must enforce the chosen read-access policy at integration before wider deployment.

## M3 / M5 handoff

See API.md for exact paths and JSON. M3's current XFY branch uses preview data and does
not call these writes yet. Wire it after port integration; preserve explicit backend
400/409/422 messages. The UI must send current_formula_version_id on release and refresh
after CURRENT_FORMULA_CHANGED. Do not show a preview as a successful persisted release.

M5 can run `mvn -B -ntp -f backend/pom.xml verify` with Java 21 and Docker.
For local Colima, supply DOCKER_HOST for its socket and
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock. Tests use disposable MySQL
8.4.11, not the user's local database. Integration tests cover create/read/trace,
invalid specifications, two concurrent releases, concurrent version allocation,
audit rollback, HTTP validation/authorization/duplicate errors and canonical migration.

## Human and report evidence

Tests were written after initial implementation; this is not a TDD claim. Automated
independent code review is not a human pair session or independent PR approval.
Human business acceptance, cross-module contract review, Review/Retro, actual hours and
contribution confirmation remain pending. R1 reporting period ends September 11;
the package records the September 12 due date. Jira updates remain user-managed.

After M4/M3 integration: create a supplier/material/specification, release the spec,
create and release a formula, view the exact supplier/spec/component provenance,
then verify the prior formula still has its original items. Attempt a stale release
and a draft/mismatched specification. Record actual observations and commit/URL.
