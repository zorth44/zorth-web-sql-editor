# SQL 优化器

## 代码与规格

- 前端：`web/src/pages/SqlOptimizerPage.vue`（任务列表首页）、`SqlOptimizerNewPage.vue`（`/sql-optimizer/new` 创建页）、`SqlOptimizationTaskPage.vue`、`components/sql-optimizer/`、`sql-optimizer/`、`api/sql-optimization.ts`。Mock 位于 `web/src/mocks/sql-optimization-*`。
- 后端：`service/src/main/java/com/bocsoft/sqleditor/optimization/`（API、repository、Scheduler、Worker、Agent client、报告验证和凭证 vault），表迁移见 `service/src/main/resources/db/migration/`。
- 行为规格：[后端](../../openspec/specs/backend-sql-optimization/spec.md)、[UI](../../openspec/specs/sql-optimizer-ui/spec.md)。精确线协议见[共享契约 v1](../sql-optimizer-api-contract.md)。

## 关键维护边界

浏览器从 `/sql-optimizer/new` 提交单条只读 SQL，service 持久化后返回 202，独立 Worker 调用固定 `sql-lite-optimizer` 同步接口；Agent 携带同一 Bearer 与配对内部 key 回调数据库 API 取证。浏览器不直连 Agent，不维持后台执行请求。`/sql-optimizer` 只列出当前用户的任务。

- 数据库队列持久化 QUEUED/RUNNING/SUCCEEDED/FAILED。领取前先获得执行槽，HTTP 等待不持有元库事务；结果写入必须匹配当前 attempt/version、有效 lease 和 hard deadline。心跳不改变领取版本；过期 RUNNING 落 WORKER_LOST，迟到结果不能覆盖终态，不透明自动重发 Agent POST。
- 幂等重放先于新增配额，同 key 不同请求冲突；响应丢失显式恢复原 payload/key，编辑输入生成新 key。重扫只允许终态，新任务关联 sourceTaskId，不覆盖原报告。
- 读取按当前用户/产品和实时数据源可见性隔离。Worker 使用未缓存的远程重新授权并显式传 AuthContext，不依赖请求 ThreadLocal。短期凭证使用独立 AES-GCM 密钥与 task/purpose/version 绑定，取真实到期与 TTL 的较早值，预留 attempt 预算，终态及过期清理密文。
- 只接受有界 structuredOutput，校验结构、枚举、唯一 ID、引用与分类证据；非法或超限报告失败，不从 prose/Markdown 猜报告，不截断或补造字段。格式校验不证明证据真伪，计划估算不等于实测性能。
- 报告按文本展示，limitations 保持可见；SUCCEEDED 只表示取得有效报告，不宣称全部规则或全库扫描通过，不执行原 SQL、建议或 DDL。
- 查询缓存绑定用户/产品，退出清理；活跃任务约 5 秒轮询，后台/卸载暂停、聚焦刷新、终态停止。详情 URL 仅含 taskId，草稿不放 URL/localStorage。

## 验证、运行与生产状态

前端门禁见[代码标准](../code-standards.md)，专项浏览器测试为 `web/e2e/sql-optimizer.spec.ts`。后端覆盖幂等/配额竞态、抢占、租约/重启/迟到结果、凭证与身份变化、报告边界及清理，使用独立 Testcontainers 数据库。

本地 Mock 开发见 [web README](../../web/README.md)。真实链路见[本地开发](../local-development.md)与[联调驱动](../../tools/optimizer-live/README.md)；数据库 + Stub Agent 运行见[后端本地 fixture](../../service/tools/README-optimizer-local.md)。

2026-10-13 本地开发和关闭 Mock 的真实 Agent 联调已完成，证据见[交付归档](../archive/2026-10-13-sql-optimizer-delivery.md)与[真实联调归档](../archive/2026-10-13-sql-optimizer-live-acceptance.md)。生产未启用；后续接入必须先看[生产待验收项](../sql-optimizer-production-readiness.md)。
