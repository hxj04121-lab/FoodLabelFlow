# Change-request list for the impact selector (SCRUM-47 follow-up to SCRUM-76)

Implementation date: **2026-10-01 (Asia/Shanghai)**. Parent: SCRUM-47 / S3-M1.

Status: **implemented in merged PR #55; cross-module acceptance pending**.

- **Contract:** the route follows M2's collection contract in
  [PR #52](https://github.com/hxj04121-lab/FoodLabelFlow/pull/52), approved by M1.
- **Integration:** #55 provides the backend route for M3's SCRUM-71 selector.
  The #52 contract tests assert this operation against the YAML; M3 wiring and
  cross-module acceptance for the 2 Oct checkpoint remain separate verification.

## Behaviour

| Aspect | Implementation |
| --- | --- |
| Route | `GET /api/v1/change-requests?limit=&offset=` returns a direct `ChangeRequest[]` with the same eight fields as the single-item GET. |
| Paging | `limit` defaults to 50 (1..100) and `offset` to 0 (≥ 0). An offset past the end, including one beyond the int range, returns `200 []`. |
| Selection | `INGREDIENT_SPEC` in `SUBMITTED`, `ANALYZED` or `COMPLETED`, filtered in SQL **before** `LIMIT`/`OFFSET`. |
| Ordering | `changeRequestId` ascending, per #52. M1 suggested `createdAt` descending on #52; adopting that changes one `ORDER BY` and its tests. |
| Read policy | Any active identity, the same as the single-item GET. `CHANGE_REQUEST.CREATE` and `IMPACT.RUN` are not required. Authentication happens before any repository read. |
| 400 `INVALID_REQUEST` | Any of: a non-integer, negative or out-of-range `limit`/`offset`; a repeated parameter; any query parameter other than `limit` and `offset`. |
| 401 `AUTHENTICATION_REQUIRED` | Missing or unknown identity. |
| 500 `INTERNAL_ERROR` | A failed read, or a listed row whose target specification cannot be resolved. These never return an empty or shortened page. |
| Effects | None: no change request, analysis or audit row is written. |

The supplier material for each listed row is resolved through one batch read per page
(`SpecificationVersionLookupPort.findAllById`), not one query per row.

## Verification

Local, 2026-10-01, Java 21 and Colima:

- `ChangeRequestServiceTest` (3 new tests, 16 in total):
  - filtering before paging, ordering, short and empty pages, and the material for each row;
  - bounds checked before authentication;
  - a missing specification fails the whole read.
- `ChangeRequestApiMySqlTest` (16 new cases, 37 in total; real HTTP on MySQL 8.4.11):
  - the item shape matches the single-item GET;
  - paging across a fixture whose excluded rows sort before and between the listed ones;
  - a full last page followed by `[]`, and an empty collection returning `[]`;
  - 11 invalid queries;
  - 401s, a read by a user without the create permission, and no writes;
  - a failed specification lookup returns 500, not an empty page.
- `mvn -B -ntp -f backend/pom.xml verify`: **371 tests, 0 failures/errors/skips, BUILD SUCCESS** (352 + 19 new).
