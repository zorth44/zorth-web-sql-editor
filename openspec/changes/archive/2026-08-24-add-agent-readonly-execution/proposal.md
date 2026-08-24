## Why

AI Platform 的 Database Agent 将改调本服务的元数据和执行接口，而不再自己持有 JDBC。现有 `POST /api/v1/sql/executions` 允许任意单条 SQL（`autoCommit=true`），超时固定 60 秒，执行历史分不清来源且 `client_ip` 从未写入。Agent 场景必须在服务端强制只读，否则只读完全依赖对方 parser 和人工确认的只读账号；同时需要按请求缩短超时，并把编辑器与 Agent 的执行审计分开。

## What Changes

- 现有执行接口增加可选 `readOnly`。为 `true` 时，在目标库执行前拒绝非只读单语句，返回稳定错误码（建议 `422 READ_ONLY_VIOLATION`），不打开写路径。编辑器不传该字段时行为与现在相同，仍可跑 DML/DDL。
- 现有执行接口增加可选 `timeoutSeconds`。调用方传入时夹在 `1` 与配置上限之间，同时约束 JDBC `queryTimeout` 和本次 `WebAsyncTask` 超时；不传则继续用服务级默认 60 秒。
- 现有执行接口增加可选 `source`：`WEB_SQL_EDITOR` | `AI_AGENT`。缺省为 `WEB_SQL_EDITOR`，写入执行历史。这是审计归因，不是鉴权，不能当成只读防线。
- 执行开始时真正写入 `client_ip`：只信任网关在可信代理条件下设置的转发头，否则记直连地址；禁止盲信任意 `X-Forwarded-For`。
- Token 透传、元数据接口、单用户并发 3、数据源只读账号、批量表结构、按备注搜表：**不在本次范围**。鉴权已按用户 Bearer 工作；并发配额只限制执行、不限制元数据，保持现状。

## Capabilities

### New Capabilities

无。不新开 Agent 专用执行 API，避免权限、连接池、取消、超时和历史分叉。

### Modified Capabilities

- `backend-sql-editor`: 单条执行请求支持可选 `readOnly` 与 `timeoutSeconds`；只读模式在扫描通过后、目标执行前拦截写语句；只读违规使用稳定 API 错误；按请求超时同时作用于 JDBC 与异步 HTTP 超时。
- `backend-sql-history-export`: 历史记录增加 `source` 快照并在列表/详情返回；`client_ip` 在创建 RUNNING 记录时按可信代理规则写入，不再恒为 NULL。

## Impact

- 后端执行：`SqlExecutionRequest` / `SqlExecutionController` / `SqlExecutionService` / `SqlStatementClassifier`（或并列的只读判定）、错误码表、`docs/backend-development-spec.md`。
- 后端历史：Flyway 新列 `source`、`ExecutionHistoryRecord` / mapper / `start()` 写入 `source` 与 `client_ip`、列表与详情 DTO。
- 配置：可信代理 / 转发头（若尚未配置）；`timeoutSeconds` 上限沿用现有 `sql-editor.execution.timeout-seconds`。
- 前端：编辑器可不改请求体（缺省非只读、来源编辑器、默认超时）。TypeScript 契约可补可选字段，避免后续漂移。历史 UI 若展示 `source` 为加分项，不做阻断。
- 调用方：`zorth-ai-service` 在 Phase 2 开始传 `readOnly=true`、`source=AI_AGENT` 和更短的 `timeoutSeconds`。因本服务 `fail-on-unknown-properties: true`，这些字段必须先在本服务落地，对方才能开始发送。
- 非目标：不改 `GET /tables`、`/table-detail`、授权过滤器、连接池与 `max-concurrent-per-user`。
