# SCRUM-84 — Login decision and controlled demo identities

**Status:** Proposed for team review. **Scope:** SCRUM-73 / SCRUM-84 integration.

## Context and decision

The existing UI uses an external provider/subject header contract. The local demonstration needs three independently permissioned users: Maker (`LABEL.CREATE`, `LABEL.VALIDATE`, `LABEL.SUBMIT_REVIEW`), Checker (`LABEL.APPROVE`, `LABEL.REQUEST_CHANGES`) and Publisher (`LABEL.PUBLISH`). These are **different server-side users**, not merely UI role labels.

**Decision:** Use the server's `DEV_EXTERNAL` mappings solely for explicitly enabled local demonstration; resolve and authorize every protected command on the backend. The frontend fetches `/api/identity/demo-options` and `/api/identity/current` after selection. Production authentication remains a **future OIDC/Keycloak integration**, not a completed feature. Do not treat client-supplied identity headers as production authentication.

## Security implications

- Development identity switching must be explicitly enabled and refused in production, including when a development flag is accidentally enabled. A localhost-only UI is not a security boundary; backend enforcement is mandatory.
- On every identity transition, clear actor/permissions immediately, invalidate old data and form state, abort tracked requests and reject late responses from an earlier generation. Writes must remain unavailable while the new identity is unverified.
- Every workflow command rechecks server-side permissions. A Maker cannot approve their own label; publishing requires a matching APPROVE record for the **current** task/label version and current validation.
- `REQUEST_CHANGES` retains the task and historical label/approval records. Maker creates a new immutable version, which requires fresh validation and independent approval.
- Demo identities are not suitable for an untrusted shared deployment. Restrict bind address, network exposure and deployment profiles accordingly. Avoid persisting tokens or passwords in frontend storage.

## Alternatives

| Option | Decision | Reason |
| --- | --- | --- |
| UI-only role toggle | Rejected | Does not enforce authentication or permissions. |
| DEV_EXTERNAL controlled demo | Chosen for local demo only | Works with current identity tables and supports workflow demonstrations. |
| Keycloak/OIDC now | Deferred | Requires token validation, client registration, session/logout and production deployment design. |

## Implementation and verification status

- **Implemented in working tree, not yet accepted:** demo-options endpoint, production guard in `ExternalActorResolver`, identity session store, switcher, and initial returned-task revision API.
- **Previously verified manually:** demo-options HTTP 200; browser Maker/Checker/Publisher selection; Maker draft-create enabled after consent, Checker/Publisher disabled; RBAC API Playwright one test passed.
- **Still required:** identity-switch race tests, production-configuration negative test, revision browser E2E, full integration CI and formal security review. A successful build alone is not acceptance.
