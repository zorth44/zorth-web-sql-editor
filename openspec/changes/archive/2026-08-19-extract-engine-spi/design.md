## Context

当前服务只支持目标 MySQL。`sql_data_source.engine` 已持久化，但 `ConnectionConfiguration` 组装时丢掉该字段；JDBC URL、属性白名单、SSL、失败分类、catalog 复位、元数据和 SQL 扫描都写死在主干类（`JdbcConfigurationBuilder`、`JdbcPropertyValidator`、`ConnectionFailureClassifier`、`ConnectionUse`、`MysqlMetadataService`、`SqlStatementScanner`）。前端类型 `Engine = 'MYSQL'`，执行/历史 JSON 使用 `mysqlErrorCode`。

后续还要加 PostgreSQL、Hive、GBase。本变更只抽插件面并把 MySQL 收成唯一实现，对外除错误码字段改名外行为不变。约束仍是 Java 8、Spring Boot 2.7、单实例、目标连接走现有 CIDR 与动态 Hikari 池。

## Goals / Non-Goals

**Goals:**

- 让数据源 CRUD、连接池、执行、导出、历史只依赖 `EngineSupport`，不再拼 `jdbc:mysql://` 或依赖 MySQL 错误码类名。
- 把现有 MySQL 行为原样迁入 `MYSQL` 实现：URL、白名单、SSL、系统库、`SHOW CREATE TABLE`、反引号扫描、catalog 复位。
- `engine` 贯穿保存后的连接配置；未注册引擎拒绝写入。
- 将 `mysqlErrorCode` / `mysql_error_code` 改为厂商无关的 `vendorErrorCode` / `vendor_error_code`。
- 用 ArchUnit 锁住「主干不直接依赖 MySQL 实现类」。

**Non-Goals:**

- 不注册 PostgreSQL / Hive / GBase，不加对应 JDBC 驱动或 Testcontainers。
- 不提供 `GET /api/v1/engines`，不改数据源表单为动态字段（类型仍只读 MySQL）。
- 不改资源树契约（仍是 databases → tables），不引入 PARTITION / schema 层。
- 不把协议族（mysql wire / postgres wire）抽成可复用基类；本阶段只有一个实现，族字段可留在接口上供后续使用，但不做继承树。
- 不改元数据库（Flyway 仍针对服务自己的 MySQL）。
- 不改变网段策略、池容量、执行超时、单语句限制。

## Decisions

### 1. 单一 `EngineSupport` 接口，Spring 注册表按 id 查找

在 `com.bocsoft.sqleditor.engine` 放契约：`EngineSupport`、`EngineId`（本阶段常量 `MYSQL`）、`EngineRegistry`。MySQL 实现放在 `com.bocsoft.sqleditor.engine.mysql`，由 Spring 收集 `List<EngineSupport>` 建 id → 实现的不可变表。

`EngineSupport` 覆盖本阶段主干真正分发的能力：校验连接字段/JDBC 属性、拼 `JdbcTarget`、分类连接失败、选中/复位命名空间（MySQL 即 catalog）、列出 namespace/表/表详情、拆分并要求单语句、标识符引号。能力开关（默认库是否必填、能否同连接切换 namespace）做成只读方法，MySQL 返回与今天一致的值，避免后续引擎改主干。

拆成 JDBC / Metadata / Dialect 三个接口会让唯一实现立刻散成三套 bean，收益要等第二种引擎才出现，因此本阶段不拆。

### 2. `SavedDataSource` 与 `ConnectionConfiguration` 携带 `engine`

`DataSourceService.configuration(...)` 写入记录上的 `engine`。借池、执行、元数据、导出从 `SavedDataSource` 取 id，经 `EngineRegistry.require(id)` 得到插件；找不到则 `400 VALIDATION_FAILED` 或对已存脏数据 `422`/`500` 安全失败且不打开目标连接。

未保存连接测试的请求体今天没有 `engine`。本阶段测试路径在缺省时使用 `MYSQL`（唯一注册引擎），避免改 test DTO 形状。创建/更新仍校验请求里的 `engine` 必须已注册。

### 3. 把现有 MySQL 类迁到实现包，主干只留编排

| 现有类 | 迁入后 |
| --- | --- |
| `JdbcConfigurationBuilder` / `JdbcPropertyValidator` | `engine.mysql` 实现细节 |
| `ConnectionFailureClassifier` 中的 MySQL SQLState/错误码 | MySQL 引擎方法 |
| `MysqlMetadataService` 的 JDBC 元数据与 `SHOW CREATE TABLE` | MySQL 引擎方法；`MetadataController` 只做鉴权、分页参数和分发 |
| `SqlStatementScanner` 的反引号/`#` 状态机 | MySQL 引擎的扫描器；`SqlExecutionService` 向引擎要单语句 |
| `ConnectionUse` 的 catalog 复位 | MySQL `restoreSession`；通用 try/finally 还池仍留在 `datasource.connection`，复位策略由引擎提供 |

网络解析、CIDR、Hikari 池生命周期、凭证加密留在 `datasource.connection`，它们与方言无关。

`SqlStatementClassifier`（SELECT/DML/DDL）本阶段仍留在 `execution`：它按首词分类，MySQL 的 SHOW/DESC/REPLACE 行为不变。等第二种方言再下沉到引擎。

前端 `web/src/sql-editor/sql.ts` 本阶段不改扫描器（仍按 MySQL 拆语句）；后端仍是多语句拒绝的权威。动态编辑器 language 属于后续引擎目录变更。

### 4. 错误码字段改名为 `vendorErrorCode`

执行 `422 SQL_EXECUTION_FAILED` 的 `details`、历史详情 JSON、Java 记录与 MyBatis 映射一律使用 `vendorErrorCode`。Flyway 新增迁移将 `sql_execution_history.mysql_error_code` 重命名为 `vendor_error_code`。前端 `contracts.ts`、MSW、错误展示同步改名，不再读写 `mysqlErrorCode`。

不保留旧字段别名：产品尚未多客户端并行依赖该字段，双字段会把 MySQL 命名继续漏进契约。

MySQL 实现仍把 `SQLException.getErrorCode()` 填进 `vendorErrorCode`，数值与今天相同。

### 5. ArchUnit 禁止主干依赖 `engine.mysql`

在现有 `ArchitectureTest` 增加规则：`datasource`（除通过接口）、`execution`、`metadata`、`history`、`export`、`common`、`auth` 不得依赖 `..engine.mysql..`。`engine` 包不得依赖 `execution` / `history`。允许 `engine.mysql` 使用 JDBC、`datasource.connection` 的网络/目标类型、以及 metadata/execution 的 API DTO（或把引擎输出做成 engine 包内的中立 DTO 再由 controller 映射；优先复用现有 `metadata.api` / `execution` 扫描结果，避免本阶段再复制一层）。

若循环依赖出现，将元数据 DTO 视为共享 API 包，引擎实现依赖 API 而不依赖 `MetadataController`。

### 6. 标识符与「database」对外名称不变

公开 API 路径与字段仍叫 `database` / `defaultDatabase`。引擎内部方法使用 namespace，MySQL 将其映射为 catalog。这样后续 PostgreSQL 可以把 schema 填进同一套 databases API，而不在本变更扩大树层级。

## Risks / Trade-offs

- [一次抽太多类，回归面大] → MySQL 实现必须保持现有单测与 Testcontainers 行为；本变更不改网段、池大小、超时。抽取后先跑 `NetworkAndJdbcSecurityTest`、`MysqlMetadataService` 测试、`BackendIntegrationTest`。
- [未保存测试请求没有 engine] → 缺省 MYSQL；第二种引擎落地时再给 test DTO 加 `engine`。
- [ArchUnit 过严导致引擎无法复用 DTO] → 允许实现依赖 `*.api` 包，禁止依赖 Controller/Service。
- [重命名错误码字段让旧前端/Mock 立刻不兼容] → 本仓库前后端同发；MSW 与类型一并改。
- [Flyway 列重命名在有历史数据的环境需要停机窗口] → 单列 rename，无回填逻辑；回滚用反向 rename 迁移，不支持双写。
- [接口偏胖，后续仍可能再拆] → 接受；第二种引擎证明哪些方法成族后再抽 `MysqlFamily` 基类。

## Migration Plan

1. 合入 SPI 与 MySQL 迁入，行为对齐现有测试。
2. 部署前执行 Flyway 列重命名；同时发布前端（`vendorErrorCode`）。
3. 回滚：回退应用版本，并准备将 `vendor_error_code` 改回 `mysql_error_code` 的反向迁移（仅在该 Flyway 版本已执行时需要）。

## Open Questions

- 未保存连接测试是否在本阶段就给请求体加上可选 `engine`（缺省 `MYSQL`）。设计选择：不加，避免扩大表单契约。
- `SqlStatementClassifier` 是否一并迁入 MySQL 引擎。设计选择：本阶段不迁。
