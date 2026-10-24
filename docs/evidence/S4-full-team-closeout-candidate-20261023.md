# FOOD 单体 Sprint 4 全员收尾报告（本地候选）

日期：2026-10-23（Asia/Shanghai）

仓库：FoodLabelFlow 单体；不包含 FoodLabelFlow-Microservices/CN。

Jira：Sprint 74「Demo & Hardening」。

本地分支：codex/food-m3-m4-on-pr91。
当前 HEAD：920581ff861e601d1fdbd614a9f434b34d7a7698；本轮尚未提交，HEAD 之后的内容都是本地未提交改动。

## 结论边界

本报告记录已完成的本地技术验收和未完成的全员签收。读取 Jira 时 SCRUM-85 至 SCRUM-89 均为 In Progress；没有子任务或关联缺陷。检查了全部五项（没有 Done 项可抽查）。项目 Definition of Done 页面无法读取，因此没有推断 Jira 以外的验收要求。PR #92 仍为草稿、未合并；远端成功 Actions run [39886228433](https://github.com/hxj04121-lab/FoodLabelFlow/actions/runs/39886228433)、Sonar、安全扫描记录对应旧 SHA 920581ff861e601d1fdbd614a9f434b34d7a7698，不代表本地 diff 已获 CI 认可。没有提交、推送、更新 PR 或 Jira。

## S4 全员任务对照

| Jira / 负责人 | 要求与依赖 | 本地实现、测试与证据 | 结论、缺口及负责人下一步 |
| --- | --- | --- | --- |
| [SCRUM-85](https://hxj04121.atlassian.net/browse/SCRUM-85) / M1 黄翔嘉 | 新鲜数据库中采用 Spec V2、运行影响分析；校验 40 个相关产品的命中/排除；M2 复核合同与错误矩阵、更新 A07。依赖产品/规格目录和影响分析合同。 | 最终隔离浏览器环境中采用 40/40、0 跳过、0 失败；影响分析 40 条（20 NO_ACTION、20 REVIEW_REQUIRED）。完整实体映射见 ../../../../outputs/food-s4-final-20261023/s4-generated-ids-final.json 和 s3-ui-final-live/s3-product-flow-observations.json。后端全量验证与新增目录接口回归日志见本报告测试段。 | 功能链有真实 UI 证据，负责人验收和 M2 跨模块合同复核未记录。M1/M2 需复核证据并在获批远端交付后更新 Jira；不能据测试自动结项。 |
| [SCRUM-86](https://hxj04121.atlassian.net/browse/SCRUM-86) / M2 RunChen Cai | 冻结标签修订、当前校验、additive impact 合同；错误矩阵/OpenAPI/测试一致；A07 汇总成员用例、图和模式；由成员交叉审阅。依赖 M1/M3/M4/M5 的合同与证据。 | 合同候选与 A07 已在分支；此前合同组定向测试 29 项通过（旧证据）。本轮新增目录原料 API 文档、集成测试；后端最新全量验证记录在 ../../../../outputs/food-s4-final-20261023/backend-verify-final-code.log。 | 合同仍是候选，未进入 main，未取得成员交叉审阅。M2 与 M1/M3/M4/M5 完成合同复核，记录差异和签收。 |
| [SCRUM-87](https://hxj04121.atlassian.net/browse/SCRUM-87) / M3 Xu fy | 影响摘要/筛选/产品名、采用和 CR/分析入口、标签声明预填与提示；真实新环境浏览器故事、权限拒绝 Playwright、演示脚本/deck；由另一成员排练最终 RC。依赖规格、配方、标签和身份接口。 | 新增规格创建/发布表单及回归测试；修复批量采用选择在异步返回后丢失的问题并补测试；修正有效日期含时间戳导致规格选项被隐藏的问题。真实浏览器在全新隔离卷中完成 Spec V2 → 40 个采用 → 40 条影响 → 20 标签校验/送审 → 独立 Checker 批准 → Publisher 发布；60 个产品的历史规格、配方和标签逐一复核。证据含 s3-live-specification-released.png、s3-live-impact-golden.png、s3-live-publication-1.png、s3-live-publication-20.png、s3-live-closed-review-workspace.png。 | 核心浏览器故事通过。应用没有 Supplier 独立演示登录，本次规格录入使用具备 DATA.MAINTAIN 的 Data Admin 身份；若验收要求独立 Supplier 身份仍需产品/权限负责人确认。另一成员排练与真人验收未做。 |
| [SCRUM-88](https://hxj04121.atlassian.net/browse/SCRUM-88) / M4 zhuwenyu26 | Change Manager/Data Admin 演示身份；历史与审计 API/UI；安全扫描、身份映射和权限记录；Maker/Checker 及历史负向覆盖；解决或接受安全 findings。依赖身份、审计、工作流接口。 | 浏览器显示 V1 SUPERSEDED、V2 PUBLISHED，creator/approver/publisher 与时间；历史、当前产品和声明由只读 API 复核。Maker 未获 LABEL.APPROVE 时按钮禁用、直接请求 403；独立 CREATE+APPROVE 的测试身份另测 maker-checker 403。重复送审/批准/发布及非法转换返回 409。证据包括 maker-approval-denied 截图、returned-revision 和负向响应 JSON。 | 本地权限与审计行为有证据；独立安全负责人评审/接受记录缺失。M4 或安全负责人需完成真人安全签收并将 finding 状态关联到最终 SHA。 |
| [SCRUM-89](https://hxj04121.atlassian.net/browse/SCRUM-89) / M5 shj040128shj | 可重复的 Compose fresh reset，基线无本轮业务写入；正负回归；最终 RC SHA 对应 CI/Sonar/安全/浏览器证据；另一成员能按文档独立重跑。依赖脚本、Docker、最终提交和 CI。 | 更新 scripts/s4-m1-spec-v2-demo.sh 和 S4-demo-runbook.md：fresh-ui 只启动干净种子基线，使用唯一 Compose 项目和卷；默认不复用旧项目，不执行 down -v。bash -n 通过。Docker context desktop-linux 可用；最终项目卷标签与项目名匹配。 | 脚本和本地重演通过；同伴独立重跑及最终 SHA 的远端 CI/Sonar/安全检查未执行。M5 同伴按 runbook 重演；之后请求确认再推送和更新 PR。 |

## 隔离环境与业务闭环

本轮主浏览器验收使用全新 Compose 项目 food-s4-ui-final-20261023-4d8a61，唯一卷 food-s4-ui-final-20261023-4d8a61_spectrace_mysql_data，端口 Web 25181、API 28088、MySQL 28324。只读基线为 60 个种子产品、5 个已发布规格、0 个 CR、0 个 ReviewTask；容器健康检查通过。另一套 Playwright 全量测试运行在 food-s4-suite-final-20261023-6a4bd2 独立卷中。没有删除卷或向生产/共享演示环境写入。

使用前缀 FOOD-S4-20261023-4d8a61。规格证据和 CR 描述带此前缀；配方和标签版本由系统生成 UUID，业务契约没有可编辑名称字段，完整关联 ID 集合保存在 JSON manifest 中。S3 负向用例在该独立卷内创建专用测试用户及其 user-role links；没有通过 SQL 写入或修改配方、标签、校验、审批、发布等业务状态，也没有改动既有账号。正向业务操作由网页执行；HTTP GET 用于只读核对，额外的 API 负向检查单独报告。

| 步骤 | 实际结果与完整 ID |
| --- | --- |
| 基线 | 产品 prod_usda_1118468，原配方 formula_1118468_v1，原标签 label_1118468_v1；基线声明不含 SOY。 |
| 新规格 | mat_chocolate_base 的 V2：bbf7e4fd-2f1d-4b61-80ed-6b92e35a4a4d，状态 RELEASED。原料 ing_soy_lecithin；规格行 0ad1d67b-3c04-4fad-bbdf-4bc7834149b5、e9094c00-f0e3-45ca-b463-1e3b0338cc96、684c2457-2144-458a-ab09-c79e5a5642a9。 |
| 采用和影响 | Change Request 97bbe4b4-0d31-4a57-b3ba-e25b8925454a；分析 c440d3ea-95a9-41f1-97c0-25d1b233604e。实际采用 40 个配方，40 条 finding：20 无需动作、20 需复核。 |
| 新配方和标签 | prod_usda_1118468：formula_1118468_v1 → cc37abea-d52d-4c8a-bb50-9f5b4c8597d4；label_1118468_v1 → label_4823033e40b047b4909b9a70522d5d5c。新标签绑定新配方，保留 WHEAT 并增加 SOY CONTAINS。Validation aca56a41-a2ae-42f1-af68-b5ddac46beb6 为 PASSED；ReviewTask a4839c70-4b79-41ed-8f45-3a7435ebeca3。 |
| 发布与当前读取 | 独立 Checker 批准，Publisher 发布。主流程发布 20 个标签；60 个产品的历史标签/配方快照核对通过。当前发布读取指向新标签版本，声明和配方对应。20 个 label/formula/task/validation 完整 ID 见 manifest 与 observations。 |
| 负向验证 | 缺 SOY 声明的标签 label_78af416a48bb4112b88a3c60be226b52 校验 FAILED，run 2876854c-37c8-46e0-8e38-9d5ea215a8c3，阻断码 ALLERGEN_DECLARATION_MISSING。Maker 无权审批得到 403；独立 maker-checker 身份也被拒。重复送审、重复批准、重复发布和非法状态迁移按契约返回 409；具体响应保存在输出目录。 |

正向标签链通过真实网页。后端契约/权限/API 负向断言和发布后当前版本读取另行通过 API/自动化验证；API 成功不替代网页证据。六个 live Playwright 项在默认总套件中因环境保护变量跳过，但均由独立隔离服务/数据的 opt-in 真实测试执行并通过，详见下节。

## 测试、跳过和失败记录

命令、日志、退出码、ID、容器快照和截图位于 ../../../../outputs/food-s4-final-20261023/。当前最终结果：

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 后端 Maven 全量 verify（JDK 25，最终本地源码） | 694 项通过，0 失败/错误/跳过，BUILD SUCCESS，退出码 0。 | backend-verify-final-code.log |
| Catalog GET /api/catalog/ingredients 回归 | 6/6 通过，BUILD SUCCESS。 | backend-catalog-ingredients-regression.log |
| Playwright 默认全套 | 144 passed、6 skipped、0 failed，3.6 分钟，退出码 0。 | frontend-playwright-final.log |
| 独立 Supplier specification 表单测试 | 2/2 通过，含无 DATA.MAINTAIN 权限拒绝。 | supplier-specification-creator-ui-test.log |
| 批量采用权限/错误测试 | 4/4 通过。 | batch-adoption-ui-targeted-test.log |
| 真实隔离浏览器完整 S3/SOY 流程 | 1/1 通过，完成 20 个独立审批发布和历史核验。 | s3-ui-final-live.log、s3-ui-final-live/ |
| 前端生产构建 | 通过；bundle 646.61 kB，Vite 有超过 500 kB 的非阻断提醒。 | frontend-build-final2.log |
| Compose 脚本语法 | bash -n 通过。 | s4-fresh-compose-verified.log |

默认全套中的六项：catalog-live、formula-lifecycle-fullstack、s3-product-flow-live、scrum84-identity-live、scrum84-returned-revision-live、validation-live。它们因各自要求独立服务、LIVE_WRITES 或一次性数据库而未在默认共用 suite 中执行；分别在隔离服务中通过 2、1、1、1、1、1 项，日志分别为 catalog-live-targeted.log、formula-live/、s3-ui-final-live.log、identity-live.log、returned-revision/、validation-live.log。跳过不计入默认全套 PASS，也未阻断本报告覆盖的业务链。

保留的失败记录：JDK 17 运行 Maven 时因 class file 版本 65 高于运行时支持版本而在测试前失败，随后改用 JDK 25 重跑；第一次完整 UI 尝试触发测试断言优先级错误，在规格成功发布后提前中止，随后修复测试并在另一全新隔离卷重跑通过。失败日志仍保存在 backend-verify-java17-initial-failure.log、s3-full-ui-first-attempt.json 与 s3-full-ui-live.log。没有删除断言或单纯放宽超时。初次静态生产构建上的历史浏览器失败也保存在 frontend-playwright-full.log；最终全套使用 Vite dev server，结果见 frontend-playwright-final.log。

## 修改文件和证据

本地代码差异涉及：

- backend/src/main/java/com/spectrace/catalog/interfaces/web/CatalogController.java
- backend/src/test/java/com/spectrace/CatalogIntegrationTest.java
- frontend/src/api/catalog.ts、frontend/src/data/catalog.ts、frontend/src/pages/Materials.tsx
- frontend/src/components/SpecificationCreator.tsx、frontend/src/components/BatchAdoptionPanel.tsx
- frontend/tests/catalog-fixture.ts、frontend/tests/supplier-specification-creator.spec.ts、frontend/tests/batch-adoption-ui.spec.ts、frontend/tests/s3-product-flow-live.spec.ts
- docs/s1-m1/API.md、docs/evidence/S4-demo-runbook.md、docs/evidence/S4-final-report-index-20261010.md（追加 10/23 证据索引）、scripts/s4-m1-spec-v2-demo.sh
- 本报告 docs/evidence/S4-full-team-closeout-candidate-20261023.md

工作区另有两张既有生成截图未纳入代码：test-artifacts/sprint1/screenshots/wo-m4-rbac-http-results.png、test-artifacts/sprint1/screenshots/wo-m5-audit-log-mysql.png。请在审阅 diff 时一并保留或决定是否纳入交付。

主闭环截图：../../../../outputs/food-s4-final-20261023/s3-ui-final-live/；实时进度与完整 ID：../../../../outputs/food-s4-final-20261023/s4-generated-ids-final.json、s3-product-flow-observations.json、s3-product-flow-progress.json。负向校验截图：soy-missing-blocked-validation.png、maker-approval-denied 截图和 returned-revision/。命令与退出码见各日志。

## CI、安全与性能

PR #92 的 GitHub Actions（后端、前端、容器、安全扫描和 Sonar）在旧 SHA 920581ff861e601d1fdbd614a9f434b34d7a7698 有成功记录；没有在本轮本地 diff 上生成对应的远端报告。本地已运行最终后端全量验证、前端完整 Playwright、真实隔离浏览器链和前端 build。本地尝试 Trivy v0.76 文件系统扫描，但在扫描开始前，官方 DB 镜像 mirror.gcr.io 与备用 ghcr.io 的连接均以 EOF 失败，退出码 1；这是数据库下载失败，不是扫描发现，也不算 PASS。较宽范围的第一次扫描因包含本机生成目录而中断，未产生结果。日志见 trivy-filesystem-final.log 与 trivy-filesystem-sources-final.log。未运行 OWASP Dependency-Check/Sonar 本地替代扫描；当前工作流的安全与 Sonar gate 由 CI vars/secrets 启用，最终 diff 需在确认后更新 PR 并绑定新 SHA 才能证明。没有用旧 SHA 的绿色结果冒充最终版本通过。

查看 Jira Sprint 74 的五项任务时未发现独立性能指标或门槛，因此没有虚构 benchmark 或 PASS。前端 build 的 Vite 提示 JS bundle 646.61 kB，超过默认 500 kB 建议值；这是非阻断提示，仍应由负责人判断是否属于 S4 性能验收范围。工作流容器 job 包含 down -v 清理步骤，本轮没有运行可能删除卷的完整 CI job；隔离项目和卷均保留。
## 演示材料

已更新现有 Spaces 演示文稿「FoodLabelFlow — Sprint 4 SOY Demo and Acceptance」（Page ID page_70adc782b4f481919c6ad74cb9344b6e），保留 8 页原布局，改为本轮 fresh-ui 环境、规格/CR/分析/配方/标签 ID、真实浏览器结果、缺声明与权限负例、全员任务状态及剩余真人验收门。已逐页渲染并检查排版；本地渲染与文本提取副本位于 ../../../../outputs/food-s4-final-20261023/deck/。
## 尚未完成和最小下一步

1. M1/M2/M3/M4/M5 负责人分别复核其 Jira 验收与本地证据；M2 完成跨模块合同审阅，M4 安排独立安全负责人评审。
2. 由另一成员在全新项目依照 runbook 独立排练并记录成员、时间、最终 SHA。这是必须真人完成的验收，不能代签。
3. 确认并提供项目 Definition of Done 页面或导出，补齐目前不可读取的要求。
4. 审阅当前 diff 和最终测试结果后，另行请求确认，再提交/推送并更新 PR #92；这会生成新 SHA。之后重新跑 CI、Sonar、安全扫描并把证据绑定到新 SHA。
5. 获准后再更新 Jira 评论/状态；本地测试通过不自动将任务标记 Done。

当前状态：核心技术实现和隔离真实浏览器故事已通过；全员真人签收、同伴排练、DoD 核实及远端交付尚未完成。因此不能写“FOOD 单体 S4 全员完成”。
