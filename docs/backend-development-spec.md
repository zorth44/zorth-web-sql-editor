# Web SQL 编辑器后端与 API 开发说明书

> 状态：评审稿 v0.3
>
> 仓库：`zorth-web-sql-editor`
>
> 服务建议名：`zorth-web-sql-service`
>
> 适用范围：仅支持 MySQL。第一阶段交付数据源管理，第二阶段交付 SQL 执行。

## 1. 服务职责

新建独立 Spring Boot 服务。

第一阶段负责：

- 使用现有授权服务校验 Token，并取得当前用户及唯一所属产品。
- 保存数据源配置，并把每条数据源归属到当前用户的唯一产品。
- 加密保存数据库密码，管理目标 MySQL 的动态连接池。
- 测试连接。

第二阶段追加：

- 查询数据库、表、字段、主键和索引元数据。
- 在独立执行线程池中执行查询、DML 和 DDL，并支持尽力取消。
- 保存用户自己的 SQL 执行历史。
- 重新执行查询并以 CSV 流式导出。

不负责：

- 用户、角色和产品主数据维护。
- SQL 审批、危险操作识别、风险审计或数据脱敏。
- 数据库权限的二次模拟。最终可执行能力由数据源配置的 MySQL 账号决定。
- 跨请求事务、手动提交、回滚或数据库变更版本管理。

**数据源列表、详情和所有后续操作都由后端按当前用户所属产品过滤。** 每条数据源只属于一个产品，不提供共享或跨产品绑定。不同产品即使连接同一数据库实例，也必须分别创建具有不同 ID 的数据源记录。

## 2. 推荐技术栈

| 类别 | 推荐 |
| --- | --- |
| Java | Java 8（跟随授权服务基线；Boot 2.7 OSS 已结束维护，安全补丁跟授权服务同一来源） |
| 框架 | Spring Boot 2.7.18 |
| Web | Spring MVC；SQL 执行与导出走 Servlet 异步，不长时间占用 Tomcat 工作线程 |
| 安全 | Spring Security，全局默认 `authenticated()` |
| 内部元数据库 | MySQL |
| 数据访问 | MyBatis |
| 数据库迁移 | Flyway |
| 目标库访问 | MySQL Connector/J + 原生 JDBC |
| 连接池 | HikariCP，按数据源动态创建 |
| 缓存 | Caffeine；可选 Redis 用于多实例扩展 |
| API 文档 | springdoc-openapi（兼容 Boot 2.7），生成 OpenAPI 3 |
| 测试 | JUnit 5、Testcontainers MySQL、MockWebServer/WireMock |
| 指标 | Actuator + Micrometer |

与现有授权服务保持同一运行基线：使用 `javax.*`（非 `jakarta.*`），API 和数据模型按本文约定实现。新服务不要依赖授权服务内部 Java 包、Redis Key 格式或数据库表。

## 3. 总体架构

```text
Browser
  │ Authorization: Bearer token
  ▼
zorth-web-sql-service
  ├─ AuthContextFilter ───────► bddf-authorization-service
  ├─ DataSourceService ───────► SQL Editor Metadata MySQL
  ├─ DynamicPoolManager ──────► Target MySQL A / B / C
  ├─ MetadataService ─────────► Target INFORMATION_SCHEMA
  ├─ SqlExecutionService ─────► 执行线程池 / Target JDBC
  ├─ ExecutionRegistry ───────► Running Statement in memory
  └─ CsvExportService ────────► Streaming HTTP response（Servlet 异步）
```

浏览器不直接连接目标数据库，也不接收数据库密码。

## 4. 权限模型

### 4.1 基本规则

- 一个用户只属于一个产品。
- 每条数据源只属于一个产品，`product_id` 在创建时由服务端从 Token 上下文写入；客户端不得提交或修改该字段。
- **可见性由后端过滤：** `GET /api/v1/data-sources` 只返回 `product_id = currentProductId` 的数据源。详情、测试、修改、删除、元数据和 SQL 执行都使用相同条件。前端不承担产品权限过滤。
- 用户只能查看和操作自己产品的数据源。产品不匹配的 ID 与不存在的 ID 一律返回 `404 DATA_SOURCE_NOT_FOUND`，不使用 403，避免泄露其他产品是否配置了该数据源。
- 所有用户在可见数据源上的权限一致：查看、测试、编辑、删除、执行 SQL 和导出。
- 每次接口调用都必须从 Token 取得用户和产品，禁止接受客户端传入的 `userId`、`productId` 或权限字段作为授权依据。
- 元数据库时间全部按 UTC 存储，API 日期时间一律输出 ISO-8601（`...Z`）。Flyway 表使用 `utf8mb4`。

访问判定：

```sql
select 1
from sql_data_source ds
where ds.id = :dataSourceId
  and ds.product_id = :currentProductId
```

### 4.2 数据源身份与隔离

- 数据源唯一身份只由 `id` 决定；`name` 允许在同一产品内或跨产品重复。
- Host、端口、默认数据库、用户名完全相同的两条记录仍是两个独立数据源，只要 ID 不同。
- 删除数据源只删除当前产品下匹配该 ID 的记录，不影响其他产品连接同一物理数据库的独立记录。
- Session 中的产品用于顶栏展示和服务端过滤，不是可切换条件。

## 5. 授权服务接口契约

现有授权服务仍负责浏览器登录和退出；SQL 服务只消费其 Token。以下浏览器契约已按当前 `bddf-authorization-service` 与现网 `bddf-ui` 源码核对。

### 5.1 浏览器登录与退出

当前登录入口为 LDAP 接口，不使用旧的 `POST /login`：

```http
POST /ldap/login
Content-Type: application/json
```

```json
{
  "username": "ehr-user",
  "password": "<base64-password><12-char-random-suffix>",
  "productType": "chinaBank",
  "selectUserId": null
}
```

- `password` 按 UTF-8 Base64 编码后追加 12 位随机字符串，以兼容授权服务现有 `PasswordValidatorUtils#getBase64Pwd`；这不是加密，部署必须使用 HTTPS。
- `productType` 可选值为 `synthetical`、`chinaBank`、`oversea`，缺省为 `chinaBank`。
- 一个 LDAP 身份绑定多个本地账号时，首次响应包含 `needSelectAccount: true` 和 `bindAccounts`；用户选择后用相同凭据及 `selectUserId` 再次调用。
- 未绑定本地账号时响应 `needBind: true`。SQL 编辑器第一阶段不复制账号绑定/创建流程，引导用户到原系统完成绑定。
- 成功登录响应是授权服务 `AjaxResult`：业务码在响应体 `code` 中，`code = 200` 且顶层存在 `token` 才表示取得 Token。前端不得只依赖 HTTP 200。
- 授权服务可能返回额外遗留字段；SQL 编辑器不得保存、记录或转发这些字段，尤其不得处理或持久化 `ldapUser`。当前 `LdapUserVo` 的 `pwd` 有公开 getter，登录服务部分分支还会把解码后的密码写入后返回；授权服务必须在生产联调前禁止序列化 `pwd` 并改用最小响应 DTO。前端忽略该字段不能替代服务端修复。

退出接口：

```http
POST /logout
Authorization: Bearer <existing-token>
```

当前成功响应为 `{ "code": 200, "msg": "退出成功" }`。前端无论退出调用是否成功都清理本地 Token。

### 5.2 获取当前登录上下文

```http
GET /internal/api/v1/auth/context
Authorization: Bearer <existing-token>
X-Internal-Service-Key: <service-secret>
```

成功响应：

```json
{
  "userId": "1001",
  "username": "zhangsan",
  "displayName": "张三",
  "product": {
    "id": "12",
    "name": "产品 A"
  },
  "tokenExpiresAt": "2026-08-12T08:30:00Z"
}
```

要求：

- 这是授权服务需要为 SQL 服务新增的内部接口。授权服务从请求 Token 对应的 Redis `LoginUser` 取 `userId`、`userName`、`nickName` 和 `expireTime`，再根据用户唯一产品关系取得产品 ID 与名称；不能接受 `userId` 或 `productId` 请求参数。
- Token 不存在、过期或已退出时返回 HTTP 401、错误码 `UNAUTHENTICATED`。
- 用户没有产品或存在多个有效产品时返回 HTTP 409、错误码 `USER_PRODUCT_CONTEXT_INVALID`，`details.productCount` 返回查询到的有效产品数量。
- 不返回密码、完整菜单、手机号等无关字段。
- SQL 服务和授权服务之间优先使用内网 + mTLS；暂不具备时使用独立的服务密钥。

### 5.3 Token 校验缓存

SQL 服务可按 Token 的 SHA-256 摘要缓存登录上下文，默认 TTL 60 秒：

- 缓存只降低授权服务压力，不延长 Token 有效期。
- 授权服务不可用且缓存不存在时返回 `503 AUTH_SERVICE_UNAVAILABLE`，不得降级为匿名或沿用过期身份。
- 已退出 Token 最多可能在 60 秒缓存窗口内继续有效；若该风险不可接受，将 TTL 降至 0 或由授权服务增加 Token Introspection/推送失效机制。
- 不在日志中记录原始 Token。

## 6. SQL 服务 Session API

### 6.1 当前 Session

```http
GET /api/v1/session
```

响应：

```json
{
  "user": {
    "id": "1001",
    "username": "zhangsan",
    "displayName": "张三"
  },
  "product": {
    "id": "12",
    "name": "产品 A"
  },
  "expiresAt": "2026-08-12T08:30:00Z",
  "capabilities": ["DATA_SOURCE_MANAGE"]
}
```

`capabilities` 只返回当前部署已经交付的能力：第一阶段仅 `DATA_SOURCE_MANAGE`，第二阶段上线后再追加 `SQL_EXECUTE`、`SQL_EXPORT`、`HISTORY_READ`。`expiresAt` 来自授权服务的 `tokenExpiresAt`，前端用它判断会话是否还有效；SQL 服务 60 秒 Token 缓存不延长这个时间。

不提供产品列表代理接口。推荐网关把前端、SQL 服务和授权服务登录做成同源；若登录仍直连授权服务，前端使用独立授权 baseURL。

## 7. 数据模型

### 7.1 `sql_data_source`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | varchar(36) PK | UUID |
| `product_id` | varchar(64) | 所属产品 ID，由 Token 上下文写入 |
| `name` | varchar(100) | 数据源名称，允许重复 |
| `engine` | varchar(20) | 固定 `MYSQL` |
| `host` | varchar(255) | 主机，不含协议 |
| `port` | int | 默认 3306 |
| `username` | varchar(128) | 数据库用户名 |
| `password_ciphertext` | text | AES-GCM 密文 |
| `password_iv` | varchar(64) | 随机 IV |
| `key_version` | varchar(32) | 密钥版本 |
| `default_database` | varchar(64) null | 默认数据库 |
| `ssl_mode` | varchar(20) | SSL 模式 |
| `connect_timeout_seconds` | int | 1–30 |
| `properties_json` | json | 白名单 JDBC 参数 |
| `description` | varchar(500) null | 描述 |
| `last_test_status` | varchar(20) null | `SUCCESS/FAILED` |
| `last_test_at` | datetime(3) null | UTC |
| `last_test_message` | varchar(500) null | 脱敏消息 |
| `version` | bigint | 乐观锁版本 |
| `created_by` | varchar(64) | 用户 ID |
| `created_by_name` | varchar(100) | 用户名快照，列表展示用 |
| `created_at` | datetime(3) | UTC |
| `updated_by` | varchar(64) | 用户 ID |
| `updated_by_name` | varchar(100) | 用户名快照，列表展示用 |
| `updated_at` | datetime(3) | UTC |

索引：

- `(product_id, updated_at desc, id desc)`：列表过滤和游标分页。
- `(product_id, name)`：名称查询辅助索引，非唯一。

`version` 只跟踪连接配置和描述的修改。测试已保存数据源只更新 `last_test_*` 字段，不递增 `version`，避免测试状态刷新导致编辑表单产生无意义的乐观锁冲突。

### 7.2 `sql_execution_history`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | varchar(36) PK | 执行 ID，由客户端生成、服务端校验唯一 |
| `user_id` | varchar(64) | 执行用户 |
| `username` | varchar(100) | 用户名快照 |
| `product_id` | varchar(64) | 执行时产品 |
| `data_source_id` | varchar(36) | 数据源 ID |
| `data_source_name` | varchar(100) | 名称快照 |
| `database_name` | varchar(64) | 数据库 |
| `operation` | varchar(20) | `EXECUTE` 或 `EXPORT` |
| `statement_text` | mediumtext | SQL 原文 |
| `statement_hash` | char(64) | SHA-256 |
| `statement_type` | varchar(32) | SELECT/INSERT/UPDATE/DELETE/DDL/OTHER |
| `status` | varchar(20) | RUNNING/SUCCESS/FAILED/CANCELLED/TIMEOUT |
| `result_kind` | varchar(20) null | RESULT_SET/UPDATE_COUNT/DDL |
| `returned_rows` | bigint null | 返回行数 |
| `affected_rows` | bigint null | 影响行数 |
| `truncated` | boolean | 是否截断 |
| `duration_ms` | bigint null | 执行耗时 |
| `mysql_error_code` | int null | MySQL 错误码 |
| `sql_state` | varchar(10) null | SQLState |
| `error_message` | varchar(1000) null | 脱敏错误 |
| `request_id` | varchar(64) | 请求追踪 ID |
| `client_ip` | varchar(64) null | 可选；仅故障定位 |
| `started_at` | datetime(3) | UTC 开始时间 |
| `finished_at` | datetime(3) null | UTC 结束时间 |

索引建议：

- `(user_id, started_at desc)`。
- `(user_id, data_source_id, started_at desc)`。
- `(user_id, status, started_at desc)`。
- `statement_text` 如需模糊搜索可增加 MySQL FULLTEXT；第一版数据量较小时可先用受限 `LIKE`。`keyword` 最长 200 字符，必须转义 `%` 和 `_`。

历史 SQL 原文可能含敏感值，元数据库必须限制访问并启用磁盘/备份加密。第一版为了支持原文搜索和重新打开，不对 SQL 原文做不可逆脱敏。

默认历史保留 90 天，每日清理；通过配置可调整或关闭清理。

## 8. 数据源 API

本节为第一阶段交付范围。所有接口先从 Token 取当前产品，再以 `sql_data_source.product_id` 过滤数据源。响应不包含 `productId`、密码、密文、IV、密钥版本或完整 JDBC URL；产品信息统一从 Session 获取。

无权限与不存在统一返回 `404 DATA_SOURCE_NOT_FOUND`。

成功响应直接返回本节定义的 JSON，不再套 `AjaxResult`。创建返回 201，删除成功返回 204，其余成功返回 200。错误统一返回：

```json
{
  "requestId": "req-...",
  "code": "VALIDATION_FAILED",
  "message": "请求参数不合法",
  "details": {
    "fieldErrors": [
      { "field": "port", "code": "OUT_OF_RANGE", "message": "端口必须在 1 到 65535 之间" }
    ]
  }
}
```

- 请求中的 `X-Request-Id` 合法时沿用，否则服务端生成；响应头和错误体都返回最终 request ID。
- `VALIDATION_FAILED.details.fieldErrors` 固定为数组，字段项包含 `field`、稳定 `code` 和安全 `message`。
- `VERSION_CONFLICT.details` 固定包含 `currentVersion`、`currentUpdatedAt`、`currentUpdatedByName`，前端提示刷新详情后重新编辑。
- `DATA_SOURCE_IN_USE.details` 固定包含 `runningTaskCount`。
- 未声明的 `properties` 键和值一律返回 `400 VALIDATION_FAILED`，不得静默丢弃。
- 数据源写接口使用严格请求 DTO；所有未声明字段均返回 `400 VALIDATION_FAILED`。因此客户端尝试提交产品字段时不会被静默接受。

### 8.1 列表

```http
GET /api/v1/data-sources?keyword=&pageSize=20&pageToken=
```

服务端强制按当前产品过滤，并固定按 `updated_at DESC, id DESC` 排序。`pageSize` 默认 20，上限 100。`keyword` 最长 100 字符，转义 LIKE 通配符后匹配 `name`、`host`。`pageToken` 是服务端生成的不透明游标，至少编码上一页末项的 `updated_at + id`；改变 `keyword` 或 `pageSize` 后旧游标失效并返回 `400 VALIDATION_FAILED`。

```json
{
  "items": [
    {
      "id": "15d7...",
      "name": "订单测试库",
      "engine": "MYSQL",
      "host": "mysql.internal",
      "port": 3306,
      "username": "order_dev",
      "passwordConfigured": true,
      "defaultDatabase": "orders",
      "sslMode": "PREFERRED",
      "lastTestStatus": "SUCCESS",
      "lastTestAt": "2026-08-12T06:00:00Z",
      "version": 3,
      "updatedBy": "1001",
      "updatedByName": "张三",
      "updatedAt": "2026-08-12T06:00:00Z"
    }
  ],
  "nextPageToken": null
}
```

列表不够编辑页回填。`connectTimeoutSeconds`、`properties`、`description` 只在详情接口返回。

### 8.2 详情

```http
GET /api/v1/data-sources/{id}
```

数据源不属于当前产品时返回 404。响应在列表字段基础上增加：

```json
{
  "id": "15d7...",
  "name": "订单测试库",
  "engine": "MYSQL",
  "host": "mysql.internal",
  "port": 3306,
  "username": "order_dev",
  "passwordConfigured": true,
  "defaultDatabase": "orders",
  "sslMode": "PREFERRED",
  "connectTimeoutSeconds": 10,
  "properties": {
    "serverTimezone": "Asia/Shanghai"
  },
  "description": "测试环境",
  "lastTestStatus": "SUCCESS",
  "lastTestAt": "2026-08-12T06:00:00Z",
  "lastTestMessage": null,
  "version": 3,
  "createdBy": "1001",
  "createdByName": "张三",
  "createdAt": "2026-08-11T02:00:00Z",
  "updatedBy": "1001",
  "updatedByName": "张三",
  "updatedAt": "2026-08-12T06:00:00Z"
}
```

`properties` 只回显白名单键。永远不回显密码。

### 8.3 创建

```http
POST /api/v1/data-sources
Content-Type: application/json
```

```json
{
  "name": "订单测试库",
  "engine": "MYSQL",
  "host": "mysql.internal",
  "port": 3306,
  "username": "order_dev",
  "password": "secret",
  "defaultDatabase": "orders",
  "sslMode": "PREFERRED",
  "connectTimeoutSeconds": 10,
  "properties": {
    "serverTimezone": "Asia/Shanghai"
  },
  "description": "测试环境"
}
```

要求：

- `product_id` 由服务端从当前 Token 上下文写入，请求不得包含 `productId` 或 `productIds`；包含时返回 `400 VALIDATION_FAILED`。
- `name` 在 1–100 字符范围内即可，允许重复。
- `connectTimeoutSeconds` 范围 1–30，缺省 10。
- 密码在持久化前加密。
- 创建不隐式测试连接；前端可先调用测试接口。
- 成功返回 201、详情响应体及 `Location: /api/v1/data-sources/{id}`。

### 8.4 编辑

```http
PUT /api/v1/data-sources/{id}
```

`PUT` 是完整替换，不提供部分更新。请求体为：

```json
{
  "name": "订单测试库",
  "engine": "MYSQL",
  "host": "mysql.internal",
  "port": 3306,
  "username": "order_dev",
  "password": "",
  "defaultDatabase": "orders",
  "sslMode": "PREFERRED",
  "connectTimeoutSeconds": 10,
  "properties": {
    "serverTimezone": "Asia/Shanghai"
  },
  "description": "测试环境",
  "version": 3
}
```

数据源不属于当前产品则 404。除 `password`、`defaultDatabase`、`properties`、`description` 外，其余字段和 `version` 必须提交；可空字段若省略按 `null` 或空对象处理。`password` 缺失、`null` 或空字符串均表示保持原密码，明确更换密码必须传非空值。请求不得包含产品字段。成功返回 200 和详情响应，永远不回显密码。

修改连接相关字段后：

1. 提交元数据库事务。
2. 关闭并移除旧连接池。
3. 下一次使用时按新配置懒加载连接池。

### 8.5 删除

```http
DELETE /api/v1/data-sources/{id}?version=3
```

- 数据源不属于当前产品则 404。
- 有正在运行的 SQL 时返回 409 `DATA_SOURCE_IN_USE`。
- 无运行任务时关闭连接池并删除数据源配置。
- 历史记录保留，通过名称快照继续展示。
- 成功返回 204，无响应体。

### 8.6 测试未保存配置

```http
POST /api/v1/data-sources:test
```

只接收以下连接字段，不接收 `name`、`description` 或产品字段：

```json
{
  "host": "mysql.internal",
  "port": 3306,
  "username": "order_dev",
  "password": "secret",
  "defaultDatabase": "orders",
  "sslMode": "PREFERRED",
  "connectTimeoutSeconds": 10,
  "properties": {
    "serverTimezone": "Asia/Shanghai"
  }
}
```

新增场景的 `password` 必填。请求不持久化，等待时间为请求中的 `connectTimeoutSeconds`（缺省 10，硬上限 30 秒），不返回数据库列表。

### 8.7 测试已有数据源

```http
POST /api/v1/data-sources/{id}:test
```

数据源不属于当前产品则 404。该接口支持两种明确模式：

- 不发送请求体：测试已保存配置，并把结果写入 `last_test_status`、`last_test_at`、`last_test_message`。
- 发送完整连接字段请求体：测试编辑页当前但尚未保存的配置；`password` 缺失、`null` 或空字符串时复用该数据源已保存密码。该模式不保存表单，也不更新最近测试状态。

两种测试接口都以如下结构返回连接结果。成功连接或目标库连接失败都返回 HTTP 200；输入校验、Host 网络策略拒绝等未发起连接的情况返回统一 `ApiError`：

```json
{
  "status": "SUCCESS",
  "serverVersion": "8.0.36",
  "durationMs": 128,
  "message": "连接成功",
  "failureCode": null
}
```

```json
{
  "status": "FAILED",
  "serverVersion": null,
  "durationMs": 10012,
  "message": "连接超时，请检查主机、端口和网络策略",
  "failureCode": "CONNECTION_TIMEOUT"
}
```

`failureCode` 稳定取值为 `AUTHENTICATION_FAILED`、`CONNECTION_REFUSED`、`CONNECTION_TIMEOUT`、`DATABASE_NOT_FOUND`、`TLS_FAILED`、`CONNECTION_FAILED`。`message` 必须脱敏，不返回 JDBC URL、密码、堆栈或内部网络细节。

## 9. 密码与连接安全

### 9.1 密码存储

- 使用 AES-256-GCM，每条记录使用随机 96 位 IV。
- 主密钥只从 Secret/环境变量/配置中心注入，不写入代码库和业务数据库。
- 密文、IV 和 `key_version` 分字段保存，支持以后轮换。
- 应用启动时主密钥缺失必须失败，不允许退化为明文。
- 日志对象和 DTO 的 `toString()` 排除密码。
- 测试连接失败时清理可能含用户名、Host 或参数的底层异常，只返回必要信息。

### 9.2 JDBC URL

后端根据结构化字段生成 URL，不接受用户传入完整 JDBC URL。Host 为 IPv6 时自动加方括号。

用户 `properties` 只允许下列键，大小写按 Connector/J 规范；其他键或不在允许集合中的值统一返回 `400 VALIDATION_FAILED`：

| 允许的键 | 允许值 |
| --- | --- |
| `serverTimezone` | IANA 时区名，例如 `Asia/Shanghai`、`UTC` |
| `characterSetResults` | `utf8`、`UTF-8` |
| `zeroDateTimeBehavior` | `CONVERT_TO_NULL`、`EXCEPTION`、`ROUND` |
| `tinyInt1isBit` | `true`、`false` |
| `sendFractionalSeconds` | `true`、`false` |

明确禁止（即使用户传入也不得生效）：`useSSL`、`requireSSL`、`verifyServerCertificate`、`allowMultiQueries`、`allowLoadLocalInfile`、`allowUrlInLocalInfile`、`autoDeserialize`、`user`、`password`、`sessionVariables`、`connectionAttributes`、`socketFactory`、`socksProxyHost`、`localSocketAddress`、`connectTimeout`、`socketTimeout`、以及任意 Connector 扩展类相关参数。SSL 只走表单字段 `sslMode`；连接超时只走 `connectTimeoutSeconds`。

合并顺序：

1. 用 host / port / database 生成基础 URL。
2. 写入白名单内的用户参数。
3. **最后写入固定安全参数，同名键以固定参数为准，用户值不能覆盖。**

固定安全参数：

```text
allowMultiQueries=false
allowLoadLocalInfile=false
allowUrlInLocalInfile=false
autoDeserialize=false
useUnicode=true
characterEncoding=UTF-8
```

`sslMode` 映射：

- `DISABLED`：`useSSL=false`
- `PREFERRED`：`useSSL=true`、`requireSSL=false`、`verifyServerCertificate=false`
- `REQUIRED`：`useSSL=true`、`requireSSL=true`、`verifyServerCertificate=false`

第一阶段不提供 `VERIFY_CA` / `VERIFY_IDENTITY`。文档和运维说明必须写明：当前三档不校验服务器证书。


### 9.3 Host 访问

任意用户都能新增 Host 会带来内网端口探测风险。部署配置提供允许/禁止 CIDR，解析 DNS 后校验**所有** A/AAAA 地址：

```yaml
sql-editor:
  network:
    allowed-cidrs: ["10.0.0.0/8", "172.16.0.0/12"]
    denied-cidrs: ["127.0.0.0/8", "169.254.0.0/16"]
```

校验通过后，使用本次解析得到的 IP 建立连接，避免再次解析造成 DNS 重绑定。如果业务确实需要 localhost 或其他网段，应显式配置，而不是关闭校验。

## 10. 动态连接池

- 每个已使用的数据源创建独立 HikariPool，首次访问时懒加载。
- 默认每池最大 5、最小 0；连接超时使用该数据源的 `connectTimeoutSeconds`（1–30，缺省 10）；空闲 10 分钟回收。
- 长时间无访问的数据源池整体关闭，默认 30 分钟。
- 配置更新/删除时关闭旧池。
- 全局限制连接池数量和目标库连接总数，避免大量数据源耗尽资源。
- 连接初始化可执行 `SET time_zone`，不得执行用户可配置的任意初始化 SQL。
- 所有请求借出的 Connection、Statement、ResultSet 必须使用 try-with-resources。

还池前必须重置会话，避免 `USE` / `SET` / `setCatalog` 污染下一个请求：

1. `connection.setAutoCommit(true)`。
2. 若数据源 `default_database` 非空，将 catalog 恢复为该值。Connector/J 不允许 `setCatalog(null)`，空默认库不得调用 `setCatalog`。
3. 若默认库为空且本次请求已经切换过 catalog，从目标 Hikari 池逐出该连接（`HikariDataSource.evictConnection`），不要调用 `Connection.abort()` 后再还池——abort 会掐死物理连接却把已关闭的包装对象留在池里，下一个请求（例如查看表 DATA）就会在 `setAutoCommit` 时报 `No operations allowed after connection closed`。
4. 清理警告；如有未提交事务（防御性）则 rollback。还池/丢弃失败不得覆盖已经成功的业务结果。
5. 不要依赖用户 SQL 里的 `USE` 或 `SET SESSION` 对后续请求仍然生效。第二阶段若用户执行了这类语句，只影响当次连接，还池后丢弃。

测试连接使用短生命周期连接，测完立即关闭，不要把测试连接放进业务池。

“连接数据源”在产品语义上是选择一个数据源；后端不会为浏览器维持永久 JDBC Connection。

## 11. 元数据 API

所有元数据接口先校验数据源 `product_id` 与当前产品一致（失败一律 404），再使用目标 MySQL 账号访问。数据库账号看不到的对象不额外暴露。本节为第二阶段。

### 11.1 数据库列表

```http
GET /api/v1/data-sources/{id}/databases?keyword=&pageSize=100&pageToken=&includeSystem=false
```

`pageSize` 默认 100，上限 200。数据来源可使用 `DatabaseMetaData#getCatalogs()` 或 `INFORMATION_SCHEMA.SCHEMATA`。是否在导航中展示系统库由 `includeSystem` 和配置决定，默认隐藏 `information_schema`、`performance_schema`、`mysql` 和 `sys`。

隐藏系统库只影响元数据导航。用户在 SQL 编辑器里仍可查询这些库，最终可见性以 MySQL 账号为准；`SELECT INTO OUTFILE` 等同样不额外拦截。

### 11.2 表和视图

```http
GET /api/v1/data-sources/{id}/tables?database=orders&keyword=&types=TABLE,VIEW&pageSize=200&pageToken=
```

`pageSize` 默认 200，上限 200。

返回：

```json
{
  "items": [
    {
      "database": "orders",
      "name": "order_item",
      "type": "TABLE",
      "comment": "订单明细"
    }
  ],
  "nextPageToken": null
}
```

### 11.3 表结构

```http
GET /api/v1/data-sources/{id}/table-detail?database=orders&table=order_item
```

返回字段：

- `columns`：名称、原始类型、JDBC 类型、长度、精度、小数位、是否可空、默认值、额外属性、注释、序号。
- `primaryKey`：名称和有序字段。
- `indexes`：名称、是否唯一、类型和有序字段。
- `ddl`：`SHOW CREATE TABLE` 返回的建表/建视图语句；读取失败时为 `null`。

数据库名和表名只作为参数传给 `DatabaseMetaData` 或经过反引号转义的内部固定 SQL，禁止直接拼接未经校验的标识符。

## 12. SQL 执行 API

本节为第二阶段。数据源不属于当前产品时返回 `404 DATA_SOURCE_NOT_FOUND`。

### 12.1 执行语义

- 支持目标账号允许的查询、DML、DDL 和其他单条 MySQL Statement。
- 不做危险语句识别、审批或额外拦截。`SELECT INTO OUTFILE`、`LOAD DATA` 等能否执行，只取决于 MySQL 账号。
- `autoCommit=true`。DML 成功即提交；MySQL DDL 通常会隐式提交。
- 一次请求只允许一条语句，Connector 设置 `allowMultiQueries=false`。
- 前端把脚本切分后逐条串行提交，每条语句一个请求和一个 `executionId`；后端用同一规则做权威校验。无法可靠识别或存在第二条语句时返回 `400 MULTI_STATEMENT_NOT_SUPPORTED`。这条兜底不因前端支持脚本而放宽。
- 不支持 `DELIMITER` 客户端命令；存储过程、触发器脚本不作为验收范围。
- 取消和超时都是尽力而为。语句已在数据库完成或已经提交时，取消不能撤销结果。

### 12.2 SQL 语句分类

| 形态 | statement_type | result kind | database 是否必填 |
| --- | --- | --- | --- |
| `SELECT` / `WITH ... SELECT` / `SHOW` / `EXPLAIN` / `DESC` / `DESCRIBE` | 对应 SELECT 或 OTHER | `RESULT_SET` | 访问表对象时必填；`SHOW DATABASES`、`SELECT 1` 可不填 |
| `INSERT` / `UPDATE` / `DELETE` / `REPLACE` | 对应类型 | `UPDATE_COUNT` | 必填 |
| `CREATE` / `ALTER` / `DROP` / `TRUNCATE` / `RENAME` | `DDL` | `DDL` | 必填（除非语句自带库名且后端能设置 catalog） |
| `USE` / `SET` / `SET SESSION` | `OTHER` | `UPDATE_COUNT` | 否。执行后仍按 §10 重置会话，不保证对后续请求生效 |
| `CALL` | `OTHER` | 只取第一个结果集或更新计数；多结果集其余丢弃 | 视过程而定 |
| 无法分类 | `OTHER` | 按 JDBC 是否有 ResultSet 决定 | 访问表对象时必填 |

`jdbcType` 返回 JDBC `java.sql.Types` 的名称字符串（如 `BIGINT`），不要返回整数常量。`typeName` 返回数据库原生类型名。

### 12.3 默认限制

参考最新 Bytebase 的 100,000 行和 100 MB 上限，设置：

```yaml
sql-editor:
  execution:
    default-row-limit: 1000
    max-row-limit: 100000
    max-result-bytes: 104857600
    timeout-seconds: 60
    max-concurrent-per-user: 3
    max-concurrent-global: 50
    max-statement-bytes: 1048576
    executor-pool-size: 50
```

- 前端可请求 `rowLimit`，范围 1–100,000；缺省 1,000。
- JDBC 使用 `Statement#setQueryTimeout(60)` 和 `setMaxRows(rowLimit + 1)`，读取多一行判断是否截断。
- 同时统计序列化后的近似字节数，超过 100 MB 时停止读取并标记截断。
- DML/DDL 同样受 60 秒超时约束，但不应用返回行数限制。
- 这些都是应用保护，不保证降低目标数据库的扫描成本。

### 12.4 执行线程模型

同步 MVC 里把 JDBC 跑在 Tomcat 工作线程上，60 秒查询会占满容器线程，取消接口可能没有线程可跑。必须拆开：

```text
HTTP 请求（Tomcat / 异步分派）
        │ WebAsyncTask / DeferredResult，超时 60 秒
        ▼
执行线程池（大小 = max-concurrent-global）
        │ 借连接、创建 Statement、登记 ExecutionRegistry
        ▼
目标 MySQL
```

要求：

- SQL 执行和 CSV 导出使用 Servlet 异步，把 Tomcat 线程还回去，再在独立执行线程池里跑 JDBC。
- 执行线程池大小不小于 `max-concurrent-global`。
- Tomcat `maxThreads` 按普通 API + 取消 + 健康检查配置，不要按“并发执行数 × 60 秒”去估。
- `ExecutionRegistry` 保存 `executionId -> userId / Statement / Future`，供取消接口从另一条 HTTP 请求调用 `Statement.cancel()` 并 `Future.cancel(true)`。
- 客户端断开、异步超时或容器错误回调里取消 Statement，并归还连接。导出已有“HTTP 断开即取消”；普通执行同样适用。
- 第二阶段单实例部署，取消才能定位到持有 Statement 的进程。需要多实例时再做执行节点路由。

### 12.5 创建执行

前端先生成 UUID，便于请求未结束时取消：

```http
POST /api/v1/sql/executions
Content-Type: application/json
```

```json
{
  "executionId": "f44b...",
  "dataSourceId": "15d7...",
  "database": "orders",
  "statement": "select * from order_item limit 100",
  "rowLimit": 1000
}
```

`executionId` 规则：

- 必须是规范 UUID 字符串，否则 `400 VALIDATION_FAILED`。
- 该 ID 只要已经在历史表或运行注册表中出现过，一律 `409 EXECUTION_ID_CONFLICT`。不区分是当前用户还是他人、运行中还是已结束，避免泄露别人的执行 ID。
- 前端禁止对执行请求自动重试，因此正常路径不会用同一 ID 重放。冲突时前端应新生成 ID 再提交。

后端处理顺序：

1. 校验 Token，并确认数据源 `product_id` 与当前产品一致；不一致则 404。
2. 校验执行 ID、SQL 大小、单语句规则、database 必填规则和并发配额。
3. 先插入 `RUNNING` 历史记录（唯一约束冲突则返回 `EXECUTION_ID_CONFLICT`）。
4. 在执行线程池中注册 `executionId -> userId/Statement/Future`。
5. 设置数据库 Catalog 并执行 SQL。
6. 返回结果或标准错误，同时更新历史状态。
7. 重置连接会话并归还连接池，从运行注册表移除资源。

查询响应：

```json
{
  "executionId": "f44b...",
  "kind": "RESULT_SET",
  "columns": [
    {
      "name": "id",
      "label": "id",
      "jdbcType": "BIGINT",
      "typeName": "BIGINT"
    }
  ],
  "rows": [["9007199254740993"]],
  "rowCount": 1,
  "truncated": false,
  "durationMs": 28
}
```

DML 响应：

```json
{
  "executionId": "f44b...",
  "kind": "UPDATE_COUNT",
  "affectedRows": 42,
  "durationMs": 97,
  "message": "执行成功"
}
```

DDL 响应 `kind=DDL`，`affectedRows` 可为 `null`。

### 12.6 数据类型编码

- `TINYINT/SMALLINT/INTEGER` 在安全范围内返回 JSON Number。
- `BIGINT` 返回 JSON String。
- `DECIMAL/NUMERIC` 返回 JSON String，避免精度丢失。
- 日期时间返回 MySQL 原始规范化字符串，不强制转换浏览器时区。
- `NULL` 返回 JSON null。
- `BINARY/VARBINARY/BLOB` 返回 `{ "binary": true, "size": 1234, "base64": null }`；不回传内容。
- JSON 字段默认作为字符串返回，避免改变键顺序和数字精度。

### 12.7 取消执行

```http
POST /api/v1/sql/executions/{executionId}:cancel
```

- 取消走同步短请求，不进执行线程池。
- 只有创建该执行的用户可取消。他人的 ID 或不存在的 ID 返回 `404 EXECUTION_NOT_FOUND`，不泄露是否存在。
- 调用 `Statement.cancel()` 并中断执行 Future。
- 属于当前用户但已结束时返回 409 `EXECUTION_ALREADY_FINISHED`。
- 多实例部署时，运行注册表需要迁移到可定位执行节点的方案；在此之前单实例部署。

### 12.8 错误响应

SQL 错误使用适当 HTTP 状态和统一 Body：

```json
{
  "requestId": "req-...",
  "code": "SQL_EXECUTION_FAILED",
  "message": "Table 'orders.unknown' doesn't exist",
  "details": {
    "executionId": "f44b...",
    "sqlState": "42S02",
    "mysqlErrorCode": 1146
  }
}
```

不得返回堆栈、密码、完整 JDBC URL 或应用内部类名。

## 13. CSV 导出 API

本节为第二阶段。

```http
POST /api/v1/sql/exports
Accept: text/csv
```

请求：

```json
{
  "executionId": "f44b...",
  "rowLimit": 100000
}
```

规则：

- 导出会读取该用户执行历史中的数据源、数据库和 SQL 原文并重新执行，不读取某次执行在内存中的结果。
- `executionId` 必须属于当前用户，历史状态必须为 `SUCCESS`、结果类型必须是 `RESULT_SET`，且用户当前仍有该数据源权限。他人或不存在的 ID 返回 `404 EXECUTION_NOT_FOUND`。
- 客户端不能在导出请求中替换 SQL，避免把导出接口变成绕过正常执行流程的第二个 SQL 执行入口。
- DML/DDL 历史不可导出，返回 400。
- 使用 Servlet 异步 + `StreamingResponseBody` 和流式 ResultSet，逐行写 CSV，不在内存构建整个文件。
- UTF-8 BOM、RFC 4180 引号转义、CRLF 行结束符。
- 默认 `NULL` 导出为空字段；配置可改为字面量 `NULL`。
- 默认启用 CSV 公式注入保护：以 `= + - @` 开头的文本字段前加单引号；可以通过部署配置关闭以获得完全原始值。
- 应用同样的 100,000 行、100 MB 和 60 秒上限。
- HTTP 断开时取消 Statement 并释放连接。
- 响应头使用安全文件名及 `Content-Disposition: attachment`。
- 导出行为也写一条 `operation=EXPORT` 的执行历史，新记录保存实际重放的 SQL 和独立执行 ID。
- 浏览器侧使用 `fetch` + Blob 下载，100 MB 上限对浏览器内存同样生效。

## 14. 执行历史 API

### 14.1 历史列表

```http
GET /api/v1/sql/history?keyword=&dataSourceId=&database=&status=&statementType=&pageSize=30&pageToken=
```

- 强制 `user_id = currentUserId`，不允许读取同产品其他用户历史。
- `pageSize` 默认 30，上限 100。`keyword` 最长 200 字符，LIKE 必须转义 `%` 和 `_`。
- 游标包含 `started_at + id`，不要使用大 Offset 分页。
- 列表只返回 SQL 摘要；原文在详情接口返回。
- 记录 `client_ip` 时只信任网关设置的转发头，并配置可信代理；不要直接读任意 `X-Forwarded-For`。

### 14.2 历史详情

```http
GET /api/v1/sql/history/{id}
```

返回完整 SQL、连接快照和执行结果摘要，不保存或返回查询数据行。

若数据源仍存在但当前用户产品已经与数据源所属产品不一致：

- 仍可查看属于自己的历史 SQL。
- `connectionAvailable=false`。
- 不返回当前数据源详情，也不能直接重新执行。

## 15. 统一错误码

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 参数错误 |
| 400 | `MULTI_STATEMENT_NOT_SUPPORTED` | 多语句 |
| 400 | `SQL_NOT_EXPORTABLE` | DML/DDL 导出 |
| 401 | `UNAUTHENTICATED` | Token 无效 |
| 404 | `DATA_SOURCE_NOT_FOUND` | 数据源不存在，或不属于当前产品 |
| 404 | `DATABASE_NOT_FOUND` | 数据库不存在 |
| 404 | `EXECUTION_NOT_FOUND` | 执行不存在，或不属于当前用户 |
| 409 | `VERSION_CONFLICT` | 乐观锁冲突 |
| 409 | `USER_PRODUCT_CONTEXT_INVALID` | 当前用户没有唯一有效产品 |
| 409 | `DATA_SOURCE_IN_USE` | 有运行任务时删除 |
| 409 | `EXECUTION_ID_CONFLICT` | 客户端执行 ID 已被使用 |
| 409 | `EXECUTION_ALREADY_FINISHED` | 取消已结束且属于自己的任务 |
| 413 | `STATEMENT_TOO_LARGE` | SQL 超过 1 MB |
| 429 | `EXECUTION_LIMIT_EXCEEDED` | 并发超限 |
| 422 | `SQL_EXECUTION_FAILED` | MySQL 语法、权限或执行错误 |
| 503 | `AUTH_SERVICE_UNAVAILABLE` | 授权服务不可用 |
| 504 | `SQL_EXECUTION_TIMEOUT` | 执行超时 |

MySQL 权限不足属于 `SQL_EXECUTION_FAILED`，通过 `sqlState/mysqlErrorCode` 给前端展示原始数据库错误。

## 16. 并发、事务与一致性

- 数据源配置增删改使用元数据库本地事务。
- 更新数据源使用 `version` 乐观锁，避免并发编辑被静默覆盖。
- SQL 执行使用目标库独立连接和 `autoCommit=true`。
- 不把目标库事务与元数据库历史事务组成分布式事务。
- 若目标 SQL 已成功但更新历史失败，SQL 结果不能回滚；记录指标和错误日志，由后台补偿将超时 `RUNNING` 标记为 `UNKNOWN/FAILED`。
- 服务启动时把超过配置阈值仍为 `RUNNING` 的历史修正为 `FAILED`，错误码 `SERVICE_RESTARTED`。

## 17. 日志、指标与健康检查

虽然不建设危险操作审计，仍需普通应用可观测性：

- 日志记录 `requestId`、用户 ID、产品 ID、数据源 ID、执行 ID、耗时和状态。
- 默认不在应用日志打印 SQL 原文；只记录 `statementHash` 和语句类型。
- 不记录 Token、数据库密码、结果数据和完整 JDBC URL。
- 指标：执行次数、失败数、超时数、耗时分布、活动执行数、连接池数、连接等待时间、导出字节数、授权服务失败率。
- `/actuator/health/liveness` 不访问任何数据库。
- `/actuator/health/readiness` 只检查元数据库和授权服务，不遍历目标数据源。
- 生产只暴露 `health` 和 `prometheus`，绑到管理端口或内网；禁止默认开放 `env`、`heapdump`。

## 18. 推荐包结构

```text
com.bocsoft.sqleditor
  auth/
    AuthClient
    AuthContextFilter
    CurrentUser
  datasource/
    DataSourceController
    DataSourceService
    CredentialCipher
    DynamicPoolManager
  metadata/
    MetadataController
    MysqlMetadataService
  execution/
    SqlExecutionController
    SqlExecutionService
    ExecutionRegistry
    SqlExecutorPool
    JdbcValueEncoder
  export/
    CsvExportController
    CsvExportService
  history/
    HistoryController
    HistoryService
  common/
    ApiError
    RequestIdFilter
    GlobalExceptionHandler
  config/
```

## 19. 测试要求

### 19.1 单元测试

第一阶段：

- 数据源所属产品判断；产品不匹配与不存在都映射为 404。
- 密码 AES-GCM 加解密、错误密钥和轮换版本。
- 数据源列表/详情不泄露密码字段。
- JDBC 白名单拒绝未知或禁止键，固定安全参数不能被用户覆盖。
- IPv6 Host 生成 URL 时加方括号。
- 乐观锁和统一错误映射。

第二阶段：

- BIGINT、DECIMAL、NULL、日期和 BLOB 编码。
- CSV 引号、逗号、换行、中文、NULL 和公式注入保护。
- 历史只能由创建用户读取。
- `executionId` 冲突与非法 UUID。
- LIKE 通配符转义。

### 19.2 Testcontainers 集成测试

第一阶段：

- 新增、详情回填、测试、更新、删除 MySQL 数据源。
- 测试连接等待时间与 `connectTimeoutSeconds` 一致，硬上限 30 秒。
- 连接还池后 catalog 不残留上一次 `USE`。
- 解析后的 IP 不在允许 CIDR 时拒绝。

第二阶段：

- 数据库/表/字段/主键/索引元数据；`defaultDatabase` 为空时列库仍返回账号可见的非系统库。
- SELECT、INSERT、UPDATE、DELETE、CREATE、ALTER、DROP、SHOW DATABASES。
- 账号权限不足时保留 MySQL 错误码。
- 超时和取消后连接能归还池；取消请求在执行占用期间仍能被处理。
- 返回行数和体积截断。
- 导出大结果时服务端内存保持稳定。

### 19.3 权限集成测试

构造产品 A、B，并让两个产品分别创建名称、Host、端口完全相同但 ID 不同的数据源：

- A 的列表只出现 A 创建的数据源，B 的列表只出现 B 创建的数据源。
- A 用 B 的数据源 ID 访问详情、测试、更新、删除，得到 404 而不是 403。
- 创建或更新请求手工传 `userId`、`productId`、`productIds` 均无效，其中产品字段返回 400。
- A 修改或删除自己的数据源，不改变 B 的独立数据源。
- 第二阶段：用户只能查询自己的历史。

## 20. 部署要求

- 推荐仓库布局：`docs/`、`web/`、`service/`。
- 前端和 SQL 服务推荐由同一网关提供同源地址。登录/退出若不能反代，须单独配置授权服务 CORS。
- SQL 服务到授权服务、元数据库和目标 MySQL 使用内网连接。
- 生产 Secret 至少包括：元数据库密码、凭据主密钥、内部服务密钥。
- 网关上传/请求体限制要允许 1 MB SQL，但禁止无限请求体。
- 第二阶段：SQL 执行和 CSV 导出的网关读取超时应略高于应用 60 秒，例如 75 秒。
- 执行线程池、Tomcat 线程和 `max-concurrent-global` 按 §12.4 配置。
- 第二阶段单实例部署，保证执行取消可定位；需要高可用时再设计执行节点路由。
- 可信代理配置后再使用转发 IP。

## 21. 交付物

第一阶段：

- Spring Boot 源码及配置样例（认证、数据源、加密、连接池、测试连接）。
- Flyway 建表脚本（`utf8mb4`，数据源表直接含 `product_id`；执行历史表可先建好供第二阶段使用）。
- 数据源相关 OpenAPI。
- 授权服务新增接口说明和 Mock Server。
- 数据源 Testcontainers 集成测试。
- 数据源凭据密钥生成/轮换运维说明。
- 部署环境变量清单和健康检查说明。

第二阶段追加：

- 元数据、执行、导出、历史源码与 OpenAPI。
- 执行线程池与取消的集成测试。
- Testcontainers 覆盖 SQL 执行与导出。

## 22. 待评审决策

- 先做数据源管理，再做 SQL 编辑器。
- 后端运行基线：Java 8 + Spring Boot 2.7.18，跟随授权服务；已知 OSS 维护期已结束。
- 数据源列表由后端按所属产品过滤；创建时由服务端写入当前产品，客户端不传产品字段。
- 不提供共享和跨产品绑定；不同产品连接同一数据库时分别创建不同数据源 ID。
- 数据源名称允许重复，唯一身份只使用 ID。
- SSL 三档不校验证书。
- 默认查询限制：1,000 行；硬上限 100,000 行；结果上限 100 MB；超时 60 秒。
- SQL 历史默认保留 90 天。
- 第二阶段单实例部署，以实现可靠的运行中取消。
- CSV 默认开启公式注入保护。
- SQL 原文保存在历史库中以支持搜索和重新打开，不做内容脱敏。
