## Why

现场已有可用的 Hive 数据源（参考实现 `bddf-public-service` 的 `Constants.DBTYPE_HIVE = "3"`，走 `DbToolsUtils.getConnection()`：用户名/密码 + `jdbc:hive2://host:port/db`）。这是三种 Hive 连接方式里**唯一完全契合现有引擎模型**的一种（host/port/username/password + CIDR + 动态 Hikari 池），先把它单独落地，用来证明「新协议族」也能只注册引擎、不改主干；同时为后续 Kerberos/ZooKeeper 服务发现的两个引擎（`HIVE_KERBEROS`、`ICEBERG`）提供可委托的 `HiveEngineSupport` 与 Hive JDBC 依赖。

## What Changes

- 注册 `HIVE`：`@Order(4)`、`family=HIVE_WIRE`、`editorLanguage=hive`、`identifierQuote` 反引号、`defaultPort=10000`、`defaultNamespaceRequired=false`、资源树第一层 `NAMESPACE` 文案「数据库」、`defaultDatabase` 可选。
- 独立实现 Hive 方言目录/扫描/失败分类：`listDatabases` 走 `show databases`，`listTables` 走 `use <db>` + `show tables`，`tableDetail` 走 `desc <table>` + `show create table`；`supportsRelationshipMetadata()=false`、`tableConstraints` 返回 unavailable。**不委托 MySQL**（MySQL 走 `information_schema`，方言不同）。
- JDBC 拼 `jdbc:hive2://<ip>:<port>/<db>`；参考里的跨集群 `hive.metastore.uris` 作为**白名单属性**（可选，默认空）。
- 引入 Hive JDBC 驱动依赖；优先按 `service/third-party/gbase` 的 drop-in 模式放入参考同款驱动 JAR，否则用 Central 版本并排除冲突传递依赖。
- 前端目录出现 Hive 卡片，编辑器语言注册 `hive`；表单与资源树由现有描述符驱动，不写 Hive 专用分支。
- 不注册 HIVE_KERBEROS / ICEBERG，不引入 Kerberos/ZooKeeper，不改 `EngineSupport`、`DynamicPoolManager` 或编排层。

## Capabilities

### New Capabilities

- `backend-hive-engine`: Hive（dbType 3，普通 JDBC）作为独立 `EngineSupport` 的注册、Hive 方言目录/扫描、JDBC 方案与失败分类合同。

### Modified Capabilities

- `backend-engine-spi`: 注册表启动后包含 MYSQL、POSTGRESQL、GBASE_8A、HIVE；未注册引擎仍拒绝写入。
- `backend-engine-catalog`: `GET /api/v1/engines` 返回四项；HIVE 描述 `family=HIVE_WIRE`、端口 10000、`editorLanguage=hive`、NAMESPACE 为数据库。
- `backend-data-source-management`: 允许创建/更新 `engine=HIVE`。
- `backend-connection-security`: 新增 Hive JDBC 组装合同（`jdbc:hive2://`、属性白名单、IPv6 括号）。
- `data-source-management`: 类型可选 Hive；表单按目录渲染。
- `frontend-sql-editor-workbench`: Hive 资源树与编辑器语言跟随目录描述。

## Impact

- 后端：`engine.hive` 新增引擎包（`HiveEngineSupport`/`HiveJdbc`/`HiveCatalogs`/`HiveSqlScanner`/`HiveExplain`/`HiveFailures`）、`EngineId.HIVE`、`@Order(4)`、ArchUnit 禁止主干依赖 `engine.hive`、`pom.xml` 增 Hive JDBC 驱动。
- API：目录多一项 HIVE；CRUD/测试/元数据/执行路径不变。
- 前端：MSW 目录、类型卡片图标、`hive` 编辑器语言注册。
- 部署：连接真实 Hive 前须放入 Hive JDBC 驱动（drop-in 或 Maven 依赖）。
