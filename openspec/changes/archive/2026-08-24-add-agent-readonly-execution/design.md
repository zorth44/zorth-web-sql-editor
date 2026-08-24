## Context

AI Platform 的 Database Agent 将把 `listTables` / `getTableSchema` / `executeQuery` 改调本服务，认证走用户 Bearer 透传。本服务现有执行链是给 Web SQL 编辑器准备的：扫描器只禁止多语句，`SqlStatementClassifier` 只分类不拒绝，`autoCommit=true`，超时写死在配置里（默认 60 秒），历史有 `client_ip` 列但 `ExecutionHistoryService.start()` 从不赋值。

对方 Phase 1 可以先靠自己的 JSqlParser Guard 和只读账号白名单接入现有接口。Phase 2 要摘白名单，必须由本服务在执行入口强制只读，并让 Agent 能传更短超时、把来源写入历史。本服务 `fail-on-unknown-properties: true`，对方不能先发新字段。

约束：不新开 Agent 专用执行 API；编辑器缺省行为不能变；`source` 只做审计归因，不能当安全边界。

## Goals / Non-Goals

**Goals:**

- 同一条 `POST /api/v1/sql/executions` 可被编辑器（可写）和 Agent（只读）共用。
- `readOnly=true` 时，在占用并发配额、写历史、打开目标连接之前拒绝写语句，错误码稳定。
- 调用方可传 `timeoutSeconds`，JDBC `queryTimeout` 与本次 `WebAsyncTask` 超时用同一个有效秒数。
- 历史能区分 `WEB_SQL_EDITOR` 与 `AI_AGENT`，并真正写入 `client_ip`。
- 不传新字段的现有编辑器客户端继续可用。

**Non-Goals:**

- 不为 Agent 复制一套执行/取消/导出 API。
- 不改 Token 鉴权（用户 Bearer 透传已经成立）。
- 不改单用户并发 3 / 全局 50，也不把元数据计入执行配额。
- 不在数据源上增加只读账号字段（可另开 change）。
- 不做批量表结构、`table-detail` 表备注、按备注搜表。
- 不引入 JSqlParser；不把 `source` 当鉴权。
- 不在历史公开 API 返回 `client_ip`（列给故障定位，本次只落库）。
- 不提供测试环境本身（联调地址和测试数据源是运维事项）。

## Decisions

### 决策一：在现有执行接口加可选字段，不新开路径

`SqlExecutionRequest` 增加三个可选字段：

| 字段 | 缺省 | 非法值 |
| --- | --- | --- |
| `readOnly` | `false` | 非布尔由 Jackson 400 |
| `timeoutSeconds` | 配置 `sql-editor.execution.timeout-seconds`（默认 60） | 小于 1 或大于该配置上限 → `400 VALIDATION_FAILED` / `OUT_OF_RANGE` |
| `source` | `WEB_SQL_EDITOR` | 非 `WEB_SQL_EDITOR` \| `AI_AGENT` → `400 VALIDATION_FAILED` |

考虑过 `POST /api/v1/sql/queries` 或 `/api/v1/ai/executions`。放弃：权限、连接池、取消、超时、历史、导出重放都要再接一遍，编辑器和 Agent 会分叉。可选字段让编辑器零改动。

### 决策二：只读用现有 classifier 的 `SELECT` 类型，执行前拒绝

`SqlStatementClassifier` 已把 `SELECT` / `WITH` / `SHOW` / `EXPLAIN` / `DESC` / `DESCRIBE` 归为 `StatementType.SELECT`。`readOnly=true` 且类型不是 `SELECT` 时返回 `422 READ_ONLY_VIOLATION`，不 `acquire`、不插历史、不借目标连接。多语句仍由引擎扫描器先返回 `400 MULTI_STATEMENT_NOT_SUPPORTED`。

拒绝发生在校验阶段，与 `MULTI_STATEMENT_NOT_SUPPORTED` 一致，避免探测写语句也占并发额度。Agent 自己还有审计；本服务历史只记录真正开跑的执行。

考虑过引入 JSqlParser。放弃：Java 8 基线要新依赖，且和编辑器现有「首词分类」重复。已知缺口：`SELECT ... INTO OUTFILE`、`SELECT ... FOR UPDATE` 仍会被放行。靠 `readOnly=true` 时 `Connection.setReadOnly(true)` 做提示性加固，真正的库级只读账号不在本次范围。

`readOnly=true` 且语句已放行后：借连接、`setReadOnly(true)`、再 `setAutoCommit(true)` 并执行。若 JDBC 仍返回 update count 而不是 ResultSet，视为只读失败（`422 READ_ONLY_VIOLATION`），历史记 `FAILED`——这是执行已开始后的兜底。

编辑器不传或传 `false`：不调用 `setReadOnly`，DML/DDL 与现在相同。

### 决策三：按请求超时同时改 JDBC 和 WebAsyncTask

今天超时是 Controller 构造时算死的 `(timeoutSeconds + 5) * 1000`，JDBC 用同一配置。只改 `Statement.setQueryTimeout` 会让 HTTP 层仍按 65 秒砍请求。

有效秒数 `T = request.timeoutSeconds ?? config`，校验 `1 <= T <= config.timeoutSeconds`。上限就是今天的配置项，避免 Agent 或编辑器把超时抬到 3600。JDBC `setQueryTimeout(T)`；该次 `WebAsyncTask` 超时 `(T + 5) 秒`。导出路径不改。

### 决策四：`source` 只写入历史并在列表/详情返回，不做过滤、不当鉴权

Flyway 给 `sql_execution_history` 加 `source varchar(20) NOT NULL DEFAULT 'WEB_SQL_EDITOR'`。`HistorySummary` / `HistoryDetail` 增加 `source`。本次不加 `?source=` 过滤，以免改游标 scope。

客户端可以谎称来源，所以只读仍以 `readOnly` 为准，不以 `source=AI_AGENT` 推断只读。导出历史固定 `WEB_SQL_EDITOR`（导出请求不接 `source`）。

### 决策五：`client_ip` 默认记直连地址，仅可信代理才读转发头

当前没有任何 Forwarded 过滤器。默认 `request.getRemoteAddr()`。仅当对端落在新配置 `sql-editor.http.trusted-proxy-cidrs` 内时，才取网关设置的 `X-Forwarded-For` 最左（或 `X-Real-IP`，若网关只设这个）非空地址。列表为空则永不信任转发头。写入前截断到 64 字符。历史 API 不返回该字段。

Agent 直连时记到的是 AI 服务地址，这是预期：那是打到本服务的对端。

## Risks / Trade-offs

- [首词分类拦不住所有写] → 文档写明缺口；`setReadOnly(true)` 作提示；只读库账号作为后续 change。Phase 2 摘 AI 白名单前应确认账号权限。
- [只读拒绝不写历史] → 与现有校验错误一致；换的是不占并发。需要「谁试过写」看 AI 侧审计。
- [客户端可伪造 `source`] → 接受；只读不靠它。
- [可信代理配错会信伪造的 XFF] → 缺省空列表，fail closed。
- [按请求超时不能高于配置] → Agent 要的是更短，不是更长；加长需改部署配置。

## Migration Plan

1. 先发本服务（含 Flyway `source` 列和新可选字段）。旧编辑器不发这些字段，行为不变。
2. 再生产把网关 CIDR 写入 `trusted-proxy-cidrs`。
3. 再让 AI 服务开始发 `readOnly=true`、`source=AI_AGENT`、较短 `timeoutSeconds`。

回滚：停用新字段即可；`source` 列有默认值，旧代码不读也无妨。不要回滚 Flyway。

## Open Questions

无。测试环境地址、固定测试 `dataSourceId`、Token 透传政策确认不在本 change 实现范围内，联调前由负责人提供。
