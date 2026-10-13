# 项目执行约定

## 连续执行

- 用户下达完整实施任务后，任务编号是 Agent 的内部清单。按依赖顺序持续阅读、实现、测试、修复、自查和阶段提交，不在每个编号完成后等待“继续”。
- 常规组件拆分、文件组织、既定范围内的实现选择和普通测试失败由 Agent 自行处理。阶段汇报用于告知进展，不构成验收或结束执行。
- 环境缺失先检查并准备授权范围内的依赖、服务和测试配置；工具要求升级权限时走实际审批流程，不以提示词代替权限设置。
- 外部问题只阻塞依赖它的工作。记录问题、尝试、证据和所需输入，同时推进独立任务。只有交付标准达到，或全部剩余工作均受外部依赖阻塞时，才结束实施并交付。
- 不得以删除测试、放宽断言、跳过门禁或降低权限保护来制造通过结果。既有失败须区分基线问题与本次回归。

## SQL 优化器分工

先读 `docs/plan-sql-optimizer-async-tasks.md`、`docs/sql-optimizer-api-contract.md` 和本侧实施方案。

- `feat/sql-optimizer-frontend`：拥有 `web/**`、前端实施方案与 `openspec/changes/add-sql-optimizer-frontend/**`。
- `feat/sql-optimizer-backend`：拥有 `service/**`、后端实施方案与 `openspec/changes/add-sql-optimizer-backend/**`。
- 共享契约、共同入口、根配置和另一侧归属文件不得由实现分支独自修改。需要改变契约、产品范围、凭证政策或文件归属时，提出具体决策问题并继续独立工作。
- 各侧在自己的 worktree 和分支执行。不要编辑其他 worktree；不要自行启动额外 Agent。主目录中的 `.worktrees/` 仅是隔离工作区容器，搜索代码时应排除它，避免将不同分支误当成当前代码。根工作区用于共同准备和最终协调。
- 实施启动时创建本侧 OpenSpec change，将方案映射为 tasks；`tasks.md` 是任务完成状态的唯一清单。本侧方案记录实施说明和验收证据，不重复维护另一套完成勾选。
- Mock/Stub、本地真实数据库与真实 Agent 联调分别验收；不能将 Mock 验收标成生产链路完成。

## 验证与交付

- 前端：在 `web/` 运行 `pnpm typecheck`、`pnpm lint`、`pnpm test`、`pnpm build`，再运行优化器 Playwright 场景。依赖按 lockfile 安装，生产禁止启用 Mock。
- 后端：在 `service/` 使用 JDK 17 运行 Maven，编译保持 Java 8 兼容；运行 `mvn test`，交付前运行 `mvn verify`。数据库集成测试沿用 Testcontainers 独立容器；Docker 29 如协商失败，按 service README 使用 `-Dapi.version=1.44`。
- 不共享破坏性测试 schema；不同 worktree 服务使用独立端口。不得终止不属于本任务的进程。
- 按可审查的功能阶段提交，只暂存本侧任务文件；不提交凭证、个人环境配置和构建产物。
- 交付包含运行方式/页面预览、变更说明、实际测试结果（含失败和跳过）、提交记录、已知问题及外部依赖。
- 实施授权包括本地阶段提交；推送、PR 按启动任务的授权执行。不得自行合并、发布或接入生产。

## 长任务恢复

每个功能阶段在本侧 change 的 `execution.md` 记录当前阶段、最近提交、测试命令及结果、下一步和阻塞。恢复时先读本文件、本侧 tasks/execution 与 git 状态，继续尚未完成的工作，不重做已验收步骤。

完整任务可由用户显式启用持续 Goal。Goal 的完成条件以证据为准；真实联调如未包含在本地目标中，必须单独列为待验收，不能标成整体完成。
