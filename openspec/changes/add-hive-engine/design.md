## Context

阶段 0 抽 `EngineSupport`，阶段 1/2 用目录驱动表单与 NAMESPACE 树，阶段 3 用 GBase 8a 证明「同族新产品只挂族」。现注册表为 MYSQL、POSTGRESQL、GBASE_8A（见已归档的 `add-gbase-8a-engine` 与当前 `backend-engine-*` 规范）。

参考实现 `bddf-public-service` 的普通 Hive 连接极简：`DbToolsUtils.getConnection()` 对 `DBTYPE_HIVE="3"` 拼 `jdbc:hive2://<ip>:<port>/<db>`，`DriverManager.getConnection(url, username, pwd)`；跨集群时追加 `?hive.metastore.uris=thrift://...`；元数据用 `show databases / show tables / desc / show create table`。

约束不变：Java 8、Spring Boot 2.7、单实例、目标连接走现有 CIDR 与动态 Hikari 池。服务元数据库仍是 Flyway 管理的 MySQL。

## Goals / Non-Goals

**Goals:**

- 注册 `HIVE`（dbType 3）：连接、测试、资源树、执行、导出都走该实现，且走现有池与 CIDR。
- `family=HIVE_WIRE`；Hive 方言目录与扫描（metastore `show` 指令），不复用 MySQL 的 `information_schema`。
- JDBC `jdbc:hive2://`；IPv6 加括号；跨集群死表地址改成白名单属性而非硬编码。
- 目录：`displayName=Hive`、`defaultPort=10000`、`editorLanguage=hive`、NAMESPACE 文案「数据库」、`defaultDatabase` 可选。
- 主干（`EngineSupport`、编排层、`DynamicPoolManager`）零改动。
- MYSQL / POSTGRESQL / GBASE_8A 回归保持现有行为。

**Non-Goals:**

- 不注册 HIVE_KERBEROS / ICEBERG，不引入 Kerberos / ZooKeeper / keytab / UGI（见后续 change `add-kerberos-zk-discovery`、`add-iceberg-engine`）。
- 不抽公共 Hive 基类前先做 Kerberos；本变更只交付普通 JDBC。
- 不把厂商 JAR 提交进仓库，不从非官方源下载或伪造 stub。
- 不改 `EngineSupport` 方法签名或 `DynamicPoolManager`。

## Decisions

### 1. Hive 是「新协议族」，独立实现目录与 JDBC，不委托 MySQL

| 概念 | MySQL / GBase 8a | Hive |
| --- | --- | --- |
| `engine` id | MYSQL / GBASE_8A | HIVE |
| `family` | MYSQL_WIRE | HIVE_WIRE |
| JDBC 驱动 | Connector/J / gbase | Hive JDBC（`org.apache.hive.jdbc.HiveDriver`） |
| JDBC URL | `jdbc:mysql://` / `jdbc:gbase://` | `jdbc:hive2://` |
| 默认端口 | 3306 / 5258 | 10000 |
| 列元数据 | `information_schema` | `desc` / `show create table` |
| 表关系元数据 | 支持 | 不支持（Hive 约束不可靠） |
| NAMESPACE | catalog | catalog（数据库） |

GBase 8a 能委托 MySQL 是因为同协议族；Hive 的查询语法与元数据来源都不同，`HiveCatalogs`/`HiveSqlScanner`/`HiveExplain`/`HiveFailures` 独立实现。

### 2. 实现在 `engine.hive`，`@Order(4)`，不改主干

`EngineId.HIVE = "HIVE"`，`HiveEngineSupport` `@Order(4)`，目录稳定为 MYSQL、POSTGRESQL、GBASE_8A、HIVE。ArchUnit 增加 `orchestratorsDoNotDependOnHiveEngine`，让 `datasource`/`execution`/`metadata`/`history`/`export`/`script`/`auth`/`common`/`agentapi` 不得依赖 `engine.hive`。

### 3. 跨集群 metastore 用白名单属性，不硬编码环境 IP

参考实现把测试/生产 metastore 地址硬编码在代码里。本变更改为 Hive 引擎的**白名单属性** `hive.metastore.uris`（可选，默认空），值只允许 `thrift://host:port[,thrift://...]`。既不扩大攻击面，也不再写死环境地址。

### 4. 驱动依赖方案

优先沿用 `service/third-party/gbase` 的 drop-in 模式：`service/third-party/hive/hive-jdbc-standalone.jar` + Maven profile（system scope + fat jar `includeSystemScope`），未放入时服务仍启动、HIVE 测试连接返回脱敏 `CONNECTION_FAILED`。若走 Central，则 `org.apache.hive:hive-jdbc` 固定版本并对 `hadoop-common`、guava、logging 做排除，确保与 Java 8 + Spring Boot 2.7 兼容。

### 5. 验证策略

本机连不到数据源。集成测试只验证目录第 4 项与 `engine=HIVE` 创建持久化（不连真库）。URL 拼装、IPv6 括号、`metric-prefix` 属性白名单、缺驱动分类、`show` 结果映射用单元测试钉死。

前端：MSW 增加 HIVE；动态表单已按描述符渲染；补类型卡片图标与 `hive` 编辑器语言注册。编辑器语言不是 Monaco 原生 `hive` 时按现有回退规则映射（`formatterLanguageFor`）。

## Risks / Trade-offs

- [Hive JDBC / hadoop 传递依赖重、可能与 Spring Boot 冲突] → 优先 drop-in 厂商同款 JAR；否则钉版本 + 排 exclusion，并在 change 验收前跑全量回归。
- [方言扫描做错导致元数据不准] → `show` 指令结果映射用单元测试 + 目录契约测试钉死；不可靠的关系元数据直接标 unavailable。
- [目录顺序/校验带偏既有引擎回归] → `@Order(4)`；省略 engine 仍默认 MYSQL。
- [误把 Kerberos 带进本变更] → 任务明确禁止；Kerberos 是独立 change。

## Migration Plan

1. 前后端同发：目录第 4 项、Hive 表单卡片、`hive` 编辑器语言。已有 MYSQL/PG/GBase 8a 数据源不受影响。
2. 连接真实 Hive 前放入驱动（drop-in 或 Maven 依赖）并重新打包。
3. 无需 Flyway；`sql_data_source.engine` 已存在。
4. 回滚：回退应用版本。已存 `engine=HIVE` 的行在旧版本会 `ENGINE_NOT_SUPPORTED`，符合 fail-closed。

## Open Questions

- 编辑器语言用 `hive` 还是回退成 `sql`？决定：目录声明 `hive`，前端注册 `hive`（无原生时按回退规则映射到 `sql`/`mysql`），保持可编辑。
- 默认端口？决定：10000（HiveServer2 默认）。
