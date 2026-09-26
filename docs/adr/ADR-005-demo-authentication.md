# ADR-005: Demo Authentication Approach

## Status

Accepted

## Context

SCRUM-50 requires the authentication approach to be decided before the independent-approval demo.

The application currently uses an external-identity/header seam to construct the authenticated actor used by application-layer authorization. Review and publication operations enforce permissions in Java, including independent maker-checker approval.

Introducing a complete Spring Security login flow at the end of Sprint 3 would add authentication, session, UI, configuration, and integration-test scope that is not required to demonstrate the review and publication workflow.

## Decision

For the Sprint 3/4 demo, retain the existing header-based authentication seam with a controlled demo-user switch.

The demo environment will expose only predefined demo identities and permissions. Review, approval, rejection, request-changes, and publication authorization remains enforced in the Java application layer.

The controlled demo-user mechanism is a demonstration seam only and is not considered production authentication.

## Security Implications

Header-provided identity must not be trusted in a production deployment because a client able to forge identity headers could impersonate another user.

Therefore:

- the header seam is restricted to the controlled demo/development environment;
- only predefined demo users are permitted;
- authorization and maker-checker rules remain enforced server-side;
- creator and approver identities remain distinct for the independent-approval demonstration;
- audit events continue to record the acting user;
- production deployment must replace the demo seam with trusted authentication, such as Spring Security backed by an approved identity provider or trusted upstream authentication proxy.

## Consequences

### Positive

- avoids introducing a new authentication subsystem immediately before the workflow demo;
- preserves the existing automated tests and API contracts;
- allows deterministic switching between maker, checker, and publisher demo identities;
- keeps authorization, maker-checker, and audit behavior independently testable.

### Negative

- the demo seam is not suitable for production authentication;
- the demo environment must be configured so arbitrary external identity headers cannot be treated as trusted production credentials;
- production authentication remains follow-up work.

## Follow-up

Before production deployment, replace the controlled demo-user seam with a trusted authentication mechanism and retain the existing application-layer permission and maker-checker checks as defense in depth.