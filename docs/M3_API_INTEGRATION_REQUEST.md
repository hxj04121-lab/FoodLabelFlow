# M3 发给 M1 的 Sprint 1 联调需求

日期：2026-09-09。状态：前端提出的联调需求，尚非已批准接口契约。

我（M3 / Xu Feiyang）已在 XFY 分支完成后台页面、配方创建表单、本地校验、未保存提醒与浏览器测试。当前业务数据来自明确标注的 seed-preview.json，只读展示；还没有真实保存或发布。

现在需要完成 Sprint 1 的真实数据接入。M2 的共享契约已经在 main，无需重复发送；M3 直接读取仓库。当前需 M1 提供 Catalog/Product/Formula 的可调用实现、接口用法及联调地址。前端采用 React + TypeScript + Tailwind CSS + shadcn/ui，不改变后端业务边界。

## 已取得的 M2 契约

2026-09-09 获取 origin/main，提交为 97fd46ebb27830a7bce5a2bee2f214519a37f622。契约：[allergen-validation-api-v1.yaml](contracts/allergen-validation-api-v1.yaml)，当前标记为 0.1.0-draft。

文件定义过敏原列表、创建标签校验运行、读取校验运行三个接口，以及 Allergen、ValidationRunRequest、ValidationRun、ValidationResult、ApiError 等结构。它未定义 Product/Formula 的查询、创建或发布接口，也不证明后端已实现。因此无需让 M2 重发文档，但仍需向 M1 获取其业务接口用法。

ApiError 要求四个键：code、message、traceId、evidenceId；后两者可以为 null。没有真实关联值时保留键并返回 null。当前 schema 禁止额外字段，字段级 errors 不应未经契约修订直接加入。

## 请先提供的联调信息

- 后端分支、提交 SHA、启动步骤和 API 地址；不必等所有功能合并 main 后才提供联调分支。
- M1 的 Product/Formula 及 Catalog 接口路径、方法、DTO 和真实请求/响应示例；已有仓库文档只需告知路径，不必重发 M2 的共享契约。
- 身份认证方式、当前用户/权限的获取方式、用于测试的角色；账号通过安全渠道提供，不在文档中写密码或 token。相关权限请与 M4 对齐。
- 分页、筛选、字段命名、空值、时间及时区约定。
- 哪些接口已实现、哪些待实现，以及可以联调的时间。

## 第一批 只读接口

下面列出能力而非固定 URL。如已有命名约定，请直接沿用，前端适配。

| 能力 | 前端用途 | 最少需要的信息 |
| --- | --- | --- |
| 供应商列表/详情 | 供应商页面与物料关联 | supplierId、code、name、provenance 引用 |
| 供应商物料列表/详情 | 物料页、配方下拉选择 | materialId、supplierId、ingredientId（可空）、code、name、description |
| 指定物料的规格版本列表/详情 | 规格选择与追溯 | specificationVersionId、materialId、versionNumber、status、effectiveDate、releasedAt、成分项 |
| 产品列表/详情 | 产品检索和配方归属 | productId、description、brandOwner、category、fdcId、currentFormulaVersionId、provenance；哪些字段可空请标注 |
| 产品的配方版本列表 | 当前配方与历史页 | formulaVersionId、productId、versionNumber、status、isCurrentReleased、创建/发布者及可用时间字段 |
| 配方详情 | 追溯上游物料规格 | 配方头、items；每项含 materialId、specificationVersionId、sequenceNo、quantityValue、quantityUnit |
| 来源信息 | 区分公开来源与项目构造关联 | sourceType、dataset、recordId、sourceUrl 或可解析 provenanceId |

关联名称可以内嵌 DTO，也可以由列表接口查询后映射；请明确方式，避免每行表格产生多个请求。

产品页希望按名称、品牌或 FDC ID 搜索；分页需说明从 0 还是 1 开始、pageSize/total 及排序稳定性。如果 S1 只返回小规模完整列表，也可明确约定由前端筛选分页。

目前界面存在 fixture_group 分组，它只是演示数据分类，不是影响分析结果。若正式接口不提供该字段，前端将移除此筛选，不能据此计算真实审核任务数。

概览总数可先由明确完整的列表推导；若列表分页，请提供 total 或轻量汇总，不能把当前页条数当成总数。不需要为 S1 新建后续阶段的完整 Dashboard 后端。

## 第二批 创建与发布

### 创建配方草稿

需要能够为指定产品一次提交配方项，返回服务端持久化的新版本。

以下 JSON 只是建议语义，不要求按此字段名实现：

```json
{
  "productId": "prod_usda_1106285",
  "items": [
    {
      "supplierMaterialId": "mat_chocolate_base",
      "specificationVersionId": "spec_chocolate_v1",
      "sequenceNo": 1,
      "quantityValue": "12.5000",
      "quantityUnit": "kg"
    }
  ]
}
```

服务端决定 ID、版本号、创建人和生命周期；前端不能提交任意用户 ID 作为认证身份。返回实际 formulaVersionId、versionNumber、status、items 等信息。创建应整体成功或整体失败，避免留下半份配方。

如支持已保存草稿继续编辑，请说明更新接口；若暂不支持，前端先限制为创建、查看及发布，不假装更新成功。

### 发布指定配方版本

需要通过独立操作发布服务端已有的草稿版本。后端检查角色权限、状态、规格引用和业务条件，并一致地更新产品当前配方引用、当前版本标记及所需审计记录。旧版本与旧物料引用必须保留。

成功后，前端重新查询产品和配方历史验证持久化结果。请说明重复发布、并发发布及请求超时后的查询/重试机制，避免重复创建版本或产生多个 current。

## 请 M1 说明的业务规则

1. 新草稿初始状态是什么？哪些状态允许编辑和发布？哪些规格状态可被选择？
2. 产品是否必须已有已发布配方？创建是空白新版本还是复制旧版本？是否支持两种方式？
3. 同一物料能否出现多行？如果可以，如何用顺序或其他字段区分？
4. 数量和单位是否必填、是否必须成对填写？零数量是否合法？允许哪些单位？是否存在比例总和规则？
5. 现有数据库数量为 DECIMAL(12,4)、单位为 VARCHAR(40)。前端当前按此检查容量；数量传字符串还是 JSON number、空值传 null 还是省略，请明确。
6. 服务端如何处理过期的当前配方、规格被撤回、并发版本号冲突？
7. 创建/发布是否支持幂等键或其他去重机制？超时后如何确认上次请求是否成功？

不要求为这次联调扩展数据库或重新设计流程；有既定规则就提供既定规则。

## 错误与权限反馈

优先复用 M2 已有 ApiError：code、message、traceId、evidenceId。后两者无真实记录时返回 null，不能编造 ID。字段路径反馈（例如 items[0].specificationVersionId）只是可选扩展需求；当前契约未提供，需要时再由 M2 协助修订，不能作为现有字段要求。

- 未认证与权限不足要能区分。
- 字段校验失败应指出字段及原因。
- 产品/物料/版本不存在、规格不属于所选物料需明确提示。
- 历史版本不可编辑、重复发布、并发冲突需明确提示。
- 服务异常不暴露 SQL、堆栈或秘密。

现有共享契约已定义 INVALID_REQUEST、AUTHORIZATION_DENIED、RESOURCE_NOT_FOUND、INTERNAL_ERROR 等错误；LABEL_VERSION_NOT_CURRENT 和 VALIDATION_PRECONDITION_FAILED 是其标签校验语义，不直接套用到配方发布。请 M1 提供 Product/Formula 操作实际使用的状态和 code，只有新增或冲突部分需要 M2 协调。前端不根据自由文本猜业务结果；超时或网络失败时保留输入，不自动重试产生重复版本。

## 联调完成标准

1. 页面读取 API，与数据库中的真实记录一致；接口失败不静默退回种子数据。
2. 从表单创建草稿，刷新后能查询到同一版本和配方项。
3. 发布草稿后重新查询，产品当前引用及版本状态一致，旧版本仍可查看且内容不变。
4. 无权限、错误规格引用、非法状态和重复提交有可验证的失败反馈，失败后输入保留。
5. M3 留下浏览器、请求响应与提交 SHA 证据；M5 协助数据库/集成测试；人工验收由真实参与者完成。

优先顺序：M1 先提供只读接口用法和联调地址，再提供创建草稿，最后提供发布。M2 共享契约由 M3 从 main 获取，只对未覆盖或冲突的部分再讨论。
