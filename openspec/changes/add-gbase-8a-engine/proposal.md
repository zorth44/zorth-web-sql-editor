## Why

SPI、引擎目录和 PostgreSQL 已经把「第二种关系型引擎」与「schema 当 NAMESPACE」跑通。GBase 应按型号挂族，不能再改主干。本变更只加 GBase 8a，挂在已有 `MYSQL_WIRE` 上，用来验证「同族新产品」只需注册引擎、不必改 `EngineSupport` 或编排层。现场连接必须走南大通用官方 JDBC 包，不能用 Oracle MySQL Connector/J 顶替。

## What Changes

- 注册 `GBASE_8A`：`family=MYSQL_WIRE`，扫描、元数据、失败分类、SSL/属性白名单复用 mysql 族实现；目录身份、展示名、默认端口与 MYSQL 区分。
- JDBC 使用官方 `gbase-connector-java`：URL 方案 `jdbc:gbase://`，驱动类 `com.gbase.jdbc.Driver`。官方 JAR 不在 Maven Central，构建时从 `service/third-party/gbase/gbase-connector-java.jar` 按需引入。
- 目录第三项：`displayName=GBase 8a`、`defaultPort=5258`、`editorLanguage=mysql`、NAMESPACE 仍是数据库，`defaultDatabase` 可选。
- 前端目录出现 GBase 8a 卡片；表单、资源树、编辑器语言由现有描述驱动，不写 8a 专用表单分支。
- 不注册 GBase 8c / 8s / Hive，不抽 mysql 族基类，不改 `EngineSupport`、编排层或 `/databases` 契约。

## Capabilities

### New Capabilities

- `backend-gbase-8a-engine`: GBase 8a 作为挂在 `MYSQL_WIRE` 上的第三个 `EngineSupport` 的注册、目录描述和派发合同。

### Modified Capabilities

- `backend-engine-spi`: 注册表启动后包含 MYSQL、POSTGRESQL、GBASE_8A；未注册引擎仍拒绝写入。
- `backend-engine-catalog`: `GET /api/v1/engines` 返回三项；GBASE_8A 描述 `family=MYSQL_WIRE`、端口 5258、NAMESPACE 为数据库且 `listEndpoint=databases`。
- `backend-data-source-management`: 允许创建/更新 `engine=GBASE_8A`；默认库规则与 MYSQL 相同（可选）。
- `backend-connection-security`: GBASE_8A 组装 `jdbc:gbase://`，SSL 与属性白名单仍走 mysql 族，编排层仍只走 `EngineSupport`。
- `data-source-management`: 类型可选 GBase 8a；表单按目录渲染，默认端口 5258。
- `frontend-sql-editor-workbench`: GBase 8a 资源树与编辑器跟随 MYSQL 描述（数据库 / `mysql` / 反引号）。

## Impact

- 后端：`engine.gbase8a` 新增引擎类（扫描/目录委托 mysql，JDBC 改写为官方方案）、`EngineId.GBASE_8A`、`@Order(3)`、ArchUnit 禁止主干依赖 `engine.gbase8a`。
- API：目录多一项 GBASE_8A；CRUD/测试/元数据/执行路径不变。
- 前端：MSW 目录、类型卡片图标。
- 部署：连接真实 8a 前须放入官方 `gbase-connector-java.jar`；不加 GBase Testcontainers 镜像。
