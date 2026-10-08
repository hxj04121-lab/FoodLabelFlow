# A07 — M1 use case: Run Change Impact Analysis

Owner: Huang Xiangjia / M1. Related: SCRUM-47, SCRUM-77–80.
Date: 2026-10-06. Status: **final for SCRUM-80**. The design diagrams use real class and
method names on the SCRUM-79/80 branches; the analysis diagram uses domain terms only.
The design problem for this use case is the
[Impact Strategy](S3-M1-A07-impact-strategy-draft.md).

## Use case

**Actor:** Change Manager (`IMPACT.RUN`).
**Goal:** for a recorded INGREDIENT_SPEC change request, find every product whose current
formula uses the changed supplier material, decide whether its published label is still
complete (NO_ACTION) or needs review (REVIEW_REQUIRED), and open a ReviewTask for each
product that needs one.

**Preconditions:**
- The change request is SUBMITTED.
- The rule set is ACTIVE.
- Every relevant product has a current published label.
- Every relevant product has adopted the target specification in a formula version N+1.

**Postconditions, all or nothing:**
- One COMPLETED run.
- One finding per relevant product.
- One OPEN ReviewTask per REVIEW_REQUIRED finding, with no draft label yet.
- One audit event.
- The change request is ANALYZED.
- Replaying the same request with the same rule set returns the same run.

**Alternate flows:**
- 422 `RULE_SET_NOT_ACTIVE`, `PUBLISHED_LABEL_MISSING` or `FORMULA_ADOPTION_PENDING`, with
  nothing written.
- 409 for a different rule set on a request that already has a run, or for a CANCELLED
  request.
- 500 with a full rollback on any server or audit failure.

## Analysis class diagram

```mermaid
classDiagram
    direction LR
    class ChangeRequest {
        changeType
        status
        previousSpecification
        targetSpecification
    }
    class SupplierMaterial
    class SpecificationVersion {
        versionNumber
        lifecycle
    }
    class Product
    class FormulaVersion {
        versionNumber
        isCurrentReleased
    }
    class FormulaItem
    class LabelVersion {
        lifecycle
    }
    class AllergenDeclaration {
        declarationType = CONTAINS
    }
    class RuleSetVersion {
        lifecycle
    }
    class ImpactAnalysisRun {
        status
        startedAt
        completedAt
    }
    class ImpactFinding {
        classification
        missingAllergenCodes
        explanation
    }
    class ReviewTask {
        status
        assignedTo
        draftLabel
    }
    ChangeRequest --> "2" SpecificationVersion : from / to
    SpecificationVersion --> SupplierMaterial
    Product --> FormulaVersion : current released (N+1)
    Product --> LabelVersion : current published (on N)
    FormulaVersion *-- "1..*" FormulaItem
    FormulaItem --> SpecificationVersion : pins
    LabelVersion *-- "0..*" AllergenDeclaration
    ImpactAnalysisRun --> ChangeRequest : analyses (at most one)
    ImpactAnalysisRun --> RuleSetVersion : under
    ImpactAnalysisRun *-- "0..*" ImpactFinding
    ImpactFinding --> Product
    ImpactFinding --> FormulaVersion : current N / proposed N+1
    ImpactFinding --> LabelVersion : current
    ImpactFinding "1" -- "0..1" ReviewTask : REVIEW_REQUIRED only
```

## Design class diagram

```mermaid
classDiagram
    direction TB
    class ImpactAnalysisController {
        +run(changeRequestId, body) ResponseEntity
        +get(impactAnalysisId) ImpactAnalysisResponse
    }
    class ChangeImpactAnalysisService {
        +run(changeRequestId, ruleSetVersionId) ImpactAnalysisView
        +get(impactAnalysisId) ImpactAnalysisView
    }
    class RelevantProductDiscovery {
        +discover(supplierMaterialId) List~RelevantProductTarget~
    }
    class ImpactStrategyRegistry {
        +require(ChangeType) ImpactStrategy
    }
    class ImpactStrategy {
        <<interface>>
        +assess(change, product, ruleSetVersionId) ProductImpactAssessment
    }
    class IngredientSpecImpactStrategy
    class ImpactAnalysisApplicationService {
        +execute(run, findings, reviewTaskLinks) ImpactAnalysisRun
    }
    class ImpactAnalysisView {
        run
        findings
        created
    }
    class ChangeRequestRepository {
        <<port>>
        +lockById(id)
        +updateStatus(id, from, to)
    }
    class RelevantProductLookupPort {
        <<port · catalog>>
    }
    class RuleSetVersionRepository {
        <<port · validation>>
    }
    class ImpactIntegration {
        <<port>>
        +requireActor(Permission)
        +reviewTaskAssignee()
    }
    class ImpactAnalysisRunRepository {
        <<port · M5>>
    }
    class ImpactFindingRepository {
        <<port · M5>>
    }
    class ReviewTaskLinkageRepository {
        <<port · M5>>
    }
    class ImpactAuditEventPort {
        <<port · audit>>
    }
    ImpactAnalysisController --> ChangeImpactAnalysisService
    ImpactAnalysisController ..> ImpactAnalysisResponse
    ChangeImpactAnalysisService --> ChangeRequestRepository
    ChangeImpactAnalysisService --> RuleSetVersionRepository
    ChangeImpactAnalysisService --> RelevantProductDiscovery
    ChangeImpactAnalysisService --> ImpactStrategyRegistry
    ChangeImpactAnalysisService --> ImpactAnalysisApplicationService
    ChangeImpactAnalysisService --> ImpactIntegration
    ChangeImpactAnalysisService ..> ImpactAnalysisView
    RelevantProductDiscovery --> RelevantProductLookupPort
    ImpactStrategyRegistry o-- ImpactStrategy
    ImpactStrategy <|.. IngredientSpecImpactStrategy
    ImpactAnalysisApplicationService --> ImpactAnalysisRunRepository
    ImpactAnalysisApplicationService --> ImpactFindingRepository
    ImpactAnalysisApplicationService --> ReviewTaskLinkageRepository
    ImpactAnalysisApplicationService --> ImpactAuditEventPort
```

The impact application layer depends only on ports. The catalog, label, allergen, validation,
identity and audit modules own their adapters, and no impact code reads their tables. M5 owns
the run/finding/task persistence and its idempotency.

## Design sequence diagram: `POST /api/v1/change-requests/{id}/impact-analyses`

```mermaid
sequenceDiagram
    actor cm as Change Manager
    participant web as ImpactAnalysisController
    participant svc as ChangeImpactAnalysisService
    participant cr as ChangeRequestRepository
    participant runs as ImpactAnalysisRunRepository
    participant rs as RuleSetVersionRepository
    participant disc as RelevantProductDiscovery
    participant strat as IngredientSpecImpactStrategy
    participant intg as ImpactIntegration
    participant m5 as ImpactAnalysisApplicationService
    cm->>web: POST {ruleSetVersionId}
    web->>svc: run(changeRequestId, ruleSetVersionId)
    Note over svc,m5: one @Transactional unit
    svc->>intg: requireActor(RUN_IMPACT)
    svc->>cr: lockById (SELECT … FOR UPDATE)
    svc->>runs: findByChangeRequestId
    alt run exists
        svc-->>web: same rule set → existing view (200), else 409
    else first run
        svc->>rs: findActiveById
        svc->>disc: discover(material)
        disc-->>svc: targets (422 PUBLISHED_LABEL_MISSING)
        loop each target, by productId
            svc->>strat: assess(change, target, ruleSet)
            strat-->>svc: assessment (422 FORMULA_ADOPTION_PENDING)
        end
        opt any REVIEW_REQUIRED
            svc->>intg: reviewTaskAssignee()
        end
        svc->>m5: execute(run, findings, OPEN tasks)
        m5->>m5: save run · findings · tasks · audit
        m5-->>svc: persisted run
        svc->>cr: updateStatus(SUBMITTED → ANALYZED)
        svc-->>web: view (created)
    end
    web-->>cm: 201 + Location / 200 / ApiError
```

Every 422 is raised before `execute`, so a failed precondition writes nothing. Two concurrent
triggers of one request are handled twice over:

- The second waits on the change-request row lock.
- If its snapshot still misses the winner's run, M5's insert hits the
  `impact-analysis:<changeRequestId>` idempotency key, `execute` returns the winner's run,
  and the service answers 200.

## Verification links

- [SCRUM-80 evidence](S3-M1-SCRUM-80-soy-golden-e2e.md): golden end-to-end results.
- [SCRUM-79 notes](../s3-m1/SCRUM-79-impact-run.md): API, rollback and concurrency tests.
- [SCRUM-77](../s3-m1/SCRUM-77-relevant-product-discovery.md) and
  [SCRUM-78](../s3-m1/SCRUM-78-impact-strategy.md): discovery and classification rules.
