# ADR: SCRUM-68 staging with CI Compose and local Compose

Status: **reviewable ADR; adoption follows normal merge and the linked Jira decision record below**. Prepared 8 October 2026.
Issue: [SCRUM-68](https://hxj04121.atlassian.net/browse/SCRUM-68).
Accountable module: M5, Jira handle shj040128shj, account
712020:a7a06357-9441-48b2-8d6c-c6f888fcb99e, GitHub SHJ-SHJ0128.
SCRUM-68 itself is currently unassigned. Prepared by M2 for actual user/team review;
this draft does not attribute an acceptance or deployment to M5.

## Decision

Use the existing **CI Compose stack as the isolated staging qualification gate**
and **local Compose as the controlled course-demo environment**. Shared staging is
currently unavailable and is not required to close this decision through the
merged-ADR alternative expressly allowed by SCRUM-68. This chooses an executable
environment policy; it does not provision a shared service, publish an image,
change authentication, grants, credentials or Compose configuration.

The decision becomes recorded when this ADR is normally reviewed and merged to
main and its exact merge/source and verification links are added to SCRUM-68.
Record the actual reviewer and decision provenance then. An unmerged draft, the
earlier PROPOSED document or this local delivery alone is not a merged decision.
The existing team review policy applies; no new all-module approval requirement
is introduced. Staging is separate from the personal SCRUM-48/57/58 criteria.

## Context and alternatives

SCRUM-68 requires CI/local roles, promotion, verification, owner/limitations and
truthful shared-staging status. It explicitly accepts either verified shared
deployment evidence or a merged ADR. The [existing proposal on qualified main
0dc](https://github.com/hxj04121-lab/FoodLabelFlow/blob/0dc1737dab1cf5e9152c279197de44b57d306ae4/docs/architecture/S3-demo-identity-and-staging-proposals.md)
already describes the roles, five-job exact-main promotion and limitations, but
labels M5's choice pending. This ADR completes the choice and operational record.

A shared hosted environment would need its own authorization, named destination,
deployment SHA, access, health and rollback proof. No such environment is claimed.
Using only a local successful run would omit the existing merged-main CI gate.
CI plus local Compose reuses the actual verified pipeline and avoids those gaps.

## Environment roles and isolation

- CI: a disposable GitHub Actions runner builds backend/frontend from the checked-out
  SHA, starts MySQL/backend/frontend, runs the real validation/S3 scenario,
  preserves reports/screenshots/observations, then destroys its own stack.
- Local: an isolated project and new database volume replay a promoted SHA for a
  supervised course demo. Record the exact checkout, project, image/container IDs,
  ports and volume before use. Do not reuse a user's existing project/database.
- Shared: unavailable. CI container startup and a localhost demo are not shared
  staging, external availability, production readiness or production authentication.

The [existing Compose file](../../docker-compose.yml) uses MySQL 8.4.11,
Flyway at backend startup, a named project-scoped MySQL volume and health-based
dependencies. It builds app images locally; there is no registry-promotion job.
Record image IDs/digests as well as commit SHA: mutable base tags mean SHA alone
does not prove byte-identical rebuilt images.

Compose's existing development identity adapter remains enabled for its fixture
environment. Published ports are not restricted to loopback in this file; run
only on a controlled CI/local host and do not present it as an Internet service.
No caller-switch/login decision is adopted by this staging ADR. A seeded QA or
publisher is test evidence, not a real human approval or a production session.

## Promotion rule and present qualified baseline

Promote only a specific **merged-main SHA** whose actual main workflow has completed
successfully. Backend, frontend, containers, security and Sonar must actually run
and succeed under the existing policy; a conditional skip is not execution success.
Confirm the real scenario and required negatives for that SHA, and retain matching
raw reports/artifacts. A PR result, local result, branch-push result or earlier
main run does not promote a later SHA. Build/start/health alone is insufficient.

Current verified baseline:

- Main SHA: 0dc1737dab1cf5e9152c279197de44b57d306ae4, merged through [PR71](https://github.com/hxj04121-lab/FoodLabelFlow/pull/71).
- [Actual main CI37741058672](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/37741058672):
  backend, frontend, containers, security and Sonar all completed SUCCESS.
- Actual backend reports: 90 XML suites, 618 testcases, zero failures/errors/skips.
  Real scenario: 40 adoptions, 40 findings (20 NO_ACTION / 20 REVIEW_REQUIRED),
  20 independent approvals/publications, 20 CLOSED tasks and 60 historical label
  content checks. Containers actually ran validation PASS/blocking FAIL and S3
  publication. Frontend fixture 91 PASS / 3 opt-in SKIP remains separately scoped.
- At preparation, candidate 20d2d76dffb857b39549e8391aa7890ffdd7dc26 had passed
  619 tests and four genuine browser stages locally but was not remotely qualified.
  Later PR and main results require their own source-bound receipts. The local
  result is **not promoted by main0dc's result**.

When a later SHA changes the required live stages, use that SHA's actual workflow
definition and receipts. New catalog/compound/formula qualification must not be
retroactively claimed for main0dc. This ADR does not require optional new features
to declare the existing baseline qualified.

## Fixtures and real execution

Use that exact SHA's migrations and approved scenario inputs in the disposable
database. Baseline main0dc contains Flyway V1-V8; record actual installed history
rather than infer the database version from filenames alone.

The main0dc [containers workflow](https://github.com/hxj04121-lab/FoodLabelFlow/blob/0dc1737dab1cf5e9152c279197de44b57d306ae4/.github/workflows/ci.yml)
extracts only released specification input from the [adoption fixture](../../backend/src/test/resources/fixtures/s3-soy-spec-v2-adoption.sql).
Its runtime scenario performs adoption, first draft declarations, evaluation,
submission, independent QA approval and publication through the real API/UI.
Do not seed those new business outputs, PASSED results or decisions directly in SQL.
Existing migration baseline data and released scenario inputs have distinct scopes.

The baseline order is specification input, legacy PASS/blocking FAIL, then the S3
path. A newer workflow may add read-only catalog, an isolated compound-maker
fixture and a final formula lifecycle test; use its exact order. Freeze S3
publication/history proof before a later formula write changes a current pointer.
A simulated catalog 503 case is a fixture test, not proof of a real backend.

## Replay and verification procedure

These are documented steps for the selected environment; none were executed by
the ADR author in this task. Use a fresh isolated checkout/project, verify a clean
checkout of the promoted SHA and record the inputs. For the present baseline,
a project name such as s3-demo-0dc1737-20261008 identifies a new owned environment.
If its ports are occupied, stop and choose an authorized isolated run; do not
stop unrelated containers or overwrite an existing database.

Use the existing Compose configuration to validate, build and start that project.
Record the commands and results for config --quiet, build, up -d mysql backend
frontend, ps and images. Identify image/container IDs and the project's volume;
retain readiness logs. The existing default host ports are MySQL3307,
backend8080 and frontend5173; record actual values.

Verification must include:

1. MySQL health/SELECT 1, backend health and frontend availability. The existing
   [health implementation](../../backend/src/main/java/com/spectrace/identity/application/HealthApplicationService.java)
   returns status=ok and database=ok only after SELECT 1. It does not expose a
   source SHA; health is not revision proof.
2. Read actual flyway_schema_history version/checksum/success and migration
   validation/startup result. All expected migrations must have succeeded;
   no missing history, repaired checksum or manually weakened migration is accepted.
   Successful Flyway/backend/Compose CI is existing evidence; a separate retained
   local staging-history query is recorded by the verifier when running a demo.
3. Actual protected identity and pinned scenario resource bindings: change/run,
   product, old/new formula, finding/task, label/rule set, real validation,
   independent approval/publication, current pointer and immutable old content.
   Keep authentication fixtures scoped to this owned environment.
4. Browser logs/screenshots/raw observations and exact API/DB rereads. Reusing
   main evidence verifies that CI run; it does not prove a newly started local
   stack. A local demo gets its own timestamp and receipt.

A verification record contains repository, exact SHA, main run/event/attempt,
actual job outcomes, artifact IDs/digests, checkout/Compose/image/container/volume
identity, migration/health results, actors and resources, scenario counts,
limitations, verifier account/time and the retained evidence location.

## Evidence and retention

The following artifacts were freshly read for exact main0dc/run37741058672.
Their digests are GitHub artifact metadata, not a claim of a new archive download.

| Artifact | Actual ID and digest | Actual expiry |
| --- | --- | --- |
| [backend-coverage metadata](https://api.github.com/repos/hxj04121-lab/FoodLabelFlow/actions/artifacts/11533883509) | 11533883509; sha256:ec8274dcf31877c9fddef073defef0b7ba12b5c4a50e2262106002c7adc5ec64 | 2027-01-06T07:02:52Z |
| [backend-test-reports metadata](https://api.github.com/repos/hxj04121-lab/FoodLabelFlow/actions/artifacts/11533559510) | 11533559510; sha256:ccf9553232937ca3c424668ce8574bd092f41740b3c7da2c01b1cbddf975469b | 2026-10-22T07:08:02Z |
| [validation-browser-evidence metadata](https://api.github.com/repos/hxj04121-lab/FoodLabelFlow/actions/artifacts/11533294183) | 11533294183; sha256:9476b7cc9b2dee044bd86352df15707731837d831d41aacdefdc93a3d1d3b52d | 2027-01-06T07:02:52Z |

Backend reports explicitly retain 14 days. Browser/coverage do not specify
retention in YAML; the listed expiries are this run's actual metadata, not an
invented uniform policy. Preserve needed raw artifacts and SHA receipts in the
already authorized delivery location before their expiry; do not create an
unrequested publication destination. A missing/expired artifact is recorded,
not silently treated as proof.

## Failure, rollback and disposal

Business rollback is already exercised by MySQL transaction/failure-injection
tests. It is different from rolling back an environment or reversing Flyway.

On a failed verification, withhold promotion and retain that run's errors,
logs and receipts. Reproduce with a separate fresh project/database at a
previous qualified SHA; do not attach an old application image to a newer
database and assume schema compatibility. This ADR defines no reverse migration
or automatic restoration of persistent data.

For a local demo, stopping its exact owned project without deleting its volume
preserves diagnostic data. A reset uses a new isolated project/volume. CI's
existing down -v disposes only its disposable stack; do not copy it against an
existing user/shared project. Delete an owned disposable volume only after its
project/volume identity is verified and evidence is retained. Shared deployment,
persistent restore and environment changes require their own authorized work.

## Owner acceptance and SCRUM-68 completion record

The adopter/reviewer records the selected CI+local option, actual main merge SHA,
this ADR's GitHub source link, qualified-run link, verification scope, accountable
M5 contact and truthful shared-unavailable status in SCRUM-68. Use real attribution;
preparation by M2 and seeded fixture identities do not constitute M5's acceptance.

SCRUM-68's stated completion is the real decision merged to main and linked from
Jira, with no unverified shared-staging claim. It does not demand a shared
deployment once this ADR alternative is chosen, universal all-module signoff,
or completion of M4 login, teammates' A07 or personal M2 tasks. The existing
merged proposal supplies substantial background evidence; its PROPOSED status
is preserved until the decision is actually recorded.

Remaining at draft time: normal review/merge and the exact Jira link. No new
staging resources, runtime re-execution or source/security changes were performed
to prepare this ADR.
