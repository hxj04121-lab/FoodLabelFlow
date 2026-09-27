# S3 M2 Day 1 — Contract Diff and Freeze Candidate

Status: candidate only; cross-module acceptance and contract freeze are pending.
Owner: Cai Runchen / M2
Jira: SCRUM-48 / SCRUM-52
Observed: 2026-09-27 (Asia/Shanghai)

## Live baseline

- Repository: hxj04121-lab/FoodLabelFlow; branch codex/s3-m2-day1-contract-freeze starts at origin/main commit a3520e1f1d450796a694b6930d7792dda0f2b512.
- The latest main CI run at the observed commit is [GitHub Actions run 36134011826](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/36134011826), completed successfully. Backend, frontend, containers, SonarQube, SonarCloud Code Analysis, and security checks all concluded success.
- There were no open pull requests when this baseline was read.
- Sprint 3 (Sprint ID 37) is active. SCRUM-48 and SCRUM-52 were both read as Idea and assigned to RunChen Cai, then transitioned through the authenticated Jira connector to In Progress and re-read. The Jira workflow exposed the In Progress transition for both issues before the first project change.
- Current team work: SCRUM-47 (M1), SCRUM-50 (M4), and SCRUM-51 (M5) are In Progress; SCRUM-49 (M3) is Idea. This records live state, not an assessment of those members' progress.

## Current contract and implementation inventory

| Surface | Current main evidence | Day 1 finding |
| --- | --- | --- |
| Validation API | docs/contracts/allergen-validation-api-v1.yaml (OpenAPI 3.1.0, version 1.0.0): GET /api/v1/allergens; POST /api/v1/label-versions/{labelVersionId}/validation-runs; GET /api/v1/validation-runs/{validationRunId}. Success bodies are direct resources. | Existing validation contract remains the reusable S2 baseline; it does not define S3 impact, ReviewTask, review-decision, or publication resources. |
| Label-bound allergen facts and declarations | docs/contracts/label-derived-allergens-api-v1.yaml and docs/contracts/label-declarations-api-v1.yaml; current M2 reads are version-pinned. | Reuse exact formula/specification evidence and declaration facts for M1's comparison. Do not reinterpret an empty allergen list as proof that there are no allergens. |
| Shared HTTP errors | backend/src/main/java/com/spectrace/shared/api/ApiError.java and the validation, label, catalog, and identity web adapters. The envelope is exactly code, message, traceId, evidenceId; traceId/evidenceId may be null. | Preserve the four-field envelope and stable machine-readable codes. Messages are caller-safe text, not a decision API. Catalog also uses 503 for an unavailable required integration; whether a corresponding S3 response is needed remains an explicit review item. |
| Catalog specification and formula lifecycle | CatalogController exposes supplier/material/specification and formula create/read/release routes. Formula release checks the expected current formula and pins released, effective specification versions. FormulaCompositionPort reads one immutable formula by ID and returns its current-released flag and supplier-material/specification item facts. Catalog responses currently include persistence-shaped maps. | These existing M1 catalog operations do not implement the S3 explicit Spec V2 adoption action or a supplier-material-to-current-products query contract. Keep new M2 contract DTOs explicit and camelCase; do not use this slice to rewrite the existing catalog surface. |
| Label draft and workflow | LabelDraftController exposes draft create/read and structured declarations. The workflow module contains existing S2 label lifecycle guards, but main has no S3 ReviewTask API or review-decision/publication controller. | M4 owns S3 review decisions, maker-checker, publication transaction, lifecycle, and login decision. M2 defines only the compatible boundary. |
| Change impact | backend/src/main/java/com/spectrace/impact/domain/ImpactModuleBoundary.java is a boundary placeholder. Main has no ChangeRequest, ImpactAnalysisRun, ImpactFinding, or ReviewTask HTTP/application implementation. | M1 owns impact execution, classification, and task creation; M5 owns their persistence, audit, and idempotency. Main has no S3 impact contract to treat as already frozen. |

## M2 ownership map from live Sprint 3 issues

| Member | Live issue and responsibility | M2 boundary |
| --- | --- | --- |
| M1 | SCRUM-47: change request and impact core; discover affected products through the current FormulaVersion and FormulaItem.supplier_material_id; classify NO_ACTION / REVIEW_REQUIRED; create ReviewTask only for REVIEW_REQUIRED. | M2 publishes the contract, deterministic golden expectations, supplier-material lookup contract, and adoption contract. M2 does not implement the impact engine or create tasks. |
| M2 | SCRUM-48: impact/review/publication contracts, ApiError negative-path matrix, SOY expectations, supplier-material-to-product lookup, explicit Specification V2 to FormulaVersion N+1 adoption, and M2 A07 evidence. | This Day 1 PR is documentation only: live baseline, ownership, and a reviewable contract-diff candidate. |
| M3 | SCRUM-49: browser path for impact, ReviewTask, validation feedback, review, publication, and permission-aware state. | M2 specifies stable consumer contracts; no frontend files or UI behavior change in this PR. |
| M4 | SCRUM-50: ReviewTask lifecycle, validation gate, APPROVE / REQUEST_CHANGES / REJECT, maker-checker, current-version guard, atomic publication, and login decision. | M2 aligns request/response and errors after review; M2 does not implement these transitions or publication. |
| M5 | SCRUM-51: impact persistence ports/adapters, atomic task/audit writes, idempotency, architecture rules, full-path CI, and staging decision. | M2 supplies persistence-neutral contract data and M2 tests; no impact SQL, migration, audit, or full-path CI ownership is added here. |

## Candidate compatibility decisions to review

1. Keep the existing four-field ApiError shape. Candidate status meanings are 400 for malformed input, 401 for absent/invalid identity, 403 for a known actor without permission, 404 for an absent resource, 409 for duplicate/stale/immutable state, 422 for a valid command blocked by domain preconditions, and 500 for an unexpected server failure. Catalog currently returns 503 when its required identity/audit adapter is unavailable; M1/M4/M5 must confirm whether any S3 response needs that status. Confirm all S3-specific codes with M1/M4/M5 before freezing them.
2. Keep successful resources direct rather than adding a generic envelope. Use server-assigned resource IDs and camelCase in new M2-owned request/response DTOs, consistent with the current M2 OpenAPI and Java DTO boundary.
3. Impact results must be deterministic for the SOY scenario. Only products whose current formula contains the changed supplier material are relevant. NO_ACTION creates no ReviewTask. REVIEW_REQUIRED creates one and keeps target_label_version_id nullable until a replacement draft exists, as SCRUM-47 specifies.
4. Formula adoption is an explicit action after the new specification is released. It creates an immutable FormulaVersion N+1 pinned to Specification V2 and updates the current pointer atomically. It is separate from label publication. Duplicate/stale/concurrent adoption semantics and response shape remain review questions; no implementation or endpoint is asserted by this candidate.
5. M4 must confirm review and publication state names, permission errors, submission preconditions, and who supplies publication evidence identifiers. M5 must confirm transaction/idempotency boundaries and persistence-neutral identifiers. M3 must confirm what the UI needs to render and retry. M1 must confirm the impact trigger/query/finding contract and lookup semantics.
6. Freeze remains pending until actual M1/M3/M4/M5 review feedback is recorded and resolved. A review request alone is not acceptance.

## Review questions for the Day 1 pull request

- M1 (SCRUM-47): Can the proposed supplier-material lookup and impact/finding boundary be consumed without M2 owning classification, task creation, or M1 persistence?
- M3 (SCRUM-49): Are the candidate stable IDs, direct resources, outcome values, nullable target label reference, and error envelope sufficient for the browser path?
- M4 (SCRUM-50): Are the candidate review/publication boundaries, auth errors, and separation of adoption from publication compatible with the owned state machine?
- M5 (SCRUM-51): Are the candidate identifiers, conflict/precondition errors, and adoption atomicity semantics compatible with owned persistence, audit, and idempotency?

No S3 contract is declared frozen in this document. No PR CI run, reviewer approval, or implementation is claimed; only the main CI and live Jira facts listed above are recorded.
