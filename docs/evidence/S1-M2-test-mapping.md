# S1-M2 acceptance criteria, negative paths, and evidence mapping

Status: **PR #3 design baseline is merged. Cross-module runtime reconciliation is
implemented on the M2 contract-closure branch; independent review, CI, and integration
remain required before the coordination status can be closed.**

PM contract source: [`.project-control/pm-contract.sha256`](../../.project-control/pm-contract.sha256).
M2 work order: [`.project-control/work-orders/S1/M2-sad-acceptance-support.yaml`](../../.project-control/work-orders/S1/M2-sad-acceptance-support.yaml).

## Refined acceptance criteria

| ID | Acceptance criterion | Verification target | Status now |
| --- | --- | --- | --- |
| AC-M2-S1-001 | The SAD names owning modules, BR-02/03/05/07/10 implications, no new schema, and the PM contract binding. | SAD review and contract diff | Implemented; closure review pending |
| AC-M2-S1-002 | The shared contract defines allergen lookup, validation-run request/read models, stable HTTP statuses, and a common error envelope. | OpenAPI structural/peer review | Automated contract test passes locally; peer review pending |
| AC-M2-S1-003 | The Strategy decision compares a brute-force conditional design with relevant alternatives and explains the selected per-rule evaluator registry. | SAD/design review | Merged in PR #3 |
| AC-M2-S1-004 | Cross-module assumptions are assigned to M1/M4/M5; no implementation claims integration before those owners review. | Review record | Runtime reconciled; independent closure review pending |
| AC-M2-S1-005 | Negative API cases are mapped to stable error codes before endpoint implementation. | Contract/negative-path tests | Shared handler tests pass locally; CI pending |

## Required negative-path test matrix

| Proposed test ID | Scenario | Expected result | Owner dependency | Evidence state |
| --- | --- | --- | --- | --- |
| TEST-M2-S1-API-001 | Omit `ruleSetVersionId` | 400 `INVALID_REQUEST` | validation | Planned |
| TEST-M2-S1-API-002 | Actor lacks validation permission | 403 `AUTHORIZATION_DENIED` | M4 identity/RBAC | Planned |
| TEST-M2-S1-API-003 | Unknown label version or run | 404 `RESOURCE_NOT_FOUND` | label/validation | Planned |
| TEST-M2-S1-API-004 | Request validation for superseded label | 409 `LABEL_VERSION_NOT_CURRENT` (BR-05) | M1 label snapshot | Planned |
| TEST-M2-S1-API-005 | Current label lacks canonical input or active rule-set | 422 `VALIDATION_PRECONDITION_FAILED` | M1 + M5 fixture | Planned |
| TEST-M2-S1-ARCH-001 | Validation accesses a foreign repository or SQL directly | Architecture test fails | M2 implementation | Planned |
| TEST-M2-S1-ARCH-002 | Identity depends on business modules | Existing architecture rule remains green | M4/M2 integration | Existing baseline test; re-run after integration |

Implemented closure tests:

- `SharedApiErrorContractTest`: M1 400/404/409 and M4/M5 401/403 mappings share the
  exact four-field envelope without invented evidence identifiers.
- `OpenApiContractTest`: required statuses, authentication/authorization codes,
  validation status enum, and all local component references are present.
- `ArchitectureTest`: the existing identity dependency boundary remains green.

## Evidence lifecycle

When actual work is integrated, record only factual identifiers in the evidence index:

| Proposed identifier pattern | Required binding | May be recorded when |
| --- | --- | --- |
| `EVID-M2-S1-ADR-001` | committed SAD/contract diff SHA | the M2 branch commit exists |
| `EVID-M2-S1-REVIEW-001` | real M1/M4/M5 review record | the review exists |
| `EVID-M2-S1-TEST-001` | test command/report and commit SHA | the test has run on that commit |
| `EVID-M2-S1-CI-001` | GitHub Actions URL/run and PR head SHA | CI is completed for the exact head |
| `EVID-M2-S1-AC-001` | acceptance check result and integrated main SHA | acceptance is actually checked after merge |

## Conflict and integration register

| Area | Likely conflict | Resolution before integration |
| --- | --- | --- |
| `docs/architecture`, `docs/contracts` | Shared-file domain owned by M2 but consumed by M1/M4/M5 | Obtain cross-module review; rebase after PR #2 merges |
| Label snapshot | M1 may choose different formula/label DTO names or lifecycle states | Preserve semantic fields, adapt names only after M1 contract review |
| Authorization | M4 owns permission and actor mapping | Do not hard-code roles; bind 403 to M4's final permission contract |
| Fixtures/CI | M5 owns reusable integration scaffolding and evidence binding | Add endpoint tests only into the accepted M5 fixture convention |
| Database | Work order forbids schema changes | Use the baseline tables; submit PM change control if a missing constraint is discovered |

## Pre-integration checklist

- [x] PR #2 is merged; its historical approval/evidence record is not restated here.
- [x] The closure branch is based on `a3bb498bb565e4f53d8161ba837f833d6bb9167b`,
      which includes merged M1 PR #4 and M4/M5 changes.
- [ ] M1, M4, and M5 review the shared assumptions relevant to their scope.
- [ ] Any real endpoint implementation has API, negative-path, architecture, and
  MySQL/Testcontainers coverage appropriate to the accepted fixture convention.
- [ ] Evidence references use actual commit, review, and CI values only.
