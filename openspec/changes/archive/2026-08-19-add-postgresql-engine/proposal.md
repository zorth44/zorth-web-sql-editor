## Why

SPI 和引擎目录已经能按 `engine` 分发表单、资源树和 JDBC，但注册表仍只有 MYSQL。不先用第二种关系型引擎把「schema 当 NAMESPACE 第一层」跑通，后面 Hive/GBase 仍会在主干上猜树语义。本变更只加 PostgreSQL，用来验证第二个实现和现有 `databases` 适配层。

## What Changes

- 注册 `POSTGRESQL` 引擎：JDBC URL / SSL / 属性白名单 / 失败分类 / 语句扫描 / 元数据均在 `engine.postgres`，主干只走 `EngineSupport`。
- `defaultDatabase` 仍是钉死的 PostgreSQL **数据库**（进 JDBC URL）；资源树第一层 NAMESPACE 列出 **schema**，继续走 `GET .../databases`，`?database=` 表示 schema 名。
- 目录描述：`family=POSTGRES_WIRE`、`defaultPort=5432`、`editorLanguage=pgsql`、NAMESPACE 文案为「模式」、`defaultDatabase` 必填。
- 未保存测试提交 `engine=POSTGRESQL` 时派发 PG；省略 `engine` 仍默认 MYSQL。
- 前端：目录出现 PostgreSQL；Monaco 注册 `pgsql`；标识符引号随引擎（PG 用双引号）；资源树标签来自 PG 描述。
- 引入 PostgreSQL JDBC 驱动与 Testcontainers。不注册 Hive / GBase，不引入 PARTITION，不把 `/databases` 改名。

## Capabilities

### New Capabilities

- `backend-postgresql-engine`: PostgreSQL 作为第二个 `EngineSupport` 的连接、schema 元数据、扫描和失败分类合同。

### Modified Capabilities

- `backend-engine-spi`: 注册表启动后包含 MYSQL 与 POSTGRESQL；未注册引擎仍拒绝写入。
- `backend-engine-catalog`: `GET /api/v1/engines` 返回两项；POSTGRESQL 描述将 NAMESPACE 标为模式且 `listEndpoint=databases`。
- `backend-data-source-management`: 允许创建/更新 `engine=POSTGRESQL`；PG 要求 `defaultDatabase`。
- `backend-connection-security`: PG JDBC URL、SSL、属性白名单与失败分类由 PG 引擎提供。
- `backend-sql-editor`: PG 数据源的 `/databases` 列出 schema；表/执行的 `database` 参数是 schema 名。
- `data-source-management`: 类型可选 PostgreSQL；PG 表单按目录渲染；默认数据库必填。
- `frontend-sql-editor-workbench`: PG 资源树第一层为模式；编辑器使用 `pgsql`；插入 SQL 使用双引号标识符。

## Impact

- 后端：`engine.postgres` 实现包、`EngineId.POSTGRESQL`、ArchUnit 禁止主干依赖该包、PostgreSQL 驱动、PG Testcontainers。
- API：目录多一项 POSTGRESQL；CRUD/测试/元数据/执行路径不变。
- 前端：MSW 目录、动态表单、Monaco `pgsql`、资源树文案与引号。
- 不改授权服务、元数据库（Flyway 仍是服务自己的 MySQL）、网段策略、池容量、执行超时。
