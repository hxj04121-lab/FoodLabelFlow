# S3 M2 SAD: explicit specification adoption

Owner: Cai Runchen / M2. Work item: SCRUM-57 under SCRUM-48.
Prepared: 2026-10-06 (Asia/Shanghai).
Source baseline: `20a41e48c71e0851bf9c71de9ba580cf1623323a` (Day5).
Status: A07 design evidence prepared for review; review and acceptance decisions belong in
the [Day6 review ledger](../evidence/S3-M2-day6-cross-module-review.md).

This document describes implemented Catalog adoption from a released specification
to a fresh, released formula snapshot. The analysis models identify conceptual
responsibilities; the design models map them to existing Java classes, JDBC tables
and executable tests. This document adds design evidence for the existing runtime.
Test and rendering outcomes are recorded in the Day6 evidence index; neither this
design description nor its diagrams imply reviewer acceptance.

The current HTTP behavior is described in the existing
[specification adoption contract candidate](../contracts/specification-adoption-api-v1.md).
The [pattern decision](S3-M2-adoption-pattern-decision.md) explains the existing
design choices without inventing additional GoF implementations. The
[Day6 evidence index](../evidence/S3-M2-day6-a07-evidence.md) assembles the review,
verification and A07 submission links.

## Use case and scope

**UC-M2-SPEC-ADOPT: adopt an explicit newer specification for one product.**
The actor supplies the exact current released formula ID and the exact target
specification ID. The application copies the source items, replaces every item
reference for the target's supplier material, releases the new formula, selects
it as current, and records three audit events in the same transaction.

The primary actor is an authenticated maintainer with both `DATA.MAINTAIN` and
`FORMULA.RELEASE`. Identity and audit are supplied by the existing integration
adapter. Catalog owns the formula/material/specification operations in this flow;
identity and audit remain behind their existing application seam.

Preconditions checked by the implementation:

1. The request identifies a product and contains nonblank, bounded source/target
   IDs. The configured integration adapter authenticates the actor and checks
   both permissions before Catalog reads or writes.
2. The source belongs to this product, is `RELEASED`, equals
   `product.current_formula_version_id`, and has `is_current_released = 'Y'`.
   It contains at least one item.
3. The target is `RELEASED`, has `effective_date` on or before the current **UTC**
   date, and belongs to a material present in the source. Each source item's
   material agrees with its pinned specification's material.
4. None of the replaced items already pins the target, and the target's
   `version_number` is strictly greater than every specification being replaced.
   Specifications for untouched items are also released and effective.
   A replaced historical specification may be `RETIRED`.

The target specification's existing `data_provenance_id` becomes the new formula's
provenance. The request supplies no actor, formula version number, new item IDs,
or a new provenance ID.

### Main flow

1. Spring MVC binds `CatalogCommands.AdoptSpecification` from the JSON body.
   `CatalogController.adoptSpecification` delegates the product ID and command
   to the transactional `CatalogService.adoptSpecification`.
2. The service checks input and resolves `CatalogIntegration`. It requires both
   permissions using the existing identity adapter, retaining the authenticated
   actor ID for new formula and audit records.
3. Lock the product row, then the source formula row. Check the exact current
   selection and source state after obtaining these locking reads. Read the
   source's ordered item snapshot.
4. Lock every distinct source material in ascending ID order. Lock the target
   and all source specifications in ascending, distinct ID order. Validate the
   source/target bindings and eligibility under those locks.
5. `CatalogStore.adoptFormula` allocates `highest existing formula version + 1`,
   inserts a fresh `DRAFT` formula and copies every source item. New formula/item
   IDs are UUIDs. All items for the target material pin the requested target;
   other specification references and all item values remain exact copies.
6. Record `FORMULA_CREATED`, release the new formula and update the product's
   current formula selection, then record `FORMULA_RELEASED` and
   `FORMULA_SPECIFICATION_ADOPTED`.
7. Read the new formula detail and its items. The Spring transaction interceptor
   commits all Catalog writes and all three audit events before MVC emits
   `201 Created` with the existing snake_case `Map` response shape.

Success is a logical successor to source N. Its numeric version is not always
N+1: an already allocated version-7 draft makes the new version 8, even when
the released source is version 1. The existing draft is preserved.

### Exception flows and error semantics

These are the Catalog handler statuses and stable codes, not free-text message
contracts. Failures stop at the detecting step. Validation and authorization
rejections create no formula, item or adoption audit rows; runtime exceptions
after writes trigger transaction rollback.

| Condition | HTTP status / code | Detecting implementation |
| --- | --- | --- |
| Missing/malformed JSON, missing/blank/oversized IDs, null application request, empty source items | 400 `INVALID_REQUEST` | Command compact constructor, service input checks, `CatalogErrors.invalid` |
| Missing or unknown request identity | 401 `AUTHENTICATION_REQUIRED` | `RequestCatalogIntegration.requireActor` |
| Actor lacks either required permission | 403 `AUTHORIZATION_DENIED` | `RequestCatalogIntegration.requireActor` / `AuthorizationService.requirePermission` |
| Requested product is absent | 404 `RESOURCE_NOT_FOUND` | `CatalogStore.get(PRODUCT, ..., true)` |
| Source pointer changed, or source current flag is inconsistent | 409 `CURRENT_FORMULA_CHANGED` | `CatalogService.adoptSpecification` |
| A matching source item already pins the target | 409 `SPECIFICATION_ALREADY_ADOPTED` | `CatalogService.adoptSpecification` |
| Database integrity constraint failure | 409 `DATA_CONFLICT` | `CatalogErrors.conflict` |
| Body source or specification reference is absent | 422 `INVALID_REFERENCE` | `CatalogService.adoptionReference` translates the Catalog 404 |
| Source belongs to another product / source is not released | 422 `SOURCE_FORMULA_PRODUCT_MISMATCH` / `SOURCE_FORMULA_NOT_RELEASED` | `CatalogService.adoptSpecification` |
| Target material is absent, or an item's material/specification binding disagrees | 422 `SPECIFICATION_MATERIAL_MISMATCH` | `CatalogService.adoptSpecification` |
| Target or an untouched specification is not released / not effective | 422 `SPECIFICATION_NOT_RELEASED` / `SPECIFICATION_NOT_EFFECTIVE` | `CatalogService.releasedAndEffective` |
| Target is not newer than every replaced specification | 422 `SPECIFICATION_VERSION_NOT_NEWER` | `CatalogService.adoptSpecification` |
| Unexpected service, persistence or audit runtime exception | 500 `INTERNAL_ERROR` | Transaction rollback, then `CatalogErrors.internal` |
| No configured Catalog identity/audit adapter | 503 `CATALOG_INTEGRATION_UNAVAILABLE` | `CatalogService.integration` |

Catalog-generated errors use the existing four-field `ApiError`: `code`,
`message`, `traceId`, `evidenceId`. The last two are null when no real identifiers
exist. The 500 response contains a generic message; the exception detail is
logged server-side. Spring's unsupported method/media-type failures keep 405/415;
this document does not claim those framework responses use the Catalog envelope.

A repeated POST with the original source after successful adoption returns
409 `CURRENT_FORMULA_CHANGED`. A POST using the new current formula with the same
target returns 409 `SPECIFICATION_ALREADY_ADOPTED`. Concurrent requests for the
same source serialize on the product: one can commit, and the loser checks the
refreshed current selection and writes no losing snapshot or audits. There is no
automatic retry or idempotency-key implementation in this endpoint. A request
whose transaction rolled back can be retried with the same body while its source
remains current; a committed request requires refreshing the source.

## Analysis class diagram

[Mermaid source](diagrams/s3-m2-adoption-analysis-class.mmd).

`AdoptionBoundary`, `AdoptionControl` and `IdentityBoundary` express analysis
responsibilities. They are not proposed runtime classes. Product, formula,
item, material, specification, provenance, published label and evidence represent
the business relationships already stored by the application. A formula item pins
an exact specification version; a published label pins an exact historical
formula. Current pointers are selection relationships rather than content edits.

## Analysis sequence diagram

[Mermaid source](diagrams/s3-m2-adoption-analysis-sequence.mmd).

The successful interaction creates and releases a new snapshot explicitly. Its
alternative flows distinguish identity/permission rejection, source/target
precondition rejection, and persistence/audit failure with complete rollback.
The published-label relationship stays intact throughout the use case.

## Design class diagram

[Mermaid source](diagrams/s3-m2-adoption-design-class.mmd).

The concrete implementation uses the existing Controller, Service, Store and
Integration seam. `CatalogCommands.AdoptSpecification` is an immutable input
record, while Catalog reads and responses use JDBC `Map` rows and lists. There
is no typed `FormulaVersionDetail`, new domain entity implementation, new
repository abstraction, migration, or substitute persistence engine in this
adoption slice.

`RequestCatalogIntegration` delegates request identity to `IdentityService` and
permissions to `AuthorizationService`; both work with `AuthenticatedActor`.
Its existing property gate is `spectrace.dev-external-auth.enabled=true`.
An unavailable adapter fails closed with 503. This evidence does not authorize
enabling that property or treating external headers as a production login design.

## Design sequence diagram

[Mermaid source](diagrams/s3-m2-adoption-design-sequence.mmd).

The Spring transaction interceptor owns begin/commit/rollback. `CatalogStore`
executes SQL through the existing `JdbcTemplate`; it does not manually manage
the transaction. `CatalogService.adoptionReference` is a service-side wrapper
around `CatalogStore.get`, translating missing body references into 422.
Every guard or write can exit at its detecting step. The diagram's failure
branch describes that early exit and rollback, rather than executing later calls
after an exception.

## Analysis to design mapping

| Analysis responsibility | Actual design symbol | Persistent or observable result |
| --- | --- | --- |
| Adoption boundary and explicit request | `CatalogController.adoptSpecification`; `CatalogCommands.AdoptSpecification` | POST `/api/catalog/products/{id}/formula-adoptions`; 201 detail or stable error |
| Adoption control | `CatalogService.adoptSpecification` | Guards, lock ordering, snapshot/release/audit orchestration in one transaction |
| Identity boundary | `CatalogIntegration.requireActor`; `RequestCatalogIntegration.requireActor` | Authenticated actor, both permissions, 401/403 |
| Product and source selection | `CatalogStore.get(PRODUCT/FORMULA, ..., true)` | Serialized current-pointer and source-state check |
| Material/specification consistency | `CatalogService.lockMaterials`, `adoptionReference`, `releasedAndEffective` | Sorted locking reads, bound and eligible references |
| New formula and exact item copy | `CatalogStore.adoptFormula`; private `formulaVersion` | New DRAFT at MAX+1; fresh item IDs; original item values |
| Release and select current | `CatalogStore.releaseFormula` | New RELEASED/Y and release facts; old selection N; product pointer switched |
| Atomic evidence | `CatalogIntegration.audit` / `auditSpecificationAdoption`; `AuditApplicationService.recordCatalogEvent` / `recordSpecificationAdoptionEvent` | Three same-transaction events, dedicated three-ID payload |
| Error presentation | `CatalogFailure`, `CatalogErrors`, `ApiError` | Stable status/code with safe generic unexpected-error message |
| Published-label history | No adoption write to label/publication tables | Existing label and declarations remain pinned to N |

## Business invariants and transaction design

| Invariant | Enforcing behavior and practical boundary |
| --- | --- |
| Explicit actor and explicit versions | Actor derives from identity; both permissions precede Catalog access. Source and target are caller-selected IDs, not an inferred latest specification. |
| Exact current released source | Product/source locking reads check product ownership, release state, pointer and Y flag together. A stale or inconsistent selection is rejected. |
| Eligible successor references | Target and untouched specifications must be released and effective by UTC date. Material bindings are exact. Target version must exceed each replaced version; retired replaced history is allowed. |
| Exact snapshot copy | Preserve every source row's material, sequence number, quantity value and quantity unit, including null values and repeated target-material rows. Preserve untouched specification IDs. Only new row IDs and replaced references differ. |
| Historical content preservation | Old formula identity, content/provenance, lifecycle and release actor/time and all old items stay unchanged. Old `is_current_released` changes Y to N; generated `current_formula_product_id` consequently changes to null. These are current-selection metadata and are not immutable content. |
| One selected released formula | New formula becomes RELEASED/Y; product points to it. Existing generated-key unique constraint enforces at most one current released formula per product. Existing unrelated drafts remain unchanged. |
| Monotonic allocation | Product locking serializes Catalog formula writes; highest existing version locking read allocates MAX+1, including pre-existing drafts. |
| Atomic audit | New formula/items, old/new selection state, product pointer and all three events commit or roll back together. Both Audit application methods require an existing transaction via `Propagation.MANDATORY`. |
| Independent label publication | Adoption does not mutate `current_published_label_version_id`, label rows, declarations, validation, approval or publication. A product without a published label may still adopt; downstream discovery has its separate missing-label guard. |

### Lock order and proven concurrency scope

The adoption order is **product -> source formula -> distinct materials sorted by
ID -> distinct target/source specifications sorted by ID -> version allocation and
item inserts**. Each `CatalogStore.get(..., true)` issues `SELECT ... FOR UPDATE`;
these are exclusive row locking reads. The common sorted order is not a shared
database lock. The highest-formula version read in `formulaVersion` is also a
locking read and occurs while the product serialization lock is held.

Ordinary `CatalogService.create(Formula)` likewise locks its product, then all
distinct materials in sorted order before its sorted specification checks.
Specification creation already locks its material before locking the latest
specification to allocate a new version. Day5 aligned these two formula-item
INSERT paths with that material-before-specification order because an item FK
check needs a material lock. The two real MySQL regression cases reproduce the
old material/specification cycle and validate the corrected order.
`releaseFormula` was not broadened: it does not insert new item rows in this flow.

This is evidence for the implemented same-product race and the two exercised
cross-operation races. It is not a proof that every transaction in every module
is globally free from deadlock, nor that an entire impact run is serialized with
adoption. The same-product loser performs locking reads after the winner commits
and then fails the source-current guard. No losing version allocation or audit
event is retained.

### Audit and module boundaries

The events appear in the service in this order: `FORMULA_CREATED`,
`FORMULA_RELEASED`, `FORMULA_SPECIFICATION_ADOPTED`. For the configured
`RequestCatalogIntegration`, the third event records JSON keys
`sourceFormulaVersionId`, `targetSpecificationVersionId`, and
`newFormulaVersionId`; its entity ID is the new formula, and its actor/provenance
are the real actor and target specification's provenance. Generic Catalog audit
events retain their existing empty JSON payload.

`CatalogIntegration.auditSpecificationAdoption` has a compatibility default that
delegates to a generic event. The concrete request adapter overrides it to record
the three references. Consequently the complete reference-payload claim here is
about that configured adapter, which the HTTP/MySQL tests exercise. Other custom
adapters must implement the same transactional behavior to provide equal evidence.

The preserved published label N remains the basis for downstream current-label
comparison; the adopted product formula supplies proposed composition. The
existing label adapter can report the old label snapshot as not eligible for
current-formula validation after the product formula changes, while its stored
label/declaration data remains preserved. Day5's strategy integration verifies
this distinction. Adoption itself neither runs the full impact API nor creates
review findings, review tasks, approvals or replacement publications.

## Source and executable traceability

Paths are relative to the repository. Symbols below were checked against the
Day5 baseline. Test entries name existing executable assertions. The evidence
index records which checks ran for Day6 and their actual outcomes.

| Source | Symbols to inspect |
| --- | --- |
| [CatalogController.java](../../backend/src/main/java/com/spectrace/catalog/interfaces/web/CatalogController.java) | `adoptSpecification`, POST mapping and `@ResponseStatus(CREATED)` |
| [CatalogCommands.java](../../backend/src/main/java/com/spectrace/catalog/domain/CatalogCommands.java) | `AdoptSpecification`, `required` (source 120, target/product 100 characters) |
| [CatalogService.java](../../backend/src/main/java/com/spectrace/catalog/application/CatalogService.java) | `adoptSpecification`, `adoptionReference`, `lockMaterials`, `releasedAndEffective`, `create(Formula)` |
| [CatalogStore.java](../../backend/src/main/java/com/spectrace/catalog/infrastructure/CatalogStore.java) | `get`, `formulaItems`, `adoptFormula`, `formulaVersion`, `releaseFormula` |
| [CatalogIntegration.java](../../backend/src/main/java/com/spectrace/catalog/application/CatalogIntegration.java) and [RequestCatalogIntegration.java](../../backend/src/main/java/com/spectrace/catalog/infrastructure/RequestCatalogIntegration.java) | `requireActor`, `audit`, default/override `auditSpecificationAdoption` |
| [IdentityService.java](../../backend/src/main/java/com/spectrace/identity/application/IdentityService.java) and [AuthorizationService.java](../../backend/src/main/java/com/spectrace/identity/application/AuthorizationService.java) | `authenticate` returning `AuthenticatedActor`, `requirePermission` |
| [AuditApplicationService.java](../../backend/src/main/java/com/spectrace/audit/application/AuditApplicationService.java) | `recordCatalogEvent`, `recordSpecificationAdoptionEvent`, both `MANDATORY` |
| [CatalogErrors.java](../../backend/src/main/java/com/spectrace/catalog/interfaces/web/CatalogErrors.java) and [ApiError.java](../../backend/src/main/java/com/spectrace/shared/api/ApiError.java) | Catalog exception handlers and canonical four-field record |
| [Existing schema](../../backend/src/main/resources/db/migration/V1__schema.sql) and [constraints](../../backend/src/main/resources/db/migration/V2__constraints_indexes.sql) | Product pointers, formula/specification/item relationships, generated `current_formula_product_id`, `uq_formula_one_current_released_per_product_v3` |

| Existing test / methods | Design claim exercised |
| --- | --- |
| [CatalogSpecificationAdoptionTest](../../backend/src/test/java/com/spectrace/CatalogSpecificationAdoptionTest.java): `commandRejectsMissingBlankAndOversizedReferences`, `missingRequestAndInvalidProductAreRejectedBeforePersistence`, `missingIdentityAndAuditAdapterFailsClosed`, `requiresBothDataMaintenanceAndFormulaReleaseBeforeReadingOrWriting` | Input, integration availability and both permissions before persistence |
| Same class: `missingProductStaysNotFoundAndMissingBodyReferenceIsInvalidReference`, `sourceMustBelongToTheRequestedProduct`, `sourceMustBeReleased`, `staleSourceCannotReplaceTheCurrentFormula`, `currentPointerWithAnInconsistentSourceFlagCannotBeAdopted`, `sourceWithoutItemsCannotCreateAnEmptySnapshot` | Exact source/current and status semantics |
| Same class: `targetMustBeReleasedAndEffective`, `targetMaterialMustOccurInTheSourceFormula`, `untouchedSpecificationMustRemainReleasedAndEffective`, `alreadyAdoptedOrOlderSpecificationCannotCreateANewVersion`, `releasedTargetCanReplaceARetiredHistoricalSpecification` | Target, bindings, newness and historical-specification policy |
| Same class: `validAdoptionCreatesAndReleasesANewSnapshotUsingTargetEvidence` | Store/release orchestration, target provenance and three audit calls |
| [CatalogSpecificationAdoptionHttpTest](../../backend/src/test/java/com/spectrace/CatalogSpecificationAdoptionHttpTest.java): `mapsExplicitAdoptionRequestAndReturnsCreatedCatalogSnapshot`, `preservesBusinessStatusAndCanonicalErrorEnvelope`, `auditAdapterFailureReturnsGenericErrorWithoutInternalDetails`, body rejection tests, `catalogQueryTypeMismatchKeepsBadRequestAndCanonicalError`, `unsupportedAdoptionContentTypeKeepsFrameworkMediaTypeStatus`, `unsupportedAdoptionMethodKeepsFrameworkMethodStatus` | HTTP contract, body mapping, safe four-field errors and preserved framework statuses |
| [FormulaSpecificationAdoptionMySqlTest](../../backend/src/test/java/com/spectrace/FormulaSpecificationAdoptionMySqlTest.java): `explicitAdoptionReleasesNextVersionAndPreservesHistoricalContentAndPublishedLabel` | Real HTTP 201, exact historical content/items, sparse ordering and repeated material copies, old label/declarations, current pointer and real audit payload |
| Same class: `missingIdentityAndInsufficientPermissionCannotWrite`, `invalidBodyReferencesAndUnreleasedOrFutureTargetCannotLeavePartialVersions`, `duplicateOriginalSourceAndAlreadyAdoptedCurrentSourceCannotWriteAgain` | Real 401/403/400/422/409 and no rejected-write residue |
| Same class: `simultaneousSameTargetRequestsCommitOneWinnerWithoutLoserRows`, `simultaneousDifferentEligibleTargetsCommitOnlyTheWinningSpecification` | Two independent MySQL transactions, product-lock serialization, one committed winner, no losing rows/audits |
| Same class: `adoptionAllocatesAboveHighestExistingDraftAndPreservesThatDraftExactly` | MAX+1 and exact preservation of a pre-existing draft |
| Same class: `auditFailureAfterRealInsertionRollsBackAndTheSameRequestCanRetryWithoutDuplicateAudit` | All three real audit INSERTs can exist inside a failing transaction; formula/items/pointer/audits roll back and same-body retry succeeds without residue |
| [FormulaAdoptionMaterialLockMySqlTest](../../backend/src/test/java/com/spectrace/FormulaAdoptionMaterialLockMySqlTest.java): `creatingTheNextSpecificationAndAdoptingTheReleasedTargetBothCommit`, `creatingAFormulaAndTheNextSpecificationBothCommit` | Real MySQL material/specification races, bounded coordination, both HTTP 201 and correct rows/audits after material-first fix |
| [IngredientSpecImpactStrategyMySqlTest](../../backend/src/test/java/com/spectrace/impact/IngredientSpecImpactStrategyMySqlTest.java): `soyLecithinInChocolateBaseClassifiesEveryRelevantProductAsTheGoldenExpects`, `beforeAdoptionEveryRelevantProductIsAdoptionPendingAndNothingIsWritten`, `adoptionDoesNotRequireAPublishedLabelButDiscoveryRejectsItBeforeImpactWrites` | Actual HTTP adoption -> fresh discovery -> existing strategy; pinned old-label current composition vs adopted proposed composition; separate missing-label boundary; no full impact-write claim |

## Evidence status and remaining acceptance

The [Day4 evidence](../evidence/S3-M2-day4-specification-adoption.md) and
[Day5 evidence](../evidence/S3-M2-day5-mysql-adoption-guards.md) record implementation
and earlier executions. Existing 473/480-test verification references are historical
baseline evidence. They are not Day6 executions. Actual Day6 checks are recorded in the
[Day6 evidence index](../evidence/S3-M2-day6-a07-evidence.md).

All four `.mmd` files are editable diagram sources. Rendered files must come from
those sources using the actual Mermaid parser; a screenshot or manually fabricated
SVG does not establish rendering. Review of this document and cross-module
acceptance are tracked in the review ledger, with no acceptance inferred from
an empty ledger or a passed test suite.

Remaining business conditions include the scenario's actual specification release
and approval, the full SCRUM-48 impact/review/publication acceptance, and recorded
M1/M2/M4 review of the relevant boundaries. Released V2 rows in tests are explicit
fixtures and do not establish that business release. Documentation verification
does not merge, deploy, publish a label, or complete another member's work item.
