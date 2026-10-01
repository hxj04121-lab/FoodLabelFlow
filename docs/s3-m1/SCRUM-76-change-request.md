# SCRUM-76 — INGREDIENT_SPEC change request create/read

Implementation date: **2026-09-29 (Asia/Shanghai)**. Estimate: **2 story points**.
Parent: SCRUM-47 / S3-M1. Builds on SCRUM-75 ([PR #47](https://github.com/hxj04121-lab/FoodLabelFlow/pull/47)).

Status: **implemented and verified locally; team review pending**. The HTTP shape and error
codes follow M2's S3 contract `docs/contracts/s3-impact-review-publication-api-v1.yaml` and
`docs/contracts/s3-impact-api-error-matrix-v1.md` as merged in
[PR #48](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48), which took M1's review fixes.
The contract is still marked `CANDIDATE_PENDING_CROSS_MODULE_ACCEPTANCE`. Flyway V1–V4 are unchanged.

## HTTP boundary

| Operation | Permission | Success |
| --- | --- | --- |
| `POST /api/v1/change-requests` | `CHANGE_REQUEST.CREATE` | 201, direct `ChangeRequest` with a `Location` header |
| `GET /api/v1/change-requests/{changeRequestId}` | any active identity (M4 has not chosen a read permission) | 200, direct `ChangeRequest` |

The request body must contain exactly `changeType` (`INGREDIENT_SPEC`), `supplierMaterialId`,
`previousSpecificationVersionId`, `targetSpecificationVersionId` and `description`, as non-blank
strings; `description` is at most 1000 characters (counted as code points, matching the
`VARCHAR(1000)` column). The response contains exactly the eight contract fields, including
`description`. The actor, code and provenance are stored but not returned.

## Checks, in order

Every business precondition is checked before the first write, so no V2 CHECK or foreign key
can surface as a 500. Errors use the frozen four-field `ApiError`.

| Check | Status / code |
| --- | --- |
| Malformed JSON, duplicate or extra field, missing or blank value, non-string value, description over 1000 characters, a `changeType` other than INGREDIENT_SPEC | 400 `INVALID_REQUEST` |
| Missing or unknown identity | 401 `AUTHENTICATION_REQUIRED` |
| Active actor without `CHANGE_REQUEST.CREATE` | 403 `AUTHORIZATION_DENIED` |
| Previous and target versions are the same | 422 `SPECIFICATION_VERSION_UNCHANGED` |
| Unknown supplier material or specification version in the body | 422 `CHANGE_REFERENCE_NOT_FOUND` |
| Unknown change request in the GET path | 404 `RESOURCE_NOT_FOUND` |
| A version belongs to another material | 422 `SPECIFICATION_MATERIAL_MISMATCH` |
| Target not RELEASED, or previous still DRAFT (RETIRED is allowed) | 422 `SPECIFICATION_NOT_RELEASED` |
| Target effective date is in the future (UTC) | 422 `SPECIFICATION_NOT_EFFECTIVE` |
| A non-CANCELLED request already exists for the same version pair | 409 `DATA_CONFLICT` |
| Any failure after the insert, including the audit write | 500 `INTERNAL_ERROR`; request and audit row both roll back |

A created request is SUBMITTED, requested by the trusted actor, stamped with provenance
`prov_scenario_input` ("Scenario-only change request inputs" in V3), and audited as
`CHANGE_REQUEST_CREATED` / `CHANGE_REQUEST` in the same transaction.

## Design notes

- **Concurrency.** The service locks the target specification row (`SELECT … FOR UPDATE`),
  then runs the duplicate check as a locking read (`FOR SHARE`). Under REPEATABLE READ, a
  plain SELECT would read the snapshot taken before the waiting transaction's first
  query, miss the winner's committed row, and insert a duplicate. The concurrent test
  fails when `FOR SHARE` is removed, which was checked on 2026-09-29.
- **Port ownership.** `SpecificationVersionLookupPort` and `RelevantProductLookupPort`
  moved from `impact.application.port` to `catalog.application.port`. The module that owns
  the data owns the port and its JDBC adapter, as with `FormulaCompositionPort`, so
  catalog never depends on impact.
- **Audit port.** Impact audit uses a separate `audit.application.port.ImpactAuditPort`.
  The existing single-method `AuditEventPort` is used as a lambda in
  `Scrum29MySqlIntegrationTest`, so it is left unchanged.

## Difference from the SCRUM-76 Jira text

The Jira done criteria say FORMULA / RULE_SET return an "explicit 422". The merged contract
restricts `changeType` to the enum `[INGREDIENT_SPEC]`, so those types are 400
`INVALID_REQUEST`, following the contract. The earlier gaps (server-generated description,
404 for body references, 400 for unchanged versions) were resolved by the merged contract
and this implementation now follows it.

## Verification

Local, 2026-10-01, Java 21 and Colima, after merging `origin/main@0755d06` (contract from #48):

- `ChangeRequestServiceTest` (13 tests): check order, authorization before any lookup, each
  precondition and its contract code, description bounds, duplicate 409, nothing written on
  failure, reads and type filtering.
- `ChangeRequestApiMySqlTest` (21 tests; real HTTP on MySQL 8.4.11 via Testcontainers):
  - create and read-back by another active user, with the stored row (including
    `description`), typed columns and the audit row checked;
  - 12 invalid bodies returning 400 (including missing, blank and 1001-character
    descriptions), plus 401, 403, 404 (path only), 422 and 409;
  - a 1000-character description ending in an emoji is stored and returned intact;
  - request fields, response fields and every emitted error code are checked against the
    merged contract YAML and error matrix;
  - four concurrent identical requests produce exactly one 201;
  - an audit failure rolls back both the request row and the audit row.
- `mvn -B -ntp -f backend/pom.xml verify`: **352 tests, 0 failures/errors/skips, BUILD SUCCESS**.
