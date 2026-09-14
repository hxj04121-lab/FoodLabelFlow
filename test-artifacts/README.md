# Test artifacts

测试产生的文件按 Sprint 存放，不要写入项目根目录或 `frontend/test-results/`。

当前 Sprint：`sprint2`

目录约定：

- `screenshots/`：测试截图
- `playwright-results/`：Playwright 运行结果和失败附件
- `playwright-report/`：Playwright HTML 报告
- `evidence/`：需要提交到 Git 的人工验收文档、报告和固定证据

默认运行时使用 `test-artifacts/sprint2/`。切换 Sprint 时设置环境变量，例如：

```bash
TEST_ARTIFACT_SPRINT=sprint3 npm run test:e2e
```
