# SQL 优化器本地交付证据（2026-10-13）

以下摘自已删除的前后端实施方案，保留当时测试、提交与运行记录；其中分支、会话、临时路径及“未合并”等措辞是历史状态，不作为当前指令。当前生产待办见[生产待验收项](../sql-optimizer-production-readiness.md)。

## 前端交付原始记录


实现包含 capability 导航与直接页面访问保护、Monaco 创建表单、数据源/NAMESPACE、状态筛选/不透明游标栈、用户与产品隔离的查询、约 5 秒活跃轮询、后台暂停与聚焦刷新、安全报告和终态重扫。创建/重扫失去响应时保留原 payload/key，显式恢复操作；编辑输入视为新操作。详情地址仅含 taskId。

报告按 verdict/issues/suggestions/limitations 顺序展示，区分组织重要性与性能严重度，关联建议能定位问题，报告版本与明显非法形状会显示格式异常。SQL 为只读快照，首版仅复制文字建议。证据缺口始终可见，不宣称全部规则通过。

本侧顺带解决两个验收发现的问题：既有 Copilot 的 v-html lint 门禁用 Vue 节点与显式标签/属性白名单解决，保留 Markdown 格式；现有 scanner 不再将末尾纯注释误当第二条语句。390px 优化器布局与导航通过浏览器验收，只有优化器路由解除既有 body 1024px 最小宽度。

### 运行与预览

在本 worktree 的 `web/` 运行 `pnpm dev --host 127.0.0.1 --port 5173 --strictPort`，打开 http://127.0.0.1:5173/sql-optimizer。准备阶段的忽略文件 `.env.development.local` 仅开发模式启用 Mock；没有该文件时可在命令前设置 `VITE_ENABLE_API_MOCK=true`（仅 development）。使用 Mock 用户 `normal` 与任意非空测试密码登录，不输入真实凭证。

本次预览已启动，HTTP 200；终端 session 89923 为本任务的 Vite 服务。停止时仅处理本任务该服务，不终止其他 worktree 进程。原生浏览器 connector 不可用，页面视觉验收使用实际 Playwright Chromium 截图：`/private/tmp/sql-optimizer-report.png`、`/private/tmp/sql-optimizer-mobile.png`（临时本机产物，不提交）。

开发 Mock 名称夹具：默认混合报告；`mock:clean` 无问题；`mock:limited` 仅缺口；`mock:expired` 凭证过期；`mock:lost` 创建与重扫首次响应丢失；`mock:quota` 429；`mock:conflict` 409；`mock:network` 已取得状态后的单次查询网络中断。详情 `/sql-optimizer/tasks/mock-forbidden` 模拟 HTTP 403；不存在/越权任务为 404。`no-optimize-token` Mock Session 不授予 SQL_OPTIMIZE。所有这些夹具仅在 Mock 中，不构成生产链路保证。

### 实际验证

| 命令（web/） | 结果 |
| --- | --- |
| `pnpm typecheck` | 通过 |
| `pnpm lint` | 通过，0 error / 0 warning；既有 Copilot 门禁已修复 |
| `pnpm test` | 交付时 55 文件、339 测试通过，无失败和跳过；2026-10-13 在当前依赖环境下复跑有 2 个既有 CSV 导出断言失败（jsdom `Blob` 缺 `text()`），根因与复现见[验收记录第 5 节](2026-10-13-sql-optimizer-live-acceptance.md) |
| `env VITE_ENABLE_API_MOCK=false pnpm build` | 生产构建通过；生产 Mock 保护保留 |
| `pnpm e2e e2e/sql-optimizer.spec.ts e2e/copilot.spec.ts` | 12/12 通过（优化器 7 + Copilot 5），无跳过；含390px、完整优化器场景与 Copilot 回归 |
| `openspec validate add-sql-optimizer-frontend --strict`（worktree 根） | 通过 |
| `git diff --check`（worktree 根） | 通过 |

过程失败均保留说明并已修复：可选 signal 类型、格式化后的模板多语句事件、测试参数类型、scanner 末尾注释、多次 E2E 定位器（隐式 label、Monaco IME textarea）、窄屏 body 最小宽度。失败的浏览器运行曾中断以修复共享定位器，最终完整运行结果为准；未删除测试或放宽断言。Vite 监听曾被沙箱 EPERM 拒绝，通过实际权限审批后已启动。

### 提交与恢复

- `d55b78a`：OpenSpec 计划与执行记录。
- `830dd69`：API 精确 DTO、Mock 生命周期与契约测试。
- `30ef7ff`：导航/表单/列表/详情/轮询/报告/重扫与组件测试，Copilot/scanner 门禁修复。
- `031996e`：浏览器验收、移动端布局、Mock 网络夹具与交付证据。
- 最终恢复文档提交见本侧 execution.md 与 `git log --oneline 3a81b8c..HEAD`。

### 已知限制与外部依赖

生产构建仍有既有 Monaco bundle >500kB 提示，不影响构建门禁。Mock 通过 sessionStorage 模拟刷新后任务读取，仅服务开发验收。未推送、未创建 PR、未合并或发布。

FE-06 已于 2026-10-13 完成：后端已合并 main，前端关闭 Mock 对接真实四接口、真实 SQL_OPTIMIZE 账号、真实数据源与真实 Agent Platform，核对 DTO/null/游标/幂等、离页执行、关页/刷新/重扫、跨用户 404、能力 403、游标 400 与凭证过期再登录链路，14 个场景全部通过，任务与报告证据见[验收记录](2026-10-13-sql-optimizer-live-acceptance.md)，可复现驱动见 [`tools/optimizer-live/`](../../tools/optimizer-live/README.md)。本次联调 `web/**` 零改动。生产身份适配器、短期凭证托管审批与生产开关不属于前端范围，仍待外部输入。前端没有修改 service、共享契约、根配置或后端方案。

## 后端交付原始记录


完成状态以 `openspec/changes/archive/2026-10-13-add-sql-optimizer-backend/tasks.md` 为唯一清单；本方案中的原始条目继续作为范围基线，不重复打勾。恢复入口为同 change 的 `execution.md`。仅修改后端归属文件，共享契约 v1 未变。

实现四个任务 API、V8 task/attempt/credential 表、短事务 JDBC repository、数据库持久队列和独立 Worker。创建 mutex 同时串行化幂等与配额；幂等重放检查先于新增配额和执行资格。读取按用户/产品隔离并实时校验可见数据源；游标签名绑定身份/过滤/页大小。列表数据库投影只取 160 字符 SQL 预览，不加载完整报告。引擎 scanner 与安全 evaluator 外再保守拒绝写入 CTE、SELECT INTO 和可执行注释；它不代替 SQL/模型事实验证。

功能默认关闭。显式 user/product allowlist 与 integration-ready 同时满足才授予/校验 SQL_OPTIMIZE。短期已验证 Token 使用独立 AES-GCM 密钥、task/purpose/version AAD、真实到期与 TTL 的较早值，并预留完整 attempt 预算。禁止复用当前数据源密码密钥，缺密钥时失败关闭。Worker 直接调用 uncached AuthClient、核对身份/产品/权限，发送前确认有效 lease，后台不读取请求 ThreadLocal。终态与到期清理密文；功能关闭仍保留维护职责。

领取前先拿本地执行槽，领取/attempt/结果用短事务，HTTP 等待不持有元库事务。心跳不修改任务 version；完成必须匹配 RUNNING/current attempt/version/有效 lease 与 hard deadline。过期 running 统一 WORKER_LOST，迟到结果不能覆盖；无自动 POST 重发。StructuredOutput 校验类型/枚举/ID/引用/分类证据，限制响应字节和 JSON 深度，拒绝重复键和多根 JSON，保存 schema 1 快照与派生 summary。

### 实际验证

- JDK 17 + `mvn -q -Dapi.version=1.44 test`：53 suites，241 tests，0 failure/error/skipped。
- JDK 17 + `mvn -q -Dapi.version=1.44 verify`：同样 241 tests，无失败/跳过；打包及 Animal Sniffer Java 8 API 检查通过。
- 新增 19 tests：5 边界、2 配置、12 独立 MySQL/HTTP 集成。既有 MySQL 11 tests、PostgreSQL 1 test 实际执行；未用跳过门禁替代容器验收。
- 并发幂等/配额、两个 Worker 抢占、恢复队列、自己的心跳、lease/hard budget、迟到 HTTP、零元库长事务、Token 到期/身份变化/密文破坏/缺密钥/事务回滚/终态清理/retention cascade 均有测试。
- 真实随机端口 Spring HTTP 内部 validate/EXPLAIN 回调验证 Bearer 与配对内部 key；内部 key 单独不能认证用户。
- 本地打包服务 API `http://127.0.0.1:18080`、Stub `127.0.0.1:18090`，独立 MySQL 动态 loopback 端口。SELECT、JOIN 的 validate 与 plan-only EXPLAIN 实际执行；从未调用 query/ANALYZE。模拟 EXPLAIN/索引信息缺口保持 limitations，模拟 503 落 AGENT_UNAVAILABLE。
- 创建返回 202/Location；失败详情为 200；无 body retry 产生新任务并关联 sourceTaskId，实际轮询到终态。
- OpenSpec strict validate 与 git diff --check 通过。所有测试失败均已修复；测试容器关闭后仍活跃的 cached Spring context 产生 Hikari 已关闭连接校验告警，不是失败/跳过。

### 运行与联调证据

`service/tools/README-optimizer-local.md` 给出可重复的 API/Worker/auth/Agent fixture 运行方式。启动 `python3 tools/optimizer_local_fixture.py`，运行时生成独立 Token、密钥、测试账户/产品和可见数据源，凭证仅存在私有临时文件，不入仓库。使用打印的 client.json 路径调用 CLI，无需将 Token 写到终端参数或文档。Ctrl-C 或 SIGTERM 到该 harness 的 PID 只关闭本次 API 和容器。

脱敏实际响应保存在 `service/src/test/resources/optimization/local-acceptance/`：`tasks.json` 的成功/证据不足/失败，`join-task.json` 与 `join-callback.json` 的 JOIN 计划取证，`retry-task.json` 的重扫关联。示例 taskIds 仅属于本地 fixture，不代表生产链路。

本地阶段提交：c24862b（DTO/报告/Agent 边界）、0f4d497（持久 API/安全 Worker）；最终交付提交见 execution.md 与本分支 git log。未推送、创建 PR、合并或发布。
