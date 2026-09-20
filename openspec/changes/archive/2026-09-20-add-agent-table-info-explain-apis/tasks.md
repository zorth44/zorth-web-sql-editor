# Tasks

## 1. 表统计契约与引擎采集

- [x] 1.1 增加引擎中性 `TableStats`（`engine`、`estimatedRows`、`dataBytes`、`indexBytes`、`autoIncrement`、`createTime`、`updateTime`、`comment`）并挂到 `TableDetailResponse`，用序列化测试确认缺省为 `null`、有值时不含 `Rows`/`Data_length` 字段名
- [x] 1.2 在 `EngineSupport` 增加表统计采集（或由 `tableDetail` 内部填充 `stats`），编排层只调引擎，单元测试断言 metadata 服务不拼接 `SHOW TABLE STATUS`
- [x] 1.3 MySQL（及委托它的 GBase）用引用标识符执行 `SHOW TABLE STATUS` 并映射 `stats`；表不存在仍 `404 TABLE_NOT_FOUND`；统计失败时 `stats=null` 且列/`ddl` 仍返回，用 catalog 单测或集成测试覆盖
- [x] 1.4 PostgreSQL 从 `pg_class` / `pg_stat` 填 `estimatedRows` 与字节大小，禁止 `SHOW TABLE STATUS`；视图或统计缺失时结构成功且 `stats` 为 `null` 或空字段，用 Postgres catalog 测试覆盖

## 2. Explain 改写 SPI

- [x] 2.1 在 `EngineSupport` 增加 `isAnalyzedExplain` 与 `rewriteExplain(sql, PLAN|ANALYZE)`，编排/Controller 不按引擎 id 拼 `EXPLAIN` 字符串，用接口测试或伪引擎验证分发
- [x] 2.2 MYSQL 计划改写不含 `ANALYZE`；识别 `EXPLAIN ANALYZE`；ANALYZE 改写生成 `EXPLAIN ANALYZE`（可选 JSON）；用引擎单测覆盖 `SELECT 1` 与已带 `EXPLAIN` 的输入
- [x] 2.3 POSTGRESQL 计划改写为 `EXPLAIN (FORMAT JSON)`，ANALYZE 为 `EXPLAIN (ANALYZE, FORMAT JSON)`；识别 `EXPLAIN ANALYZE` 与 `EXPLAIN (ANALYZE ...)`；美元引号语句仍走现有扫描器，用引擎单测覆盖

## 3. 计划口与监管 ANALYZE 口

- [x] 3.1 增加 `POST /api/v1/sql/explains`：单语句、改写后 `readOnly` 走现有 `SqlExecutionService`、强制 `source=AI_AGENT_EXPLAIN`、请求体不含 `source`/`readOnly`/`analyze`；未知字段 400；用 MVC 测试覆盖 `SELECT` 成功且历史为 `AI_AGENT_EXPLAIN`
- [x] 3.2 计划口对 ANALYZE 返回 `422 EXPLAIN_ANALYZE_NOT_ALLOWED`，对 DML/DDL/`OTHER` 返回 `422 EXPLAIN_STATEMENT_NOT_SUPPORTED`，均不占并发、不插历史、不借连接，用服务测试断言
- [x] 3.3 增加配置 `sql-editor.explain-analyze.enabled`（默认 false）与 `timeout-seconds`（默认 15），写入 `application.yml` 与 production example，启动配置绑定测试确认缺省值
- [x] 3.4 增加 `POST /api/v1/sql/explains:analyze`：关闭时 `403 EXPLAIN_ANALYZE_DISABLED` 且无历史；开启时改写 ANALYZE、强制 `source=AI_AGENT_EXPLAIN_ANALYZE`、超时夹在 `1..analyze.timeout-seconds`；DML 仍 `EXPLAIN_STATEMENT_NOT_SUPPORTED`；用 MVC/服务测试覆盖开关两种状态
- [x] 3.5 计划口在 ANALYZE 已启用时仍拒绝 `EXPLAIN ANALYZE`，用测试锁定两条 URL 不共用 `analyze` 开关

## 4. 只读执行拒 ANALYZE 与历史 source

- [x] 4.1 `readOnly=true` 的 `POST /api/v1/sql/executions` 在执行前拒绝 ANALYZE，返回 `422 EXPLAIN_ANALYZE_NOT_ALLOWED` 且不插历史；省略 `readOnly` 时编辑器 `EXPLAIN ANALYZE` 仍可进入执行路径（可用分类/短路测试，不必真跑 ANALYZE）
- [x] 4.2 Flyway `V6` 将 `sql_execution_history.source` 扩到 `varchar(32)`；`ExecutionSource` 允许 `AI_AGENT_EXPLAIN` 与 `AI_AGENT_EXPLAIN_ANALYZE`；通用执行口仍只接受 `WEB_SQL_EDITOR`|`AI_AGENT`，非法值 400；用迁移与校验测试覆盖
- [x] 4.3 历史列表/详情返回新 `source` 值；explain 口忽略客户端 `source`（字段根本不在请求体）；导出历史仍为 `WEB_SQL_EDITOR`

## 5. 文档、前端契约与校验

- [x] 5.1 更新 `docs/backend-development-spec.md`：`table-detail.stats`、两条 explain API、错误码 `EXPLAIN_ANALYZE_NOT_ALLOWED` / `EXPLAIN_STATEMENT_NOT_SUPPORTED` / `EXPLAIN_ANALYZE_DISABLED`、只读拒 ANALYZE、新 `source` 值
- [x] 5.2 更新 `web/src/types/contracts.ts`（及现有元数据/历史测试）：`TableDetail.stats`、`ExecutionSource` 新字面量；工作台不调用 explain 口
- [x] 5.3 现有元数据、只读执行、历史集成测试仍然通过；补 table-detail 含 `stats` 的集成断言（Testcontainers MySQL，Postgres 有则同样）
- [x] 5.4 运行 `openspec validate add-agent-table-info-explain-apis --strict` 确认本 change 通过
