# 部署与安全配置

所有敏感值必须由 Secret 管理系统注入，不写入镜像、仓库、命令行历史或 Actuator。元数据 MySQL 账号只需要目标 schema 的连接、建表/迁移、查询、插入、更新和删除权限；不应拥有全局管理权限。

## 环境变量

| 变量 | 是否必需 | 用途与安全默认值 |
|---|---:|---|
| `SQL_EDITOR_METADATA_URL` | 是 | 元数据 MySQL JDBC URL；建议固定 TLS、时区和受控主机。 |
| `SQL_EDITOR_METADATA_USERNAME` | 是 | 最小权限元数据账号。 |
| `SQL_EDITOR_METADATA_PASSWORD` | 是 | 元数据密码，Secret 注入。 |
| `SQL_EDITOR_METADATA_POOL_SIZE` | 否 | 元数据池上限，默认 10。 |
| `SQL_EDITOR_AUTH_CONTEXT_URL` | 是 | 外部鉴权上下文端点；该端点实现不属于本次交付。 |
| `SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY` | 是 | 服务间密钥，仅发送到鉴权端点。 |
| `SQL_EDITOR_AUTH_CONNECT_TIMEOUT_MS` | 否 | 鉴权连接超时，默认 2000。 |
| `SQL_EDITOR_AUTH_READ_TIMEOUT_MS` | 否 | 鉴权读取超时，默认 3000。 |
| `SQL_EDITOR_AUTH_CACHE_TTL_SECONDS` | 否 | Token 摘要缓存 TTL，默认/最大 60；设 0 禁用复用。 |
| `SQL_EDITOR_AUTH_CACHE_MAXIMUM_SIZE` | 否 | 摘要缓存条目上限，默认 10000。 |
| `SQL_EDITOR_CREDENTIAL_CURRENT_VERSION` | 是 | 当前数据源密码密钥版本，如 `v2`。 |
| `SQL_EDITOR_CREDENTIAL_CURRENT_KEY` | 是 | 当前 AES-256 密钥的 Base64；多版本键建议用外部 YAML/配置树注入 `sql-editor.credentials.keys.*`。 |
| `SQL_EDITOR_CURSOR_SIGNING_KEY` | 是 | 游标 HMAC 密钥，使用独立高熵值。 |
| `SQL_EDITOR_CURSOR_MAXIMUM_TOKEN_LENGTH` | 否 | 游标最大长度，默认 2048。 |
| `SQL_EDITOR_NETWORK_ALLOWED_CIDRS` | 是 | 允许的数据源目标 CIDR，逗号分隔；空列表会拒绝启动。 |
| `SQL_EDITOR_NETWORK_DENIED_CIDRS` | 否 | 拒绝 CIDR，优先于允许规则；默认拒绝本机、链路本地地址。 |
| `SQL_EDITOR_TARGET_MAX_POOLS` | 否 | 动态池总数，默认 10。 |
| `SQL_EDITOR_TARGET_MAX_CONNECTIONS` | 否 | 动态连接全局预算，默认 50。 |
| `SQL_EDITOR_TARGET_POOL_SIZE` | 否 | 单池上限，默认/最大 5，最小空闲为 0。 |
| `SQL_EDITOR_TARGET_IDLE_POOL_MINUTES` | 否 | 空闲池退休时间，默认 30 分钟。 |
| `SQL_EDITOR_DEFAULT_ROW_LIMIT` / `SQL_EDITOR_MAX_ROW_LIMIT` | 否 | 查询默认/硬上限，默认 1000 / 100000。 |
| `SQL_EDITOR_MAX_RESULT_BYTES` | 否 | 查询和导出结果字节上限，默认 104857600。 |
| `SQL_EDITOR_EXECUTION_TIMEOUT_SECONDS` | 否 | 查询、DML、DDL 和导出超时，默认 60 秒。 |
| `SQL_EDITOR_MAX_CONCURRENT_PER_USER` | 否 | 单用户并行执行上限，默认 3。 |
| `SQL_EDITOR_MAX_CONCURRENT_GLOBAL` | 否 | 实例全局执行上限，默认 50。 |
| `SQL_EDITOR_EXECUTOR_POOL_SIZE` | 否 | JDBC 异步线程数，必须不小于全局执行上限，默认 50。 |
| `SQL_EDITOR_MAX_STATEMENT_BYTES` | 否 | 单条 SQL UTF-8 字节上限，默认 1048576。 |
| `SQL_EDITOR_CSV_FORMULA_PROTECTION` | 否 | CSV 公式前缀保护，默认 true。 |
| `SQL_EDITOR_CSV_NULL_LITERAL` | 否 | CSV 是否将 NULL 输出为字面量，默认 false（空字段）。 |
| `SQL_EDITOR_HISTORY_RETENTION_DAYS` | 否 | 历史保留天数，默认 90；0 关闭清理。 |
| `SQL_EDITOR_STALE_RUNNING_MINUTES` | 否 | 启动时修正陈旧 RUNNING 的阈值，默认 5 分钟。 |
| `SQL_EDITOR_MANAGEMENT_PORT` | 否 | 独立管理端口，默认 9081；仅绑定监控网络。 |

密钥轮换任务另使用：`sql-editor.credentials.rotation.enabled`、`dry-run`、`from-version`、`batch-size`。推荐通过受控一次性配置文件或配置树提供，不通过公共容器参数输出。

## 网络与 TLS

服务会解析 DNS 的全部 A/AAAA 结果，任一地址被拒绝则整个请求失败；连接使用已批准的 IP，避免二次解析和 DNS 重绑定。生产允许列表应只包含数据库专用网段，拒绝列表应覆盖环回、链路本地、云元数据和管理网段。

第一阶段 MySQL SSL 模式映射为 `DISABLED`、`PREFERRED`、`REQUIRED`。`REQUIRED` 加密传输但本阶段未提供浏览器侧 CA/客户端证书上传与主机名验证配置；严格 PKI 要求应由受控 JDBC/运行环境证书配置在后续阶段实现。

连接 GBase 8a 目标库须将南大通用官方 `gbase-connector-java.jar` 放到 `service/third-party/gbase/gbase-connector-java.jar` 后重新打包；该包不在 Maven Central，不能用 MySQL Connector/J 代替。未放入时服务仍可启动，GBASE_8A 连接会失败。

## 运行面

业务端口不得直接暴露到公网，应经网关完成来源限制和 TLS。管理端口只暴露 `health`、`prometheus`；`env`、`configprops`、`heapdump` 已禁用，健康详情关闭。liveness 不访问外部依赖；readiness 检查元数据 MySQL和有界鉴权端点，不遍历用户数据源。

第二阶段执行取消依赖实例内 `ExecutionRegistry`，当前必须单实例部署。网关读取超时应略高于应用执行超时（默认建议 75 秒），同时保留取消和健康检查的普通 HTTP 线程容量。需要多实例时必须先实现 executionId 到执行节点的路由，不能直接水平扩容。

## 第二阶段 API

- 元数据：`GET /api/v1/data-sources/{id}/databases|tables|table-detail`
- 执行与取消：`POST /api/v1/sql/executions`、`POST /api/v1/sql/executions/{id}:cancel`
- 导出：`POST /api/v1/sql/exports`（仅重放当前用户成功查询）
- 历史：`GET /api/v1/sql/history`、`GET /api/v1/sql/history/{id}`

所有接口继续通过 Bearer Token 派生用户/产品；元数据和执行按产品校验数据源，历史按当前用户隔离。SQL 原文只进入受控元数据库历史，不写应用日志。
