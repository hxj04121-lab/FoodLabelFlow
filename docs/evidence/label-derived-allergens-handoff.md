# M1 derived allergen display handoff (SCRUM-18 integration)

## Scope and contract

This additive M1 read resource leaves M4's LabelDraft response and the frozen
SCRUM-41 validation API unchanged. Contract:
[`label-derived-allergens-api-v1.yaml`](../contracts/label-derived-allergens-api-v1.yaml).

`GET /api/v1/label-versions/{labelVersionId}/derived-allergens`

- Use the same active identity as label GET (`X-Auth-Provider` and
  `X-External-Subject` in the existing identity integration). No `LABEL.VALIDATE`
  grant is needed. Production must retain the existing trusted identity boundary.
- No request body or query parameters are defined. Query parameters, including
  formula/rule-set/jurisdiction overrides, are rejected with 400.
- The direct JSON response contains `labelVersionId`, `formulaVersionId`,
  `ruleSetVersionId`, `jurisdictionCode`, `facts`, and `unresolvedComponents`.
- Each fact contains `allergenId`, `allergenCode`, and `derivationEvidence`.
  Evidence retains formula item, specification version, component, ingredient,
  ingredient-allergen mapping, evidence rule, and data-provenance identifiers.
- Unresolved entries retain `formulaItemId`, `specificationVersionId`,
  `specComponentId`, `ingredientId`, `rawPhrase`, `matchRule`, and `matchStatus`
  (`UNMAPPED` or `AMBIGUOUS`). Do not interpret empty facts as proof of no allergens.

## Version and failure semantics

The exact label version determines formula, rule set, and jurisdiction. No latest
or current alternative is substituted. Historical labels, old formulas, and
retired/expired rule sets remain readable. This is a display operation, not a
validation eligibility check: POST validation keeps its existing lifecycle gates.

Facts are recomputed using version-bound data stored at read time, not replayed
from an archived ValidationRun. The response does not assert PASSED/FAILED or
submission eligibility. To inspect a past run, use the existing validation-run API.

Missing/inactive identity returns 401; missing label returns 404; missing or
incomplete pinned inputs or inconsistent product/jurisdiction bindings return
422; internal failures return a sanitized 500. Errors use the existing ApiError
envelope. Unresolved components return 200 with their explicit diagnostic entries.

The service reuses owner ports in one short transaction. Existing snapshot ports
use locking reads, so the transaction deliberately does not use JDBC read-only
mode. The operation makes no business writes and creates no run/results/audit.

## Verification

Local verification on 2026-09-24 used Java 21 and MySQL 8.4 Testcontainers:
35 focused tests passed; full `mvn -B -ntp -f backend/pom.xml verify` passed
262 tests with zero failures, errors or skips. Base: main `f2b31db`.

- `LabelAllergenApiContractTest`: additive schema/record agreement and shared errors.
- `LabelAllergenQueryServiceTest`: exact binding, retired/expired reads, unavailable
  inputs and mismatched adapter output.
- `LabelAllergenApiMySqlTest`: all four positive golden fixtures, authenticated
  reads without validation permission, historical bindings despite an active
  alternative mapping, empty mapped facts, errors and unchanged input/output tables.
- `NegativeGoldenLabelAllergenApiMySqlTest`: all four negative golden fixtures,
  including unresolved diagnostics and retired-rule-set display.

## Remaining owner handoff (copyable)

M1 的 derived allergen facts 已提供独立读取接口：
`GET /api/v1/label-versions/{labelVersionId}/derived-allergens`。
返回标签绑定的 `labelVersionId / formulaVersionId / ruleSetVersionId /
jurisdictionCode`，以及 `facts[].allergenId / allergenCode /
derivationEvidence` 和 `unresolvedComponents`。接口不执行验证、不创建 run 或
audit；前端需展示未解析提示，不能将空 facts 直接显示为“无过敏原”。接口在本 PR
合并部署后可接入。

剩余请 M4 补齐同一 labelVersionId 的结构化 declarations（扩展 LabelDraft 或提供
独立 GET 均可），并明确声明类型、过敏原标识、展示文本及来源字段的实际契约，同时
返回或明确关联 formulaVersionId、ruleSetVersionId、jurisdictionCode，避免与 M1
数据串版本。这些是待确认的字段要求，不是已实现的 M4 响应。SCRUM-18 前端随后按
同一标签版本组合两组数据，分别展示“声明”和“推导事实”，并完成未解析、空列表、
鉴权失败、找不到标签及不可读取输入的接入验收。
