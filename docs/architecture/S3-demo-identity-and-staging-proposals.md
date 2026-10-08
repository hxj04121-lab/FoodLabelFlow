# S3 login/demo identity and staging proposals

Status: **PROPOSED — owner decisions pending**. Prepared 8 October 2026.
Decision owners: M4 for login/demo identity (SCRUM-50); M5 for staging
(SCRUM-51). These alternatives are reviewable proposals. They do not record
a team decision, change security configuration, deploy, or grant permissions.

## Login and independent-approval demonstration

The current identity seam reads `X-Auth-Provider` and
`X-External-Subject`, maps them to an active stored user using
`ExternalActorResolver`/`IdentityService`, and obtains permissions from the existing
user/role tables. The resolver respects the existing DEV_EXTERNAL enabled flag,
including case-insensitive provider matching and rejection of non-ASCII provider
namespaces, preventing MySQL accent-insensitive aliases; it does not change that flag.
Application services check the required permission and maker-checker policy.
Those headers identify a caller to the current development seam; they are not
proof of a production login session or ownership of an identity.

The existing local/CI Compose configuration enables the development external-auth
adapter. This proposal leaves that configuration and all grants unchanged.
The browser's maker creation/validation uses the actual connected actor with
explicit local consent and real permission checks. It fails closed while that
identity is unavailable. The new decision/publication client does not fabricate
a checker subject or role header. It uses a connected
request context; absent identity remains a 401 and insufficient privilege
remains a 403. A fixture supplying a different seeded identity demonstrates
test enforcement, not actual human approval.

GET /api/identity/current now reads the real five-field caller DTO with sorted
existing roles/permissions. The UI displays it and disables workflow controls
based on real permissions, lifecycle and maker-checker. Refresh rereads the
connected identity; neither that resource nor the UI selects a caller. The task
list/detail also reads actual paged task data. These implemented read capabilities
do not adopt either login alternative below.

| Option | Work and implications | Decision status |
| --- | --- | --- |
| Minimal Spring Security login | Authenticate a real session, define login/logout and actor/permission reads, protect credential storage/session/CSRF and map existing accounts. Requires a separate reviewed security design; no credentials or grants should be invented during product-flow repair. | Available Jira option; not implemented or selected here. |
| Retain the header seam for a controlled course demo | Owner-adopted ADR restricts the seam to the existing local/CI demo environment, documents its impersonation risk and defines a trusted actor-switch mechanism with explicit consent, fixed permitted seeded identities, and request-context invalidation. Backend permission and maker-checker checks still apply. | Available Jira option; the header seam exists, but the controlled switch and adopted decision remain unconfirmed. |

Before adopting the second option, M4 must name how the request context supplies
an allowed checker/publisher identity without allowing arbitrary browser-entered
subjects, how actor changes clear pending reads/write outcomes, and how the demo
is prevented from being presented as production authentication. No visible
avatar change alone establishes a caller identity.

Acceptance requires an actual merged code/ADR decision with the owner's
attribution and an independent maker/checker test. A green test using seeded
identities is useful test evidence; it cannot substitute for that owner decision.

## Staging and promotion

The existing workflow builds the application images, starts MySQL/backend/frontend
with Compose in its `containers` job, executes live browser validation and
uploads browser artifacts. The same Compose file supports an isolated local
course demo. This is an existing execution path, not a shared deployment.

| Option | Evidence needed | Decision status |
| --- | --- | --- |
| Shared deployed environment | Named environment, deployment SHA, health/database validation, actual browser evidence, rollback/promotion rules and owner verification. Hosting/deployment authorization is separate. | No deployment is performed by this continuation. |
| CI Compose plus local Compose as staging/demo | Adopted ADR defines CI's isolated full stack as the promotion gate, local Compose as the demo environment, exact-main SHA and fixture bindings, all five jobs/Sonar/full SOY browser acceptance, artifact retention and a replayable health/version check. | Recommended proposal for the existing no-deployment scope; M5 adoption is pending. |

Proposed promotion rule for the second option: an exact merged main SHA is
demo-ready only when its backend/frontend/containers/Sonar/security jobs succeed,
its real SOY browser path and required negative paths pass, and its database
migration/health checks are recorded. A PR result or an earlier main result
does not promote a later SHA.

Proposed verification record: repository and exact SHA; workflow run and artifact
IDs; database schema version and health; actual resource bindings for the run,
finding/task, old/new formulas and labels, validation, approval and publication;
browser trace/screenshots plus persisted API rereads; explicit actor roles and
limitations; confirmation that no deployed/shared environment was asserted.

M5 must record the accepted alternative and any changes to those rules.
Publishing this proposal alone does not satisfy SCRUM-51's decision gate.

## Concrete owner decisions still proposed

M4 / Zhu Wenyu must record either minimal session login or the controlled-header
demo ADR, including the trusted source of request context, the allowed existing
maker/checker/publisher identities, switching/refresh invalidation, handling of
unconfirmed writes, existing flag/ASCII namespace constraints, and the explicit
limit that this is not production login. The current fixture arrangement is the
existing officer maker, QA approver and publisher; ADMIN has no CREATE/APPROVE.
There is no compound-grant actor or new authorization. Its maker HTTP403 is ACL
evidence; permitted-creator maker-checker remains separately tested at service/unit
level. An owner decision is not supplied by fixture contexts.

M5 / Song Hanjie must record either an authorized shared deployment or a CI/local
Compose ADR with the exact promotion SHA/run/artifact requirements, input-fixture
scope, database/health verification, replay/rollback procedure and responsible
verifier. Existing CI Compose is a usable proposal; it is not an adopted staging
decision or proof of a deployed shared environment.

The user's current scope authorizes local implementation and excludes security
configuration/grant/credential changes and deployment. A reviewable ADR can be
prepared within that scope, but cannot be marked adopted or merged on the owners'
behalf. No implementation permission or elapsed wait supplies either decision.

## Dated test evidence for these proposals

The 4066 local candidate completed real legacy and S3 browser stages at
06:05:38 UTC, including twenty officer/QA/publisher publications and twenty CLOSED
tasks. That execution demonstrates the existing fixture context arrangement,
not an adopted login/switch ADR, a shared deployment, or the staging promotion of
a new main SHA. A catalog readiness correction and fresh final qualification are
pending. The named M4/M5 decisions above therefore remain PROPOSED, identified by
their exact proposal artifact hash in the review bundle.
