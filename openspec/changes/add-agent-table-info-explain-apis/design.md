# Design

## Context

See proposal.md for motivation. Today `table-detail` already returns JDBC columns/indexes plus MySQL `SHOW CREATE TABLE` as `ddl`. PostgreSQL synthesizes DDL. Agents that wrap this service (Spring AI / AgentScope, and `zorth-ai-service`) still lack cardinality/size, and they can only obtain plans by sending `EXPLAIN` through `POST /api/v1/sql/executions`. `readOnly=true` classifies `EXPLAIN` as `SELECT`, so `EXPLAIN ANALYZE` is currently a legal read-only query.

Constraints carried forward from `add-agent-readonly-execution`: do not fork JDBC execution, cancellation, quota, or history; `source` is audit not authorization; `fail-on-unknown-properties: true`; Java 8 / Boot 2.7; engines dispatch through `EngineSupport`.

## Goals / Non-Goals

**Goals:**

- One extra round-trip for Agents: table-detail = DDL + stats.
- Plan-only and analyzed explain are different HTTP resources with different kill switches and history sources.
- Both explain paths are facades over `SqlExecutionService` after engine rewrite.
- Generic `readOnly` executions can no longer sneak ANALYZE.

**Non-Goals:**

- MCP server, OpenAPI-as-tools, AgentScope `@Tool` implementations, Copilot UI for stats/explain.
- New authorization-service capabilities (no `SQL_EXPLAIN_ANALYZE` flag in Session).
- Metadata requests entering the execution concurrency quota.
- Batch `getTableInfo`, comment search, or showing stats in the workbench tree.
- Closing every dialect of “this SELECT is actually a write” (`SELECT INTO OUTFILE` remains a known classifier gap).

## Decisions

### 决策一：扩展 `table-detail`，不新开 `/table-info`

Agent 的 `getTableInfo` 对应现有 `GET .../table-detail`。新增字段对编辑器客户端向后兼容（多一个 `stats`）。新路径会让树、补全、Agent 三套契约分叉。统计失败返回 `stats: null`，与现有 `ddl: null` 相同，避免 `SHOW TABLE STATUS` 在锁或权限问题上把整个结构页打挂。

`stats` 固定中性字段：`engine`、`estimatedRows`、`dataBytes`、`indexBytes`、`autoIncrement`、`createTime`、`updateTime`、`comment`。InnoDB `Rows` 是估计值，字段名必须叫 `estimatedRows`。

### 决策二：两条 URL，不用 `analyze` 布尔

考虑过 `POST /api/v1/sql/explains` + `{ "analyze": true }`。放弃：模型会打开开关，监管等于没有。`:analyze` 与现有 `/{id}:cancel` 自定义方法一致。请求体与执行口类似但不含 `readOnly` / `source`；未知字段继续 400。

服务端强制 `source`：`AI_AGENT_EXPLAIN` / `AI_AGENT_EXPLAIN_ANALYZE`。客户端不能把监管口写成 `WEB_SQL_EDITOR`。通用执行口仍然只接受原来的两个 source，避免编辑器误标。

`AI_AGENT_EXPLAIN_ANALYZE` 长 24，现有 `source varchar(20)` 不够。Flyway 把列扩到 `varchar(32)`。

### 决策三：改写放在 `EngineSupport`，执行走现有服务

```
ExplainController
    → 扫描单语句（现有 engine.requireSingle）
    → classify：非 SELECT → 422 EXPLAIN_STATEMENT_NOT_SUPPORTED
    → engine.isAnalyzedExplain → 计划口 422 EXPLAIN_ANALYZE_NOT_ALLOWED
    → engine.rewriteExplain(sql, PLAN|ANALYZE)
    → SqlExecutionService.execute(readOnly=true, source=forced, statement=rewritten)
```

不复制连接池、配额、取消、超时。历史里存的是改写后的 `EXPLAIN ...`，审计能直接看出计划 vs 实测。

MYSQL：`EXPLAIN` / `EXPLAIN ANALYZE`（可选 `FORMAT=JSON` 若版本支持；不支持则默认格式，结果仍是 RESULT_SET）。POSTGRESQL：`EXPLAIN (FORMAT JSON)` 与 `EXPLAIN (ANALYZE, FORMAT JSON)`。GBase 8A 若继续委托 MYSQL catalog/engine，自动继承 MYSQL 改写。

调用方应提交原始 `SELECT`/`WITH`。已是 `EXPLAIN` 且无 ANALYZE 时，计划口规范化后再执行；带 ANALYZE 的一律拒绝（计划口）或剥掉后按 ANALYZE 改写（监管口）。

### 决策四：ANALYZE 缺省关闭，超时单独封顶

`sql-editor.explain-analyze.enabled` 默认 `false` → `403 EXPLAIN_ANALYZE_DISABLED`。这是运维开关，不是 Session capability，避免改授权服务。AgentScope 即使包了这把 Tool，未开开关也调不成。

`sql-editor.explain-analyze.timeout-seconds` 默认 15，请求 `timeoutSeconds` 夹在 `1..该上限`。不用通用 60 秒上限，避免实测计划跑成一次长查询。

计划口沿用现有执行超时配置（Agent 可传更短 `timeoutSeconds`）。

### 决策五：只读通用执行也拒 ANALYZE

`readOnly=true` 的 `POST /sql/executions` 在 classifier 之后增加引擎 `isAnalyzedExplain`。这堵住现有 `executeQuery` Tool 直接发 `EXPLAIN ANALYZE`。编辑器不传 `readOnly` 时用户仍可在工作台手跑 ANALYZE。

不把 `source=AI_AGENT` 当防线。

### 决策六：本仓不实现 Spring AI Tool

Tool 名、description、是否把 ANALYZE 注册给模型，属于 Agent 应用。本仓提供 HTTP、错误码和文档，并更新 TypeScript 契约以免前端类型漂移。工作台不调用 explain 口。

## Risks / Trade-offs

- [ANALYZE 仍会真实执行用户 SQL] → 独立 URL + 缺省关闭 + 更短超时 + 只接受 classifier SELECT + `setReadOnly(true)`。不能当成零成本。
- [首词分类 / ANALYZE 检测有方言缝] → 检测放进引擎；计划口对疑似 ANALYZE fail closed。已知 `SELECT INTO OUTFILE` 缺口不在本次关闭。
- [SHOW TABLE STATUS / pg_stat 估计不准] → 文档与字段名使用 `estimatedRows`；Agent 提示词由调用方自己写。
- [统计查询占用连接但不计执行配额] → 与现有 metadata 一致；大批量表循环仍是 Agent 侧要限制的事（对方已有「最多 5 张表」惯例）。
- [扩 `source` 列] → 只加长 varchar，默认值不变；旧行仍是 `WEB_SQL_EDITOR` / `AI_AGENT`。
- [MySQL 老版本没有 EXPLAIN ANALYZE] → 监管口在目标库报错时走现有 `422 SQL_EXECUTION_FAILED`，不在本服务模拟计划。

## Migration Plan

1. 先发本服务：`table-detail.stats`、两条 explain 口、Flyway 扩 `source`、只读拒 ANALYZE。`explain-analyze.enabled` 保持 false。
2. 编辑器：不改请求；多出来的 `stats` 可忽略。TypeScript 类型跟上即可。
3. Agent 应用：`getTableInfo` → table-detail；`explainSql` → `/sql/explains`。不要把 `/sql/executions` 当 explain。
4. 需要实测计划时，运维打开 `explain-analyze.enabled` 并确认超时，再在 AgentScope 里单独注册 Tool。

回滚：关 `explain-analyze.enabled`；计划口和 `stats` 字段保留无害。不要回滚 Flyway。

## Open Questions

无。ANALYZE 是否允许已在探索中定为「默认禁止、单独监管口」。JSON vs 默认 EXPLAIN 格式由各引擎实现选择，不阻塞契约。
