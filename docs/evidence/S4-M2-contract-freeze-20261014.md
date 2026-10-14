# S4 M2 contract-freeze candidate

Date: 14 October 2026. Owner: Cai Runchen / M2. Jira: [SCRUM-86](https://hxj04121.atlassian.net/browse/SCRUM-86).

## Scope

This change contains the M2-owned contract revision, contract alignment tests,
and final-report A07 index. It does not include M1, M3, M4, or M5 feature
implementation.

The candidate updates the label/workflow OpenAPI and error matrix for the
returned-task revision flow, S4 demo identity options, and the product label
history read API. It adds the batch specification-adoption contract and updates
the specification-adoption reference. The impact contract documents M1's
additive `productDisplayName` field alongside the existing counters and outcome
discriminator. The exact Java implementation remains owned by its producer.

The [final-report index](S4-final-report-index-20261010.md) consolidates the
available S1–S3 use cases, analysis/design diagrams, and design-pattern
decisions for M1–M5. Missing S4 owner-specific A07 artifacts are called out as
pending rather than inferred from implementation code.

## Contract review state

The label contract is version 1.3.0 and the impact contract is version 1.1.0.
The new batch-adoption contract is version 1.0.0. Contract tests check documented
routes, permissions, response schemas, error paths, and the additive impact
field against the existing contract surface.

This remains a freeze candidate. M1 producer confirmation, M3 consumer
acceptance, and M4/M5 cross-module review records are not present in this change.
The contracts therefore remain marked `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`.
The change has not been merged to `main`, so it does not claim the Jira
acceptance criterion that contract tests pass on `main`.

## Verification

Focused backend contract and identity suite on the main-based M2 branch, JDK
25.0.4, offline Maven: **30 tests passed, 0 failures/errors/skips** across
`S4M2ContractAlignmentTest`, `S3ImpactApiContractTest`, `OpenApiContractTest`,
`SharedApiErrorContractTest`, and `DemoIdentityHttpTest`.

GitHub CI on the M2-only pull request is the authoritative check for this
branch. No teammate approval or Jira completion is inferred from a passing
build.
