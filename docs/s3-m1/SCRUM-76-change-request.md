# SCRUM-76 — INGREDIENT_SPEC change request create/read

Implementation date: **2026-09-29 (Asia/Shanghai)**. Estimate: **2 story points**.
Parent: SCRUM-47 / S3-M1. Builds on SCRUM-75 ([PR #47](https://github.com/hxj04121-lab/FoodLabelFlow/pull/47)).

Status: **implemented and verified locally; team review pending**. The HTTP shape follows
M2's S3 candidate `docs/contracts/s3-impact-review-publication-api-v1.yaml`
([PR #48](https://github.com/hxj04121-lab/FoodLabelFlow/pull/48)). That candidate is not frozen,
and M1's review comments on it are still open. Flyway V1–V4 are unchanged.

## HTTP boundary

| Operation | Permission | Success |
| --- | --- | --- |
| `POST /api/v1/change-requests` | `CHANGE_REQUEST.CREATE` | 201, direct `ChangeRequest` with a `Location` header |
| `GET /api/v1/change-requests/{changeRequestId}` | any active identity (M4 has not chosen a read permission) | 200, direct `ChangeRequest` |

The request body must contain exactly `changeType` (`INGREDIENT_SPEC`), `supplierMaterialId`,
`previousSpecificationVersionId` and `targetSpecificationVersionId`, as non-blank strings.
The response contains exactly the seven candidate fields. The actor, code, description and
provenance are stored but not returned.

## Checks, in order

Every business precondition is checked before the first write, so no V2 CHECK or foreign key
can surface as a 500. Errors use the frozen four-field `ApiError`.

| Check | Status / code |
| --- | --- |
| Malformed JSON, duplicate or extra field, missing or blank value, non-string value, a `changeType` other than INGREDIENT_SPEC | 400 `INVALID_REQUEST` |
| Missing or unknown identity | 401 `AUTHENTICATION_REQUIRED` |
| Active actor without `CHANGE_REQUEST.CREATE` | 403 `AUTHORIZATION_DENIED` |
| Previous and target versions are the same | 400 `INVALID_REQUEST` |
| Unknown supplier material or specification version; unknown change request on GET | 404 `RESOURCE_NOT_FOUND` |
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

## Differences from the SCRUM-76 Jira text and open contract items

- **FORMULA / RULE_SET.** Jira said "explicit 422". The M2 candidate restricts `changeType`
  to the enum `[INGREDIENT_SPEC]`, so these types are 400 `INVALID_REQUEST`, following the contract.
- **Description.** `change_request.description` is NOT NULL but the candidate has no
  description field, so the server generates one from the material and versions. M1 has
  asked M2 to add a request field (PR #48 review).
- **404 for IDs in the body.** The candidate uses 404 for unknown specification versions and
  materials sent in the body. This implementation follows it. M1 has proposed 422 instead,
  to match S2; if that is accepted, only the mapping changes.

## Verification

Local, 2026-09-29, Java 21 and Colima:

- `ChangeRequestServiceTest` (12 tests): check order, authorization before any lookup, each
  precondition, duplicate 409, nothing written on failure, reads and type filtering.
- `ChangeRequestApiMySqlTest` (17 tests; real HTTP on MySQL 8.4.11 via Testcontainers):
  - create and read-back by another active user, with the stored row, typed columns and
    the audit row checked;
  - 10 invalid bodies returning 400, plus 401, 403, 404, 422 and 409;
  - four concurrent identical requests produce exactly one 201;
  - an audit failure rolls back both the request row and the audit row.
- `mvn -B -ntp -f backend/pom.xml verify`: **344 tests, 0 failures/errors/skips, BUILD SUCCESS**
  (315 on the SCRUM-75 baseline + 29 new).
