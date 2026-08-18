## Context

当前 `service/` 是 Java 8 + Spring Boot 2.7 的第一阶段服务，已经实现 Bearer Token 上下文、产品隔离的数据源 CRUD、AES-GCM 凭据、CIDR 策略和按数据源懒加载的 Hikari 连接池；`web/` 是 Vue 3 + TypeScript，已有登录、Session、数据源页面和 MSW 测试边界。第二阶段跨越元数据查询、长时间 JDBC 执行、取消、历史、流式下载和桌面编辑器状态，且本地缺少 `bddf-authorization-service`。

实现必须保持第一阶段数据源 API 和隔离语义不变，只支持 MySQL、单条 Statement、`autoCommit=true` 和单实例运行时取消。SQL 原文进入历史库但不得进入应用日志；Token、密码、结果集不得持久化到浏览器缓存。

## Goals / Non-Goals

**Goals:**

- 实现文档 v0.3 定义的元数据、执行、取消、CSV 导出和当前用户历史 API。
- 复用已保存数据源的产品隔离、解密和动态连接池，确保所有目标库操作先鉴权。
- 用独立执行线程池和运行注册表保持普通 HTTP/取消请求可响应。
- 交付可用的桌面 SQL 工作台，覆盖连接、资源树、Monaco、页签、结果、导出和历史恢复。
- 提供不依赖外部基础设施的临时授权假服务，使登录、SQL 服务上下文校验和退出可本地串联。
- 通过单元、组件、构建以及可用环境下的 MySQL 集成/E2E 验证冻结契约。

**Non-Goals:**

- 不实现审批、SQL Review、危险语句识别、脱敏、手动事务、多语句脚本、存储过程脚本或多数据库引擎。
- 不实现跨实例取消、执行节点路由或高可用协调。
- 不把临时 `auth-service` 当作生产授权实现，也不复制账号绑定/创建业务。
- 不保存工作表到后端，不持久化查询结果，不实现协作与分享。

## Decisions

### 1. 以现有动态池作为唯一目标数据库入口

在 `DataSourceService` 增加一个仅内部使用的、按 `id + currentProductId` 读取并解密连接配置的方法，metadata/execution/export 均通过它借用连接。这样复用第一阶段的 CIDR、固定 JDBC 安全参数、版本化池失效和密码保护。备选方案是在新模块重复组装 JDBC URL，但会制造隔离和安全规则分叉，因此不采用。

### 2. 元数据使用 JDBC `DatabaseMetaData`

数据库、表、字段、主键和索引优先通过 `DatabaseMetaData` 获取，并在 Java 层排序、过滤和游标分页。标识符不拼入任意 SQL，减少注入面；MySQL 特有注释/额外属性仅在标准元数据不足时通过固定 INFORMATION_SCHEMA 查询补充。元数据请求短暂借池、设置 catalog，并在 finally 中重置连接。

### 3. 执行由有界线程池、配额和运行注册表协作

控制器返回 `WebAsyncTask`/`DeferredResult`，JDBC 工作在大小不小于全局并发上限的专用 `ThreadPoolTaskExecutor`。服务在插入 `RUNNING` 历史后获取全局和单用户许可，并将 `executionId -> userId/dataSourceId/Statement/Future` 放入 `ExecutionRegistry`。取消接口直接调用 `Statement.cancel()` 和 `Future.cancel(true)`；所有完成路径统一释放许可、运行计数、Statement、连接并终结历史。

执行 ID 在历史表主键上全局唯一，先检查注册表并依赖数据库唯一约束解决竞态。后端 SQL 扫描器用有限状态机跳过字符串、反引号、行注释和块注释中的分号，只接受一个非空语句；不尝试完整解析 MySQL 语法。

### 4. 结果编码保持精度并限制资源

查询设置超时和 `rowLimit + 1` 最大行数，逐行估算结果字节数。`BIGINT`、`DECIMAL`/`NUMERIC` 返回字符串，二进制仅返回大小描述，其余值按文档映射；额外一行或字节上限触发 `truncated`。DML/DDL 根据 JDBC 首个结果和语句分类返回联合类型。连接归还前恢复 auto-commit/catalog/warnings。

### 5. 历史是执行事实源，导出只重放成功查询

Flyway 新增 `sql_execution_history`，MyBatis 查询始终强制当前 `user_id`，列表以 `started_at + id` 签名游标分页。每次执行先写 RUNNING、结束后写终态；失败仅保存脱敏数据库消息和错误码。导出请求只接收已有执行 ID，从当前用户的成功 RESULT_SET 历史读取 SQL 和连接快照并重新鉴权/执行，产生新的 EXPORT 历史。

CSV 通过 `StreamingResponseBody` 逐行输出 UTF-8 BOM、RFC 4180 CRLF/引号转义，并默认保护 `= + - @` 公式前缀。浏览器使用 `fetch` + Blob，不把 SQL 重新放进导出请求。

### 6. 前端以 Pinia 管理临时页签，Vue Query 管理服务端状态

编辑器 store 保存页签、每页签绑定的数据源/数据库、SQL、运行状态和当前结果；仅 SQL 页签草稿以容量受限的 `sessionStorage` 恢复，登出/401 清理。元数据、数据源和历史由 Vue Query 按文档 stale 时间缓存，切换数据源或 DDL 成功时精确失效。

Monaco 作为延迟加载组件，注册 MySQL 补全和键盘命令。SQL 选区/当前语句提取共用可测试扫描器；后端仍是最终单语句权威。结果表采用语义化表格、粘性表头和窗口化行渲染；在当前依赖规模下先用轻量组件实现，避免为验收外功能引入第二套状态模型。

### 7. 本地授权服务使用零依赖 Node HTTP 服务

`auth-service/server.js` 实现 `/ldap/login`、`/logout`、`/internal/api/v1/auth/context` 和 health。它只接受固定开发账号/任意非空密码，签发进程内 Token，校验内部服务密钥，并返回单一产品上下文；CORS 和端口由环境变量控制。选择零依赖实现是为了 `node server.js` 即可启动且不扩大 Maven/前端依赖；生产配置仍指向真实授权服务。

### 8. 本地完整链路使用显式配置而非隐式降级

提供环境样例和启动说明，将 Web 分别指向 auth 与 SQL service，SQL service 指向临时 auth 和本地 MySQL。不会在 SQL service 内置绕过认证的 profile，避免开发开关误入生产。MSW 保留给隔离前端测试，但真实串联路径不启用 Mock。

## Risks / Trade-offs

- [JDBC 取消是尽力而为，已提交 DML/DDL 无法撤销] → UI 明确提示，历史记录最终实际状态，并保持 `autoCommit=true` 契约。
- [HTTP 断开在 Servlet/驱动组合下不总能即时通知] → 异步超时与显式取消都进入同一清理路径，Statement 另有 JDBC timeout 上限。
- [SQL 扫描器不是完整解析器] → 只承担拒绝明显多语句，`allowMultiQueries=false` 作为驱动层防线，DELIMITER/过程脚本明确不支持。
- [100 MB 浏览器 Blob 仍占内存] → 服务端同时限制行数和字节数，UI 支持取消并提示重新执行语义。
- [历史原文可能包含敏感字面量] → 仅当前用户可读、数据库/备份需加密、日志只记录 hash；保留 90 天后清理。
- [Node 假授权服务不模拟真实 LDAP 多账号全部边界] → 明确标记 local-only，响应保持冻结字段，真实服务联调仍需单独验收。
- [Java 8 / Boot 2.7 已过 OSS 维护期] → 本变更遵循现有基线，不在第二阶段同时升级运行时。

## Migration Plan

1. 先部署 Flyway 历史表和兼容的后端配置；第一阶段 API 不变。
2. 部署单实例 SQL service，确认执行线程池、网关 75 秒超时、历史保留和目标连接上限。
3. 授权服务上线真实 context 接口；本地/测试环境使用临时 `auth-service`。
4. 部署前端并启用第二阶段 Session capabilities，将默认入口改为 `/sql-editor`。
5. 验证元数据、CRUD SQL、取消、导出、历史和跨产品/跨用户隔离后开放入口。
6. 回滚前端可恢复数据源默认入口；回滚后端时保留新增历史表，避免破坏性数据库回退。

## Open Questions

- 无阻塞问题；默认值采用开发说明书 v0.3：1,000 行、100,000 行硬上限、100 MB、60 秒、单用户 3、全局 50、历史 90 天、CSV 公式保护开启。
