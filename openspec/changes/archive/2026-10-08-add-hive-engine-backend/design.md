## Context

阶段 0 抽 `EngineSupport`，阶段 1/2 用目录驱动表单与 NAMESPACE 树，阶段 3 用 GBase 8a 证明「同族新产品只挂族」。现注册表为 MYSQL、POSTGRESQL、GBASE_8A（见已归档的 `add-gbase-8a-engine` 与当前 `backend-engine-*` 规范）。

参考实现 `bddf-public-service` 的普通 Hive 连接极简：`DbToolsUtils.getConnection()` 对 `DBTYPE_HIVE="3"` 拼 `jdbc:hive2://<ip>:<port>/<db>`，`DriverManager.getConnection(url, username, pwd)`；跨集群时追加 `?hive.metastore.uris=thrift://...`；元数据用 `show databases / show tables / desc / show create table`。

约束不变：Java 8、Spring Boot 2.7、单实例、目标连接走现有 CIDR 与动态 Hikari 池。服务元数据库仍是 Flyway 管理的 MySQL。

本变更只覆盖后端。前端由独立 change `add-hive-engine-frontend` 承担，双方仅通过 `GET /api/v1/engines` 目录契约耦合。

## Goals / Non-Goals

**Goals:**

- 注册 `HIVE`（dbType 3）：连接、测试、资源树、执行、导出都走该实现，且走现有池与 CIDR。
- `family=HIVE_WIRE`；Hive 方言目录与扫描（metastore `show` 指令），不复用 MySQL 的 `information_schema`。
- JDBC `jdbc:hive2://`；IPv6 加括号；跨集群死表地址改成白名单属性而非硬编码。
- 目录：`displayName=Hive`、`defaultPort=10000`、`editorLanguage=hive`、NAMESPACE 文案「数据库」、`defaultDatabase` 可选。
- 主干（`EngineSupport`、编排层、`DynamicPoolManager`）零改动。
- MYSQL / POSTGRESQL / GBASE_8A 回归保持现有行为。
- 先写测试后写实现：URL/IPv6/白名单/`show`/`desc` 映射/缺驱动分类用单元测试钉死。

**Non-Goals:**

- 不注册 HIVE_KERBEROS / ICEBERG，不引入 Kerberos / ZooKeeper / keytab / UGI（见后续 change `add-kerberos-zk-discovery`、`add-iceberg-engine`）。
- 不抽公共 Hive 基类前先做 Kerberos；本变更只交付普通 JDBC。
- 不把厂商 JAR 提交进仓库，不从非官方源下载或伪造 stub。
- 不改 `EngineSupport` 方法签名或 `DynamicPoolManager`。
- 不做前端改动（见 `add-hive-engine-frontend`）。
- 不新增集成测试；不连真实数据源或 Testcontainers。

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

### 5. 验证策略（后端，先测后码）

本机连不到数据源，且本 change 不做集成测试。URL 拼装、IPv6 括号、`hive.metastore.uris` 属性白名单、缺驱动分类、`show`/`desc` 结果映射用**单元测试**钉死；目录第 4 项、`engine=HIVE` 创建持久化用现有单元测试结构（`EngineCatalogTest`、`EngineRegistryTest`、`DataSourceServiceTest`）覆盖，不依赖真库。

## Risks / Trade-offs

- [Hive JDBC / hadoop 传递依赖重、可能与 Spring Boot 冲突] → 优先 drop-in 厂商同款 JAR；否则钉版本 + 排 exclusion，并在合并前跑全量单测回归。
- [方言扫描做错导致元数据不准] → `show` 指令结果映射用单元测试 + 目录契约测试钉死；不可靠的关系元数据直接标 unavailable。
- [目录顺序/校验带偏既有引擎回归] → `@Order(4)`；省略 engine 仍默认 MYSQL。
- [误把 Kerberos 带进本变更] → 任务明确禁止；Kerberos 是独立 change。
- [既有集成测试的 items.length()=3 断言会变红] → 本 change 不改集成测试（用户决定不做集成测试）；影响记录在 tasks/impact，留待后续处理。

## Migration Plan

1. 后端先发：目录第 4 项。已有 MYSQL/PG/GBase 8a 数据源不受影响；前端未跟进时仅多出一项 HIVE，旧前端按目录渲染仍可工作。
2. 连接真实 Hive 前放入驱动（drop-in 或 Maven 依赖）并重新打包。
3. 无需 Flyway；`sql_data_source.engine` 已存在。
4. 回滚：回退应用版本。已存 `engine=HIVE` 的行在旧版本会 `ENGINE_NOT_SUPPORTED`，符合 fail-closed。

## Open Questions

- 编辑器语言用 `hive` 还是回退成 `sql`？决定：目录声明 `hive`，前端注册 `hive`（无原生时按回退规则映射到 `sql`/`mysql`），保持可编辑。前端侧在 `add-hive-engine-frontend` 落实。
- 默认端口？决定：10000（HiveServer2 默认）。
