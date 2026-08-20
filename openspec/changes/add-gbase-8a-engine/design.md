## Context

阶段 0 抽出 `EngineSupport`，阶段 1 用目录驱动表单和 NAMESPACE 树，阶段 2 用 PostgreSQL 验证「schema 当第一层」。注册表现有 MYSQL 与 POSTGRESQL。GBase 不能当第四种协议来改主干：8a 走 MySQL 协议，8c 走 PostgreSQL 协议，8s 才需要新的 Informix 族。本变更只加 8a，用来证明同族新产品只注册引擎、不改 SPI。

约束不变：Java 8、Spring Boot 2.7、单实例、目标连接走现有 CIDR 与动态 Hikari 池。服务元数据库仍是 Flyway 管理的 MySQL。

## Goals / Non-Goals

**Goals:**

- 注册 `GBASE_8A`：连接、测试、资源树、执行、导出都走该实现。
- `family=MYSQL_WIRE`；SSL / 属性白名单 / 扫描 / 目录 / 失败分类与 MYSQL 相同。
- JDBC 使用官方 `gbase-connector-java`：`jdbc:gbase://`、`com.gbase.jdbc.Driver`。
- 目录：`displayName=GBase 8a`、`defaultPort=5258`、`editorLanguage=mysql`、NAMESPACE 文案「数据库」、`defaultDatabase` 可选。
- 主干（`EngineSupport`、编排层、`/databases` 适配）零改动。
- MYSQL / POSTGRESQL 回归保持现有行为。

**Non-Goals:**

- 不注册 GBase 8c / 8s / Hive，不加 PARTITION。
- 不抽 `MysqlFamily` 基类，不改 `EngineSupport` 方法。
- 不把官方 JAR 提交进仓库，不从非官方源下载或伪造 stub。
- 不把 `/databases`、`defaultDatabase` 改名。
- 不加 GBase Testcontainers 镜像。

## Decisions

### 1. 按型号挂族，8a 复用 mysql 扫描/目录，JDBC 用官方包

| 概念 | MySQL | GBase 8a |
| --- | --- | --- |
| `engine` id | MYSQL | GBASE_8A |
| `family` | MYSQL_WIRE | MYSQL_WIRE |
| JDBC 驱动 | MySQL Connector/J | 官方 `gbase-connector-java`（`com.gbase.jdbc.Driver`） |
| JDBC URL | `jdbc:mysql://` | `jdbc:gbase://` |
| 默认端口 | 3306 | 5258 |
| 树第一层 NAMESPACE | catalog | catalog |
| `defaultDatabase` | 可选 | 可选 |
| 扫描 / SSL / 属性 | mysql 族 | 同一套白名单与 SSL 标志 |

GBase 8a 的官方驱动基于 MySQL Connector/J，协议同族，但 URL 方案与驱动类是厂商自己的。8a 实现委托 `MysqlEngineSupport` 拼属性与 SSL，再把 `jdbc:mysql://` 改写成 `jdbc:gbase://`，并在组装时 `Class.forName("com.gbase.jdbc.Driver")`。官方 JAR 不在 Maven Central：放到 `service/third-party/gbase/gbase-connector-java.jar` 后，文件存在即激活 Maven profile `gbase-official-jdbc`（system scope + fat jar `includeSystemScope`）。未放入时服务仍启动，GBASE_8A 测试连接返回脱敏的 `CONNECTION_FAILED`。

### 2. 实现放在 `engine.gbase8a`，委托 mysql 族，不改主干

`EngineId.GBASE_8A = "GBASE_8A"`。`Gbase8aEngineSupport` `@Order(3)`，目录稳定为 MYSQL、POSTGRESQL、GBASE_8A。

8a 实现 **委托** 已有 `MysqlEngineSupport` 的扫描、目录、属性校验和失败分类，只覆盖 `id()`、`descriptor()`、`buildJdbc()`（官方方案）、`jdbcUrlWithoutNamespace()`、缺驱动时的失败分类。不复制扫描器，不改 `EngineSupport`，不让 `datasource` / `execution` / `metadata` 出现 `if (GBASE)`，也不在主干设置 `driverClassName`。

ArchUnit：主干不得依赖 `engine.gbase8a`，与 mysql / postgres 规则并列。`engine.gbase8a` 可以依赖 `engine.mysql`（同族委托）。

### 3. 不改 MYSQL 描述，8a 自带一份目录身份

MYSQL 的 `displayName` / `defaultPort=3306` 保持不动。GBASE_8A 描述与 MYSQL 同构，仅 id、展示名、端口默认值不同。`propertyFields` 与 MYSQL 白名单一致，便于切类型时丢掉 PG 键、保留 mysql 族键。

### 4. 验证策略

没有公开的 GBase 8a Testcontainers 镜像，也不能再用 MySQL 容器冒充 8a 的官方驱动握手。集成测试只验证目录第三项与 `engine=GBASE_8A` 创建持久化。端口 5258、`jdbc:gbase://`、IPv6 括号、属性白名单、缺官方驱动的分类用单元测试钉死。

前端：MSW 增加 GBASE_8A；动态表单已按描述渲染；补类型卡片图标。编辑器语言仍是 `mysql`，无需新 Monaco 语言。

## Risks / Trade-offs

- [官方 JAR 不在 Central] → 本地/部署 drop-in 路径；未放入时 MYSQL/PG 不受影响，8a 连接失败并提示缺驱动。
- [抽族基类把 MYSQL 实现搅进重构] → 8a 只委托，不抽 `MysqlFamily`。
- [目录顺序/校验带偏 MYSQL 回归] → `@Order(3)`；省略 engine 仍默认 MYSQL。
- [误加 8c/8s] → 任务明确禁止；8c 应挂 `POSTGRES_WIRE`，8s 才新族。

## Migration Plan

1. 前后端同发：目录第三项、GBase 8a 表单卡片。已有 MYSQL/PG 数据源不受影响。
2. 连接真实 8a 前放入官方 `gbase-connector-java.jar` 并重新打包。
3. 无需 Flyway；`sql_data_source.engine` 已存在。
4. 回滚：回退应用版本。已存 `engine=GBASE_8A` 的行在旧版本会 `ENGINE_NOT_SUPPORTED`，符合 fail-closed。

## Open Questions

- 是否用 `jdbc:gbase://` 与官方驱动。决定：是。扫描/目录仍挂 mysql 族，JDBC 方案与驱动类用官方包。
- 默认端口是否 5258。决定：是（GBase 8a 集群常见端口）；用户仍可改。
