# S3 M2: immutable formula creation and specification adoption decision

Owner: Cai Runchen / M2. Work item: SCRUM-57, Day 6 under SCRUM-48.
Date: 2026-10-06 (Asia/Shanghai).
Status: **implementation decision recorded; Day 6 documentation is a review candidate**.

This record explains the design implemented by the Day 4/Day 5 catalog adoption
changes. It adds design evidence, not another implementation or test run. The
inspected baseline is `20a41e48c71e0851bf9c71de9ba580cf1623323a`; its most recent
production change for this decision is `93ee8173e6c023cfd4ba6a1ffa962f00808f01a7`.
The [Day 6 evidence index](../evidence/S3-M2-day6-a07-evidence.md) identifies the
delivery, and the [cross-module review ledger](../evidence/S3-M2-day6-cross-module-review.md)
records actual review state. Neither this record nor a diagram freezes the contract.

## Design problem and constraints

When a material's newer specification becomes eligible, an authorized caller must
explicitly adopt it for a product. The product receives a fresh released formula
snapshot while earlier formula items, material/specification references, release
actors and release timestamps retain their historical values. Existing labels
continue to reference the formula on which they were published. Merely releasing a
specification must not rewrite every product that uses that material.

The executable action is `POST /api/catalog/products/{id}/formula-adoptions` with
`sourceFormulaVersionId` and `targetSpecificationVersionId`. These are exact
version IDs, not an instruction to discover whichever version is latest during a
later step. The [adoption API candidate](../contracts/specification-adoption-api-v1.md)
defines the request, response and error contract.

The decision must preserve these constraints:

1. The source belongs to the product, is RELEASED, matches the product's current
   pointer, and has `is_current_released = Y` when checked under the product lock.
2. The target is RELEASED and effective in UTC, belongs to a material present in
   the source, and is newer than every replaced item specification. An already
   adopted target and a stale source are conflicts. A replaced historical
   specification may be RETIRED; retained specifications must remain eligible.
3. Every source item gets a fresh item ID. Material, sequence number, quantity and
   unit are copied exactly, including repeated materials, non-contiguous order
   values and null quantity/unit pairs. Only specification references for the
   target material change.
4. Creation, release, current-pointer selection and all three audit events commit
   together. Either permission missing, invalid input or a persistence/audit
   failure prevents a partial committed result.
5. Allocation is the highest allocated product version plus one. If current
   released version 1 coexists with draft version 7, adoption creates version 8
   and preserves draft 7; “N+1” is not permission to reuse a draft number.
6. Canonical tables, JDBC persistence and module ownership remain. This change
   does not create a migration, a second persistence model or an automatic label
   publication process.

## Existing design reused

The [S1 catalog design](../s1-m1/DESIGN_AND_HANDOFF.md) already selected a transaction
script/application service, parameterized JDBC storage, product-row serialization,
version allocation and current-selection metadata separate from historical content.
Those mechanisms existed before explicit adoption. This record does not attribute
their original introduction to SCRUM-57 or describe the old catalog as unstructured.

The [S1 M2 SAD](S1-M2-sad.md) and
[analysis-to-design trace](S1-M2-analysis-design.md) establish controller/application
boundaries, owner-supplied snapshots/ports, explicit error semantics and a Strategy
registry for validation rule evaluation. That Strategy decision concerns multiple
rule evaluators; it is not evidence of an adoption Strategy implementation. The
[JDBC decision](JDBC_VS_JPA_DECISION.md) also supports retaining the executable
persistence baseline rather than introducing an ORM for this action.

Catalog's concrete structure has an important limit: `CatalogService` depends
directly on the concrete `CatalogStore`. There is no newly introduced catalog
persistence-port interface. `CatalogIntegration` is the existing application port
for actor/permission and audit integration; its request adapter calls the owning
identity and audit application services. “Gateway” below describes the JDBC
storage role, not a claim of a new GoF class hierarchy or a fully abstract catalog
repository boundary.

## Candidates and tradeoffs

For terminology, Fowler's [Service Layer](https://martinfowler.com/eaaCatalog/serviceLayer.html)
places callable operations and response coordination at an application boundary;
[Transaction Script](https://martinfowler.com/eaaCatalog/transactionScript.html)
organizes an operation's business steps in a procedure, optionally using a
database wrapper. The mapping below is an interpretation of the inspected code,
not a new pattern implementation delivered on Day 6. The store spans several
catalog tables, so it is not a strict single-table Table Data Gateway.

| Candidate | Fit to this problem | Decision |
| --- | --- | --- |
| Mutate the source formula's item references in place | Smaller write set, but historical formula and published-label evidence would acquire different meaning under the same version ID. | Rejected alternative. This was not the implemented pre-Day 4 catalog behavior. |
| Have the client reconstruct all items, then invoke generic create and release separately | Reuses existing actions, but reconstructing an exact server snapshot becomes a caller responsibility; the two requests have separate transaction boundaries and lack a dedicated source-to-target adoption audit link. | Existing generic flow retained for ordinary formula maintenance; not selected as the explicit adoption operation. |
| GoF Factory Method | Appropriate when subclasses choose which product implementation a creation method instantiates. The current task creates one relational formula representation and does not require such a hierarchy. | Not introduced. A private allocation helper or a method that inserts a formula is not, by itself, GoF Factory Method. |
| GoF Prototype | Could describe cloning interchangeable in-memory objects with an explicit clone contract. Here copying means inserting owned formula/item rows while deliberately retaining material/specification references. | Not introduced. Snapshot row copying is not a deep clone of the material, specification, label or provenance graph. |
| GoF Builder | Useful for assembling several complex representations in stages. This action has a validated two-ID command and one stored representation; sequence values must come from the existing snapshot. | Not introduced. Command-record validation is not a Builder/director implementation. |
| GoF Strategy for adoption policies | Useful if there are multiple supported, interchangeable adoption algorithms. This baseline has one explicit specification-replacement rule and no adoption strategy interface or registry. | Deferred until a real variation point exists. Existing validation/impact strategies remain separate concerns. |
| Application Service / Transaction Script, immutable snapshot creation and JDBC Gateway-style storage | Coordinates the single use case, preserves the canonical representation, and makes source validation, copying, selection and audit atomic using existing transaction/lock mechanisms. | Selected and implemented. |

The selected approach minimizes changes to the running catalog while putting the
new business operation at the server transaction boundary. Its tradeoff is that
the service owns procedural policy and the store exposes database-shaped rows.
This is less extensible than a separate family of version-creation policies, but
no current requirement justifies that family. Adding one merely to label a diagram
would obscure the implemented behavior and introduce unverified changes.

## Selected collaboration and implementation decisions

The [catalog controller](../../backend/src/main/java/com/spectrace/catalog/interfaces/web/CatalogController.java)
binds the request to `CatalogCommands.AdoptSpecification` and invokes
`CatalogService.adoptSpecification(String, AdoptSpecification)`. It returns HTTP
201 with the existing catalog `snake_case` detail. It does not copy items or issue SQL.
The [command](../../backend/src/main/java/com/spectrace/catalog/domain/CatalogCommands.java)
checks the required source/target IDs; the application service checks domain state.

The [application service](../../backend/src/main/java/com/spectrace/catalog/application/CatalogService.java)
requires `DATA.MAINTAIN` and `FORMULA.RELEASE`, then holds a write transaction while
checking the exact source, locking material/specification rows and validating the
target. It calls `CatalogStore.adoptFormula(...)`, records creation, releases the
new version through `CatalogStore.releaseFormula(...)`, records release and records
adoption before returning the new detail. It calls the store directly inside this
transaction; it does not simulate one atomic operation by making two HTTP calls.

The [store](../../backend/src/main/java/com/spectrace/catalog/infrastructure/CatalogStore.java)
has two distinct creation paths:

| Actual method | Responsibility |
| --- | --- |
| `formula(Formula, String)` | Ordinary caller-specified formula creation; assigns item order from the command list. |
| `adoptFormula(String, String, String, String, String, String)` | Reads the source item snapshot and inserts new item rows, preserving original sequence, quantity, unit and material; changes the specification only for the target material. |
| private `formulaVersion(String, String, String)` | Shared allocation/insert helper: latest allocated version plus one, fresh UUID, initial DRAFT/N row. This is ordinary implementation reuse, not GoF Factory Method. |
| `releaseFormula(String, String, String)` | Clears the previous current flag, releases/selects the new version and updates `product.current_formula_version_id`. |

The transient DRAFT row is created and released within the same adoption
transaction. A successful response represents a committed RELEASED version, not
a draft awaiting a separate adoption release request. The source's current flag
and derived `current_formula_product_id` change as selection metadata; its content,
release metadata and items retain their prior values. Labels and declarations are
not cloned, advanced or edited by this action.

“Immutable snapshot” is an API/write-policy statement here. It does not imply that
the returned Java `Map` values are immutable types or that all external SQL is
blocked by database immutability triggers. Supported catalog write operations
append new content and do not expose edits to released formula items.

The [integration port](../../backend/src/main/java/com/spectrace/catalog/application/CatalogIntegration.java)
adds `auditSpecificationAdoption(...)` with a compatibility default that records
the existing catalog event. The
[request adapter](../../backend/src/main/java/com/spectrace/catalog/infrastructure/RequestCatalogIntegration.java)
overrides it to call
[`AuditApplicationService.recordSpecificationAdoptionEvent(...)`](../../backend/src/main/java/com/spectrace/audit/application/AuditApplicationService.java).
That method requires an existing transaction with `Propagation.MANDATORY` and
stores `sourceFormulaVersionId`, `targetSpecificationVersionId` and
`newFormulaVersionId` in the audit JSON. All three events use the target
specification's existing provenance. Payload evidence is supplied by this actual
adapter path; the interface default alone does not promise those JSON fields.

## Before and after: historical explicit-adoption design

The following comparison is about committed history, rather than the rejected
in-place update alternative above.

| Aspect | Before Day 4: `e53f7b0f0fb7c9e0038dbbbe4193f953edab8e35` | After Day 4, retained in Day 5/Day 6 baseline |
| --- | --- | --- |
| Public operation | `POST /api/catalog/formulas` followed by `POST /api/catalog/formulas/{id}/release`; no formula-adoption action. | Additive `POST /api/catalog/products/{id}/formula-adoptions`. Generic actions remain. |
| Control | `CatalogService.create(Formula)` and `releaseFormula(...)` are separate transactions; the caller supplies formula items. | `adoptSpecification(...)` controls validation, exact source copying, release, selection and audit in one transaction. |
| Creation | `CatalogStore.formula(...)` inserts caller-specified items with `i + 1` sequence values. | `adoptFormula(...)` copies stored sequence/quantity/unit/material values; `formula(...)` remains the ordinary creation path. Both share private `formulaVersion(...)`. |
| Concurrency token | Generic release compares `Release.expectedCurrentFormulaId` with the locked product pointer. | Adoption checks its exact source against the locked pointer and the source current flag before writes. |
| Evidence | Existing creation and release events record the generic operations. | Adds a dedicated adoption event with source/target/new IDs through the request audit adapter. |
| Historical content | Released formula content is already preserved; previous selection may change. | That rule is retained and applied to an explicit specification change; labels remain pinned to the original formula. |

The “before” is the parent of the original implementation commit
`f310c0f9ca0257155b620eda8b4425c862e4a4d2`, so both the missing action and the
already-existing transaction/version mechanisms are inspectable. Day 4 was later
integrated with the then-current main; its documentation baseline is `d6ca803`.
The [Day 4 record](../evidence/S3-M2-day4-specification-adoption.md) distinguishes
the original change, integration and prior verification evidence.

## Before and after: Day 5 lock-order correction

This is a second, concrete implementation decision; it does not retroactively
claim that Day 4 had the corrected material ordering.

At Day 4 baseline `d6ca803`, ordinary formula creation/adoption could lock
specifications before inserting new formula items. Item insertion then required
material foreign-key locks. Concurrent `create(Specification)` already held the
material row while waiting on the latest specification row. Sorting specification
IDs alone could not remove that cross-table cycle: formula write
`specification -> material` versus specification creation `material -> specification`.

Day 5 code commit `93ee8173e6c023cfd4ba6a1ffa962f00808f01a7` adds
`lockMaterials(...)` before specification locking in **both**
`CatalogService.create(Formula)` and `adoptSpecification(...)`. It uses a `TreeSet`
of material IDs and locking `store.get(MATERIAL, materialId, true)` calls. Product
and source checks stay in place, and adoption's target/source specification IDs
remain sorted. Ordinary formula release is unchanged: it does not insert a new
set of formula items.

| Verified race | Day 4 observation recorded by Day 5 evidence | Corrected expectation/test |
| --- | --- | --- |
| New specification creation versus adoption on the same material | Creator HTTP 500 with a MySQL deadlock; adoption HTTP 201. | Both HTTP 201 in `creatingTheNextSpecificationAndAdoptingTheReleasedTargetBothCommit`. |
| New specification creation versus ordinary formula creation | Creator HTTP 500 with a MySQL deadlock; formula HTTP 201. | Both HTTP 201 in `creatingAFormulaAndTheNextSpecificationBothCommit`. |

These controlled regressions prove the reported races, not universal deadlock
freedom. The [Day 5 record](../evidence/S3-M2-day5-mysql-adoption-guards.md) describes
the observations and bounded coordination; the
[material-lock tests](../../backend/src/test/java/com/spectrace/FormulaAdoptionMaterialLockMySqlTest.java)
exercise real HTTP and independent database transactions.

The relevant historical code can be checked without interpreting a diagram:

```sh
git show e53f7b0f0fb7c9e0038dbbbe4193f953edab8e35:backend/src/main/java/com/spectrace/catalog/application/CatalogService.java
git diff e53f7b0f0fb7c9e0038dbbbe4193f953edab8e35 f310c0f9ca0257155b620eda8b4425c862e4a4d2 -- backend/src/main/java/com/spectrace/catalog
git diff d6ca803 93ee8173e6c023cfd4ba6a1ffa962f00808f01a7 -- backend/src/main/java/com/spectrace/catalog/application/CatalogService.java
```

## Decision-to-code/test traceability

The tests below exist at the inspected baseline. They are evidence paths for
review; this documentation task has not rerun them.

| Decision/invariant | Code | Executable evidence |
| --- | --- | --- |
| Exact command and additive 201 action | `CatalogCommands.AdoptSpecification`; `CatalogController.adoptSpecification` | [`CatalogSpecificationAdoptionHttpTest`](../../backend/src/test/java/com/spectrace/CatalogSpecificationAdoptionHttpTest.java): `mapsExplicitAdoptionRequestAndReturnsCreatedCatalogSnapshot`; required-field/malformed-body tests. |
| Both permissions required before writes | `CatalogService.adoptSpecification`; `CatalogIntegration.requireActor` | [`CatalogSpecificationAdoptionTest`](../../backend/src/test/java/com/spectrace/CatalogSpecificationAdoptionTest.java): `requiresBothDataMaintenanceAndFormulaReleaseBeforeReadingOrWriting`; [`FormulaSpecificationAdoptionMySqlTest`](../../backend/src/test/java/com/spectrace/FormulaSpecificationAdoptionMySqlTest.java): `missingIdentityAndInsufficientPermissionCannotWrite`. |
| Released/current source and exact product ownership | Product/source locking and validation in `adoptSpecification` | Unit tests `sourceMustBelongToTheRequestedProduct`, `sourceMustBeReleased`, `staleSourceCannotReplaceTheCurrentFormula`, `currentPointerWithAnInconsistentSourceFlagCannotBeAdopted`. |
| Eligibility of the resulting snapshot, without rewriting historical specs | Target/retained-spec checks and version comparisons in `adoptSpecification` | Unit tests `targetMustBeReleasedAndEffective`, `untouchedSpecificationMustRemainReleasedAndEffective`, `releasedTargetCanReplaceARetiredHistoricalSpecification`, `alreadyAdoptedOrOlderSpecificationCannotCreateANewVersion`. |
| Fresh version/items, exact copy and labels unchanged | `CatalogStore.adoptFormula`, `formulaVersion`, `releaseFormula` | MySQL `explicitAdoptionReleasesNextVersionAndPreservesHistoricalContentAndPublishedLabel`. |
| Existing drafts are neither overwritten nor renumbered | `formulaVersion` under the held product lock | MySQL `adoptionAllocatesAboveHighestExistingDraftAndPreservesThatDraftExactly`. |
| One winner for same source; no loser residue | Locked product pointer/current check and atomic transaction | MySQL `duplicateOriginalSourceAndAlreadyAdoptedCurrentSourceCannotWriteAgain`, `simultaneousSameTargetRequestsCommitOneWinnerWithoutLoserRows`, `simultaneousDifferentEligibleTargetsCommitOnlyTheWinningSpecification`. |
| Audit links and rollback include the real insertion | `CatalogIntegration.auditSpecificationAdoption`; request adapter; MANDATORY audit service | MySQL `auditFailureAfterRealInsertionRollsBackAndTheSameRequestCanRetryWithoutDuplicateAudit`; successful-adoption audit payload assertions. |
| Material ordering aligns two write operations with spec creation | `lockMaterials` called from `create(Formula)` and `adoptSpecification` | Both named `FormulaAdoptionMaterialLockMySqlTest` races above. |
| Real adoption supplies proposed N+1 to the golden integration | Catalog HTTP action consumed by existing discovery/ingredient-spec strategy components | [`IngredientSpecImpactStrategyMySqlTest`](../../backend/src/test/java/com/spectrace/impact/IngredientSpecImpactStrategyMySqlTest.java): `soyLecithinInChocolateBaseClassifiesEveryRelevantProductAsTheGoldenExpects`, `beforeAdoptionEveryRelevantProductIsAdoptionPendingAndNothingIsWritten`. |
| Canonical safe errors and framework statuses | `CatalogErrors` | HTTP `preservesBusinessStatusAndCanonicalErrorEnvelope`, `auditAdapterFailureReturnsGenericErrorWithoutInternalDetails`, and content-type/method rejection tests. |

The golden integration uses actual successful adoptions for relevant products and
retains the published label's pinned N as comparison evidence against product N+1.
It is not evidence that this action runs the full impact workflow, persists review
tasks, approves replacement labels or publishes them. Those responsibilities remain
in their owning modules and work items.

## A07 evidence scope and review limits

This record supplies the real design problem, evaluated pattern alternatives,
selected approach, rationale, two distinguishable before/after decisions and
code/test links. The accompanying [Day 6 SAD](S3-M2-adoption-sad.md) supplies the
[analysis class](S3-M2-adoption-sad.md#analysis-class-diagram),
[analysis sequence](S3-M2-adoption-sad.md#analysis-sequence-diagram),
[design class](S3-M2-adoption-sad.md#design-class-diagram) and
[design sequence](S3-M2-adoption-sad.md#design-sequence-diagram) diagrams. The
evidence index and review ledger supply the remaining artifact links and actual
review status.

The available Jira/work-order context identifies this delivery scope, but does not
establish the complete formal A07 assessment rubric. An implemented service/storage
collaboration is not automatically proof of a required GoF-pattern demonstration.
If the formal rubric requires a specific GoF creational pattern, that gap needs
explicit assessment and an authorized, justified design change; renaming these
classes in a diagram would not satisfy it.

Prior Day 4/Day 5 verification reported in their linked evidence remains baseline
evidence. No new Day 6 runtime result, current-head CI pass, reviewer approval,
business specification release or teacher acceptance is asserted here. Cross-module
review and any formal freeze are determined by real responses recorded in the
review ledger, not by this document's existence.
