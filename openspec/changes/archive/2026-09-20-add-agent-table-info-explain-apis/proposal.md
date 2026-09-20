# Proposal

## Why

Spring AI / AgentScope 一类外部 Agent 要把本服务当数据库能力后端，用少而硬的 HTTP 口包装成 `@Tool`。今天 `table-detail` 已有 `SHOW CREATE TABLE` 的 DDL，但没有表级统计；执行计划只能让模型走通用 `POST /api/v1/sql/executions`，`readOnly` 又会把 `EXPLAIN ANALYZE` 当只读放行。Agent 需要按表名一次拿到 DDL+统计，以及「只出计划」和「实测计划」两条分开的、可监管入口。

## What Changes

- 扩展 `GET /api/v1/data-sources/{id}/table-detail`：在现有列、主键、索引、`ddl` 之外返回引擎中性的表统计（估计行数、引擎、数据/索引体积等）。MySQL / GBase 内部用 `SHOW TABLE STATUS`；PostgreSQL 用 `pg_class` / `pg_stat`。统计读失败时 `stats` 为 `null`，不让整次表结构失败。
- 新增 `POST /api/v1/sql/explains`：对单条只读语句由引擎改写成 `EXPLAIN`（或等价），**拒绝 ANALYZE**，走现有执行链（配额、超时、取消、只读连接、历史）。
- 新增 `POST /api/v1/sql/explains:analyze`：单独的监管入口。服务端强制改写成 `EXPLAIN ANALYZE`（或等价），只接受会出结果集的语句，独立开关（缺省关闭）、独立超时上限、独立历史 `source`。
- 通用 `POST /api/v1/sql/executions` 在 `readOnly=true` 时拒绝 `EXPLAIN ANALYZE`，避免 Agent 的 `executeQuery` 绕过计划口。编辑器不传 `readOnly` 时仍可手跑 ANALYZE。
- 两条 explain 口由服务端写入历史来源，不接受客户端 `source`。计划口为 `AI_AGENT_EXPLAIN`，实测口为 `AI_AGENT_EXPLAIN_ANALYZE`。历史语句存改写后的文本。

非目标：MCP 运行时、把 OpenAPI 直接喂给模型、通用任意 SQL Tool、在 `explains` 上加 `analyze` 开关、新开一套 JDBC 执行后端、改授权服务能力集、本仓实现 AgentScope `@Tool`、Copilot / 表对象页展示统计。

## Capabilities

### New Capabilities

无。能力口挂在现有 SQL 编辑器、引擎 SPI 和历史上，不另开 Agent 平台 capability。

### Modified Capabilities

- `backend-sql-editor`: `table-detail` 返回表统计；新增计划-only 与 ANALYZE 两条 explain API；`readOnly` 执行拒绝 ANALYZE。
- `backend-engine-spi`: 引擎负责表统计采集，以及把用户 SQL 改写成 EXPLAIN / EXPLAIN ANALYZE。
- `backend-postgresql-engine`: PostgreSQL 用中性统计源和 `EXPLAIN (FORMAT JSON)` / `EXPLAIN (ANALYZE, FORMAT JSON)`，不使用 MySQL `SHOW`。
- `backend-sql-history-export`: 历史 `source` 增加 `AI_AGENT_EXPLAIN` 与 `AI_AGENT_EXPLAIN_ANALYZE`。

## Impact

- 后端元数据：`TableDetailResponse`、`MysqlCatalogs` / `PostgresCatalogs`（GBase 继续委托 MySQL catalog 则自动带上统计）、`MetadataController` 契约与文档。
- 后端引擎：`EngineSupport` 增加统计与 explain 改写；编排层禁止按引擎 id 字符串分支。
- 后端执行：新 Controller 薄封装现有 `SqlExecutionService`；只读分类器或并列检测拒绝 ANALYZE；配置项 `sql-editor.explain-analyze.enabled`（缺省 `false`）与独立超时上限。
- 后端历史：`ExecutionSource` 允许两个新值；explain 口强制写入对应 source。
- 前端：TypeScript 契约补 `stats`、新 `source` 字面量和 explain 请求类型，避免漂移。工作台 UI 不展示统计、不接 explain 口。
- 调用方：Spring AI / AgentScope（及现有 `zorth-ai-service`）把 `getTableInfo` / `explainSql` 包成 Tool；`explainAnalyzeSql` 默认不注册。本仓不实现这些 Tool。
- 文档：`docs/backend-development-spec.md` 错误码与 API 说明。
