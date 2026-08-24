## 1. 请求契约与配置

- [x] 1.1 在 `SqlExecutionRequest` 增加可选 `readOnly`、`timeoutSeconds`、`source`
- [x] 1.2 在 `SqlEditorProperties` 增加 `http.trustedProxyCidrs`（默认空列表），启动时按现有 CIDR 规则校验
- [x] 1.3 在 `application.yml` 与 `application-production.example.yml` 暴露 `sql-editor.http.trusted-proxy-cidrs`
- [x] 1.4 非法 `source`、越界 `timeoutSeconds` 映射为 `400 VALIDATION_FAILED` 字段错误，补单元测试

## 2. 只读执行

- [x] 2.1 在 `SqlExecutionService` 于 classify 之后、`registry.acquire` 之前：`readOnly=true` 且类型不是 `SELECT` 时抛 `422 READ_ONLY_VIOLATION`
- [x] 2.2 `readOnly=true` 且放行后，对借出的连接调用 `setReadOnly(true)`，再保持现有 `setAutoCommit(true)`
- [x] 2.3 JDBC 在只读模式下返回 update count 而非 ResultSet 时，按 `READ_ONLY_VIOLATION` 失败并 `history.failure`
- [x] 2.4 缺省或 `readOnly=false` 不调用 `setReadOnly`，DML/DDL 行为与现在一致
- [x] 2.5 在错误码表 / `docs/backend-development-spec.md` 增加 `422 READ_ONLY_VIOLATION`

## 3. 按请求超时

- [x] 3.1 计算有效超时 `T`：请求值或配置默认值，范围 `1..config.timeoutSeconds`
- [x] 3.2 `SqlExecutionController` 按本次 `T` 构造 `WebAsyncTask` 超时 `(T + 5) * 1000`，不要再用构造期写死的常量
- [x] 3.3 JDBC `setQueryTimeout(T)` 使用同一有效秒数
- [x] 3.4 导出路径保持原配置超时，不做按请求覆盖

## 4. 历史 source 与 client_ip

- [x] 4.1 Flyway 迁移：`sql_execution_history` 增加 `source varchar(20) NOT NULL DEFAULT 'WEB_SQL_EDITOR'`
- [x] 4.2 `ExecutionHistoryRecord`、mapper XML、`start()` 写入 `source`；缺省与导出均为 `WEB_SQL_EDITOR`
- [x] 4.3 `HistorySummary` / `HistoryDetail` 返回 `source`
- [x] 4.4 实现 client IP 解析：对端不在 `trusted-proxy-cidrs` 时用 `getRemoteAddr()` 并忽略转发头；在列表内时取 `X-Forwarded-For` 最左或仅有的 `X-Real-IP`；截断 64 字符
- [x] 4.5 `execute()` 把解析到的 IP 传入 `history.start()`；列表/详情 JSON 不暴露 `clientIp`

## 5. 测试

- [x] 5.1 只读：`INSERT`/`UPDATE`/`DELETE`/`DDL`/`CALL` 返回 `READ_ONLY_VIOLATION` 且无历史行、不占并发
- [x] 5.2 只读：`SELECT`/`WITH`/`SHOW` 成功；编辑器不传 `readOnly` 时 `INSERT` 仍成功
- [x] 5.3 `timeoutSeconds` 越界 400；合法更短值作用于 JDBC（可用测试替身或集成超时断言）
- [x] 5.4 `source=AI_AGENT` 写入并出现在历史列表/详情；省略时为 `WEB_SQL_EDITOR`；非法值 400
- [x] 5.5 client IP：无可信代理时忽略伪造的 `X-Forwarded-For`；对端在 CIDR 内时记录转发地址
- [x] 5.6 现有不带新字段的执行/元数据集成测试仍然通过（Jackson 未知字段仍拒绝）

## 6. 文档与契约

- [x] 6.1 更新 `docs/backend-development-spec.md`：执行请求字段、只读规则、按请求超时、历史 `source`、`client_ip` 可信代理规则、错误码
- [x] 6.2 若前端有执行/历史 TypeScript 契约，补可选字段；编辑器请求体可不改
- [x] 6.3 运行 `openspec validate add-agent-readonly-execution --strict` 确认本 change 通过
