# S3 M2 Day 6: A07 evidence index and verification

Owner: Cai Runchen / M2. Jira: SCRUM-57 under SCRUM-48.
Date: 2026-10-06, Asia/Shanghai. State: **design/evidence delivered as a review candidate**.

## Today's scope and implemented baseline

SCRUM-57 is the assigned logical Day 6 work item: analysis/design class and sequence
diagrams for Adopt Specification Change into Formula, the immutable-adoption design
problem, and reconciliation of actual cross-module review. This scope comes from
the live Jira task and available Day 4/Day 5 implementation, not from assuming that
the calendar date grants ownership of another module or the Day 7 merge gate.

At the original Day 6 preflight, main was `e7512c638dd8661cff7e6c64aaf978a8acba3bf0`.
Day 4 [PR61](https://github.com/hxj04121-lab/FoodLabelFlow/pull/61), head `d6ca803`, and
Day 5 [PR62](https://github.com/hxj04121-lab/FoodLabelFlow/pull/62), head `20a41e4`,
were unmerged drafts in that snapshot. This evidence is based on exact Day 5 commit
`20a41e48c71e0851bf9c71de9ba580cf1623323a`; the Day 6 branch then depended on PR62.
Later attributable M1 decisions are recorded in the
[2026-10-07 follow-up](S3-M2-day6-cross-module-review.md#attributable-m1-follow-up-on-2026-10-07).
They do not change the inspected baseline or turn these Day 6 checks into new runs.

The earlier 473/480-test runs establish the implementation baseline and are not
counted as today's new implementation or today's local test executions. Day 6
adds the artifacts and verification below.

## A07 artifact map

| Required evidence | Artifact |
| --- | --- |
| Use case, main/exception flows, invariants and analysis-to-design mapping | [Adoption SAD](../architecture/S3-M2-adoption-sad.md) |
| Analysis class diagram | [Editable Mermaid](../architecture/diagrams/s3-m2-adoption-analysis-class.mmd) / [rendered SVG](../architecture/diagrams/s3-m2-adoption-analysis-class.svg) |
| Analysis sequence diagram | [Editable Mermaid](../architecture/diagrams/s3-m2-adoption-analysis-sequence.mmd) / [rendered SVG](../architecture/diagrams/s3-m2-adoption-analysis-sequence.svg) |
| Design class diagram | [Editable Mermaid](../architecture/diagrams/s3-m2-adoption-design-class.mmd) / [rendered SVG](../architecture/diagrams/s3-m2-adoption-design-class.svg) |
| Design sequence diagram | [Editable Mermaid](../architecture/diagrams/s3-m2-adoption-design-sequence.mmd) / [rendered SVG](../architecture/diagrams/s3-m2-adoption-design-sequence.svg) |
| Problem, candidates, selected approach, rationale, actual before/after changes and implementation/test decisions | [Pattern decision](../architecture/S3-M2-adoption-pattern-decision.md) |
| Dated findings, attributable M1 follow-up and remaining freeze acceptance | [Cross-module review ledger](S3-M2-day6-cross-module-review.md) |

Analysis participants represent boundary/control/entity responsibilities. Design
participants are the real controller, transactional service, JDBC store,
identity/audit integration and exception mapping. The current selection flag may
change; immutable content, item values and release metadata do not. The diagrams
and decision do not invent a GoF Factory Method/Prototype/Builder implementation.

The rendered SVGs are generated from the corresponding Mermaid sources. The return
bundle also supplies PNG previews, a vector PDF and the renderer/validation receipt.
Those exports are diagrams of the implementation, not screenshots pretending to be
application or business-acceptance evidence.

## Review-driven corrections made today

Two `missingAllergenCodes` descriptions in the [S3 OpenAPI candidate](../contracts/s3-impact-review-publication-api-v1.yaml)
incorrectly said the allergens were absent from the proposed formula. The existing
[M1 strategy](../../backend/src/main/java/com/spectrace/impact/application/strategy/IngredientSpecImpactStrategy.java)
actually computes allergens derived from adopted N+1 that are absent from the old
published label N's CONTAINS declarations. Both descriptions now state that meaning.
Only those two descriptions change: routes, fields, required sets, constraints,
permissions, status codes, version 1.0.0 and the pending-acceptance marker remain.

The [Day 3 golden evidence](S3-M2-day3-soy-golden-v1.md) now names the actual shared
CSV read by the relationship and strategy tests, rather than an obsolete JSON claim.
Its personal scheduled-runner paragraph was removed in the direction of M1's existing
course-evidence review finding. Golden CSV data, production code, tests, schema,
frontend, CI and security configuration are unchanged by Day 6.

## Verification performed on this delivery

| Check | Actual result |
| --- | --- |
| Existing `S3ImpactApiContractTest`, `OpenApiContractTest`, `SharedApiErrorContractTest` | Local offline Maven execution on 2026-10-06: **12 tests / 3 suites, zero failures/errors/skips; BUILD SUCCESS**. Checks the candidate and frozen S2/error boundaries. |
| Mermaid parser and real headless-browser render | All four class/sequence sources parsed and rendered with isolated Mermaid 12.1.0, using its [official API](https://mermaid.js.org/config/usage.html); SVGs and preview/PDF exports were generated from the actual sources. Source/export hashes are recorded in the return bundle. |
| Source/method/test trace | Real Java symbols and 35 named existing test methods in the SAD were checked; the decision distinguishes historical code changes from rejected alternatives. |
| Scope/wire comparison | The production/test/frontend/CI/security trees are unchanged against `20a41e4`. OpenAPI content differs only in the two described strings; golden data is unchanged. |

Local full MySQL/Compose/browser regression was not repeated for this documentation
change. Current-head remote CI results, link/diff checks and the final delivery commit
are recorded in the PR and return receipt after publication, rather than copying
the earlier PR61/PR62 results into a new-current-head claim.

## Acceptance still required

M1's Jira acknowledgement of incorporated PR48 corrections remains real. The review
ledger also records the later exact-head PR61 APPROVED and PR62/PR63 COMMENTED
reviews on 2026-10-07; those scoped decisions must not be omitted or promoted into
complete freeze acceptance. The ten unresolved PR48 threads are a Day 6 snapshot,
not a claim that they were resolved by later merges. No explicit complete exact-version
M1/M3/M4/M5 freeze approval is recorded here. The shared GET/ReviewTask-assignee policy,
M3 consumer decisions and M5 transaction/idempotency acceptance still need attributable
owner decisions before freeze.

The OpenAPI remains `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`; the SOY oracle remains
a proposed scenario, not a real business V2 release. Full impact-run/review/approval/
label-publication and accepted merged-current-main evidence are separate gates.
The diagrams cover the available Jira/work-order A07 scope; formal rubric acceptance
must be established through the actual course assessment.

SCRUM-57 and SCRUM-48 remain In Progress. No merge, deployment, business publication,
contract freeze, team approval or Sprint closure is asserted by this delivery.
