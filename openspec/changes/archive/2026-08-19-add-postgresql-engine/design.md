## Context

阶段 0 抽出 `EngineSupport`，阶段 1 用目录驱动表单和 NAMESPACE 树。注册表仍只有 MYSQL：NAMESPACE 对应 catalog，`defaultDatabase` 与树上的第一层是同一个东西。PostgreSQL 不是这样：连接必须钉死一个 **database**，树上第一层是该库里的 **schema**。本变更用第二个关系型引擎验证这套映射，同时证明主干不必再为第二种库加 `if (MYSQL)`。

约束不变：Java 8、Spring Boot 2.7、单实例、目标连接走现有 CIDR 与动态 Hikari 池。服务元数据库仍是 Flyway 管理的 MySQL。

## Goals / Non-Goals

**Goals:**

- 注册 `POSTGRESQL`：连接、测试、资源树、执行、导出都走该实现。
- `defaultDatabase` = 钉死的 PostgreSQL 数据库（JDBC URL path），创建/更新/测试必填。
- `GET .../databases` 对 PG 列出 schema；`?database=` / 执行 JSON `database` = schema 名。
- 目录：`family=POSTGRES_WIRE`、端口 5432、`editorLanguage=pgsql`、NAMESPACE 文案「模式」。
- 用 Testcontainers 覆盖：目录两项、schema 列表、系统 schema 隐藏、选中 schema 后执行、失败分类。
- MYSQL 回归保持现有行为。

**Non-Goals:**

- 不注册 Hive / GBase，不加 PARTITION。
- 不把 `/databases`、`defaultDatabase`、URL `database` 改名。
- 不抽 `PostgresFamily` / JDBC 族基类；MYSQL 与 PG 仍是两个独立实现。
- 不在同一连接上切换 PostgreSQL database（做不到）；只切换 schema。
- 不把前端 `sql.ts` 做成完整按引擎切换的扫描器；后端仍是多语句拒绝的权威。允许为 dollar-quote / 双引号做最小扩展，避免 PG 脚本在前端被拆错。

## Decisions

### 1. `defaultDatabase` 钉库，NAMESPACE 填 schema

| 概念 | MySQL | PostgreSQL |
| --- | --- | --- |
| JDBC URL path | catalog | database（`defaultDatabase`） |
| 树第一层 NAMESPACE | catalog | schema |
| `?database=` / 执行 `database` | catalog 名 | schema 名 |
| `applyNamespace` | `setCatalog` | `setSchema` |
| `restoreSession` | 还原 catalog；空默认库则逐出 | `SET search_path TO DEFAULT`（参数是钉死库名，不是 schema，忽略作 search_path 目标） |
| `jdbcUrlWithoutNamespace` | 去掉 URL 末段 catalog | **原样返回**（URL 里没有 schema；去掉 database 会连到错误的库且无法切换） |
| `defaultNamespaceRequired` | false | true（必须钉死 database） |
| `canSwitchNamespaceOnConnection` | true | true（可切换 schema） |

列表「默认数据库」列名不改：它对应连接字段，不是树上的 schema。

### 2. 实现放在 `engine.postgres`，Spring `@Order` 固定目录顺序

`EngineId.POSTGRESQL = "POSTGRESQL"`。`PostgresEngineSupport` `@Order(2)`，MYSQL `@Order(1)`，目录稳定为 MYSQL 然后 POSTGRESQL。

不把协议族抽成基类。SPI 只加两个默认方法，避免导出和超时写死 MySQL：

- `applyConnectTimeout(Properties, millis)`：MYSQL 写毫秒 `connectTimeout`；PG 写秒级 `connectTimeout` / `loginTimeout`。
- `streamingFetchSize()`：MYSQL `Integer.MIN_VALUE`；PG 正数（负值会被驱动拒绝）。

ArchUnit：主干不得依赖 `engine.postgres`，与 mysql 规则并列。

### 3. JDBC、SSL、属性白名单

URL：`jdbc:postgresql://host:port/database`，IPv6 加方括号。

SSL：`DISABLED` → `sslmode=disable`；`PREFERRED` → `sslmode=prefer`；`REQUIRED` → `sslmode=require`。不做 CA/主机名校验。

`propertyFields` 与校验同源，只开放少量键，例如 `ApplicationName`、`stringtype`、`tcpKeepAlive`、`reWriteBatchedInserts`。禁止 `currentSchema`（与树切换冲突）、socket/ssl factory、会话初始化。

标识符最长 63。`defaultDatabase` 与 schema 名都走 `validateIdentifier`。

### 4. 元数据与 DDL

`listDatabases` 用 `DatabaseMetaData.getSchemas()`。默认隐藏 `information_schema` 以及 `pg_` 前缀 schema（含 `pg_catalog` / `pg_toast` / `pg_temp_*`）。`public` 与用户 schema 可见。

`listTables` / `tableDetail` 把 `database` 参数当 schema：`getTables(null, schema, ...)`。DDL：视图用 `pg_get_viewdef`；表从列/主键重建简化 `CREATE TABLE`，拿不到则 `null`（与 MYSQL 失败时一致）。

系统 schema 隐藏只影响导航；用户仍可在编辑器里查 `pg_catalog`。

### 5. 扫描与失败分类

PG 扫描器：单引号（`''`，默认无反斜杠）、`E'...'` 转义串、双引号标识符、dollar-quote `$tag$...$tag$`、`--` / `/* */`。不认 MySQL 的 `#` 和反引号。

失败分类：`28P01`/`28000` → `AUTHENTICATION_FAILED`；`3D000` / “database does not exist” → `DATABASE_NOT_FOUND`；超时/拒绝/TLS 复用现有码。PostgreSQL 驱动 `getErrorCode()` 常为 0，执行错误仍以 SQLState 为主，`vendorErrorCode` 仅在非 0 时出现。

### 6. 前端：目录、pgsql、引号

目录 fixture 增加 POSTGRESQL。动态表单已按描述渲染；PG 的 `defaultDatabase.required=true` 由现有 `connectionFields` 驱动（补上 required 校验）。

Monaco 注册 `pgsql`；`editorLanguageFor` 对 `pgsql` 返回 `pgsql`，未知 language 仍回退 `mysql`。格式化把 `pgsql` 映射为 sql-formatter 的 `postgresql`。

目录增加 `identifierQuote`（MYSQL `` ` ``，PG `"`）。资源树插入预览和表 DATA 查询用该引号。前端扫描器增加 dollar-quote，避免 `SELECT $tag$ a;b $tag$` 被拆成两条；后端仍是权威。

## Risks / Trade-offs

- [MYSQL 回归被 PG 目录顺序/校验带偏] → `@Order` 固定 MYSQL 第一；未保存测试省略 engine 仍 MYSQL；现有 MYSQL Testcontainers 用例保留。
- [把 defaultDatabase 误当成 schema] → 文档和测试钉死：创建必须填数据库名；树返回 `public`/`sales` 而不是库名；执行 `database=public`。
- [jdbcUrlWithoutNamespace 若按 MYSQL 削 URL] → PG 实现原样返回；连接测试用 URL 里的库，失败走 `3D000`。
- [导出 fetchSize=MIN_VALUE 在 PG 抛错] → `streamingFetchSize()` 分引擎。
- [connectTimeout 毫秒被 PG 当成秒] → `applyConnectTimeout` 换算。
- [前端扫描器仍偏 MySQL] → 只补 dollar-quote；多语句拒绝以后端为准。
- [第二套 Testcontainers 变慢] → PG 集成测试单独类，`disabledWithoutDocker`。

## Migration Plan

1. 前后端同发：新驱动、目录第二项、PG 表单/树/pgsql。
2. 无需 Flyway；`sql_data_source.engine` 已存在。
3. 回滚：回退应用版本。已存 `engine=POSTGRESQL` 的行在旧版本会 `ENGINE_NOT_SUPPORTED`，符合阶段 0 的 fail-closed。

## Open Questions

- PG 是否允许省略 `defaultDatabase`（驱动会用用户名当库名）。决定：不允许。树和执行都依赖钉死库，省略会造成「连到哪个库」不透明。
- 是否把 `SqlStatementClassifier` 下沉到引擎。决定：本阶段不迁；PG 的 SELECT/DML/DDL 首词与现分类兼容，`SHOW`/`REPLACE` 对 PG 走 OTHER 可接受。
