# SQL 优化器：启动与验收约定

日期：2026-10-13。本文准备启动任务，不代表业务实现或真实联调已经完成。

## 工作区与运行环境

主工作区：`/Users/zorth/Code/bddf/zorth-web-sql-editor`，分支 `main`，只负责共同准备与最终协调。

| 执行侧 | 从此路径启动独立 Codex 会话 | 分支 | 服务端口 |
| --- | --- | --- | --- |
| 前端 | `.worktrees/sql-optimizer-frontend` | `feat/sql-optimizer-frontend` | Vite 5173，Playwright preview 4173 |
| 后端 | `.worktrees/sql-optimizer-backend` | `feat/sql-optimizer-backend` | 本地 API 18080；WireMock/Testcontainers 动态端口 |

各会话实际工作区必须是表中的 worktree，不能仅在父会话中 `cd` 后假设权限边界已改变。主目录 `.worktrees/` 已忽略。两个分支从同一份本地准备提交创建；未推送或创建 PR。

已发现的环境：Node 24.21.0、pnpm 10.33.3、Playwright 1.54.1、OpenSpec 1.5.0、JDK 17.0.15、Maven 3.9.15、Docker 29.4.0。Maven 默认选 JDK 25，后端命令须显式指定 JDK 17：

```bash
cd service
env JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home mvn -Dapi.version=1.44 test
env JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home mvn -Dapi.version=1.44 verify
```

这一路径为本机准备结果；其他机器需重新定位 JDK。服务本地启动时配置 `SERVER_PORT=18080`。现有容器测试自行创建 MySQL/PostgreSQL 隔离数据库，不需要手工建立共享测试 schema。WireMock 用于本地认证/Agent fixture；不要使用生产 Token 做测试。

前端使用独立安装的 node_modules，不复制或链接主目录的可变依赖目录。准备时配置忽略的 `web/.env.development.local`，仅 development mode 的 API Mock 为 true，避免影响默认生产 build。Playwright 已在配置中使用 test mode 与 Mock。若已有 `.env.local` 启用 Mock，生产检查须显式运行 `env VITE_ENABLE_API_MOCK=false pnpm build`，不得删除生产保护。

## 前端启动任务

在前端 worktree 新会话中下达以下完整任务；如需跨轮自动继续，明确要求“创建持续 Goal 并执行以下任务”。开工准备不自动激活 Goal。

```text
阅读 AGENTS.md、docs/plan-sql-optimizer-async-tasks.md、
docs/sql-optimizer-api-contract.md 和 docs/plan-sql-optimizer-frontend.md。

完成 SQL 优化器前端本地交付：FE-01～FE-05，
创建 add-sql-optimizer-frontend OpenSpec change，将清单映射到 tasks.md。
保持共享契约不变，只修改前端归属文件。

按依赖持续实现、测试、修复、自查和本地阶段提交。
每完成一个任务编号不等待确认；普通实现选择和测试失败自行处理。
缺环境先准备授权范围内的依赖，确有外部阻塞时记录并继续独立工作。
共享契约、产品范围、凭证政策或另一侧文件需要改变时提出具体问题。

以 MSW 完成前端闭环，执行 typecheck、lint、test、build 和优化器 E2E。
提供可运行页面预览、测试结果、提交记录、已知问题和恢复记录。
FE-06 真实联调单独标记待验收，不将 Mock 验收当真实联调。
不自行推送、合并或发布。
```

## 后端启动任务

```text
阅读 AGENTS.md、docs/plan-sql-optimizer-async-tasks.md、
docs/sql-optimizer-api-contract.md 和 docs/plan-sql-optimizer-backend.md。

完成 SQL 优化器后端本地交付：BE-01～BE-07 中的本地可验收部分，
创建 add-sql-optimizer-backend OpenSpec change，将清单映射到 tasks.md。
实现契约 API、持久化队列、Worker、报告校验、凭证保护和本地联调夹具。
保持共享契约不变，只修改后端归属文件。

按依赖持续实现、测试、修复、自查和本地阶段提交。
每完成一个任务编号不等待确认；普通实现选择和测试失败自行处理。
使用 JDK 17、独立 Testcontainers 数据库和 Stub/WireMock Agent。
执行 mvn test 和 mvn verify，明确报告容器测试的实际执行/跳过情况。
缺环境先准备授权范围内的依赖，确有外部阻塞时记录并继续独立工作。
共享契约、产品范围、凭证政策或另一侧文件需要改变时提出具体问题。

交付可运行的本地 API/Worker 方式、测试证据、提交记录和恢复记录。
生产身份、真实 Agent 回调、真实前后端联调分别列出外部依赖与未完成项。
不得绕过认证，不将 Stub 验收当真实链路完成。
不自行推送、合并或发布。
```

## 完成标准与阻塞

阶段进展写入本侧 `execution.md`，完成状态只维护在本侧 `tasks.md`。阶段提交与汇报后继续下一项。

前端本地完成必须有页面闭环和前端门禁/E2E 证据；后端本地完成必须有真实数据库持久化、Worker 生命周期/恢复/迟到结果保护、权限/幂等/配额以及 Stub Agent 的证据。已失败或被跳过的检查不能记成通过。

以下是独立外部验收依赖，不阻塞 Mock/Stub 实现，但整体上线前必须解决：

- 生产 Agent `CurrentUserPort`、已验证 Bearer 和内部取证回调配置。
- 短期凭证托管政策确认、实际到期信息、独立密钥来源和生产配置。
- 真实 Agent 地址、内部 caller key 配对与数据源集成启用。
- 关闭 Mock 的前后端联合验收及生产环境批准。

依赖阻塞记录必须包含：受影响项、已尝试方式、证据、需要的具体输入和仍可推进的任务。只有所有剩余工作均受外部依赖阻塞时，才结束本地执行并报告。

## 准备验证结果

这些是现有代码的环境检查，不能用作尚未实现的 SQL 优化器验收证据。worktree 创建后的依赖安装与最终分支检查记录于主工作区忽略的 `.worktrees/preflight.md`。

| 检查 | 结果 |
| --- | --- |
| 前端 typecheck | 通过 |
| 前端 lint | 既有失败：`web/src/components/copilot/CopilotPanel.vue:302` 的 `vue/no-v-html` warning；门禁要求零 warning。本次准备不修改业务组件，实施交付仍须解决或提出明确决策 |
| 前端单测 | 49 个文件、311 个测试通过 |
| 前端生产 build | 显式关闭 Mock 后通过；有现有 bundle 大小提示 |
| 浏览器 smoke | Chromium 登录/列表/分页/退出场景 1 个通过 |
| Docker | 29.4.0 可用；访问 socket/测试绑定端口需要实际工具权限 |
| 后端 verify | JDK 17、`-Dapi.version=1.44`、实际端口/Docker 权限下通过；50 个 suite、222 个测试，0 failure/error/skipped，MySQL/PostgreSQL 集成测试实际执行 |
