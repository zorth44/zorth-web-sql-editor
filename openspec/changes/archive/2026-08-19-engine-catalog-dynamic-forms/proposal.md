## Why

阶段 0 已经把目标库能力收进 `EngineSupport`，但前端仍把 MySQL 写死在表单、JDBC 参数、列表类型文案、资源树「数据库」文案和 Monaco `mysql` 语言里。不先把目录和渲染面拆开，第二种引擎一上来就会同时改 Vue 和回归 MySQL。

## What Changes

- 新增已认证 `GET /api/v1/engines`：由注册表汇总每个 `EngineSupport` 的目录描述（展示名、族、默认端口、连接字段、JDBC 表单项、资源树层级、编辑器 language、能力开关）。本阶段注册表仍只有 `MYSQL`。
- 数据源创建/编辑表单改为目录驱动的渲染器：类型从目录选择，端口/SSL/默认命名空间/JDBC 控件按选中引擎生成；校验规则来自目录，不再写死 MySQL 键。
- 未保存连接测试请求体增加可选 `engine`；前端始终提交当前选中引擎；缺省仍派发 `MYSQL`，避免旧客户端立刻失败。
- 资源树产品模型改为通用节点 `NAMESPACE` / `TABLE` / `VIEW`。MySQL 的第一层仍走现有 `GET .../databases`（适配层），标签和过滤文案来自该引擎目录；工作区 URL、执行请求仍使用 `database` 字段名。
- SQL 编辑器 language 取自当前数据源引擎的目录，不再在组件里写死 `mysql`。
- 不注册 PostgreSQL / Hive / GBase，不引入 `PARTITION` 层，不新增通用 `/resources` 树 API，不把 `defaultDatabase` / `/databases` 改名。

## Capabilities

### New Capabilities

- `backend-engine-catalog`: 引擎目录 API 的形状、鉴权、以及「描述由插件提供、主干只汇总」的合同。

### Modified Capabilities

- `backend-engine-spi`: `EngineSupport` 必须发布目录描述（展示、表单、树层级、language、能力），供目录 API 和前端渲染使用。
- `backend-data-source-management`: 创建/更新/测试使用的 `engine` 必须是目录中的已注册 id；测试体可带 `engine`。
- `backend-connection-security`: 未保存测试在提交 `engine` 时按该引擎派发，省略时仍默认 `MYSQL`。
- `backend-sql-editor`: `/databases` 作为 `NAMESPACE` 列表的适配接口；`DatabaseItem` 增加 `kind=NAMESPACE`。
- `data-source-management`: 列表类型文案和表单控件由引擎目录驱动，不再只读 MySQL。
- `frontend-sql-editor-workbench`: 资源树按 `NAMESPACE` 理解第一层，标签来自目录；编辑器 language 来自目录。

## Impact

- 后端：`EngineSupport` 目录描述、`EngineRegistry` 列表、新 `EngineCatalogController`、连接测试 DTO、`DatabaseItem`、OpenAPI、文档中的引擎目录段落。
- API：新增 `GET /api/v1/engines`；测试 JSON 可含 `engine`；`DatabaseItem` 增加 `kind`。数据源 CRUD 字段名不变。
- 前端：`DataSourceForm` / `validation.ts` / `model.ts`、数据源列表、`ResourceBrowser`、Monaco 语言、MSW。
- 不改授权服务、元数据库表结构、网段策略、连接池容量、执行超时。不引入第二种 JDBC 驱动。
