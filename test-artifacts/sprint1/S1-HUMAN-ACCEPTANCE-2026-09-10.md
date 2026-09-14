# S1 Human Acceptance（简版）

日期：2026-09-10
测试人：Sun Huajian（SHJ）

## Section 7.3 验证记录

| Work order | 验证内容 | 测试结果 | 截图 |
|---|---|---|---|
| S1-M1 | 创建 Formula；发布 Formula；查看历史版本 | PASS（自验） | `wo-m1-formula-published.png`、`wo-m1-version-history.png` |
| S1-M2 | Formula API/错误路径验证 | PASS（自验） | `wo-m3-formula-create-preview.png` |
| S1-M3 | 浏览器创建/发布 Formula；查看历史版本 | PASS（自验） | `wo-m3-formula-create-preview.png`、`wo-m1-formula-published.png`、`wo-m1-version-history.png` |
| S1-M4 | RBAC：未登录 401；无权限用户 403；管理员可创建/发布 | PASS（自验） | `wo-m4-rbac-http-results.png` |
| S1-M5 | 创建/发布后查看详情与历史；Audit 记录验证 | PASS（自验） | `wo-m5-product-detail-after-reload.png`、`wo-m5-audit-log-mysql.png` |

截图目录：`test-artifacts/sprint1/screenshots/`

结论：Formula 创建、发布、详情、历史版本、RBAC 和 Audit 均已实际验证通过。当前记录为本人自验；尚未有其他组员进行独立人工复核，因此不标记为 peer approval。
