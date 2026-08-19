## Context

阶段 0 已落地 `EngineSupport` + `EngineRegistry`，运行时按 `engine` 分发 JDBC、元数据、扫描和失败分类。注册表仍只有 `MYSQL`。对外缺口在展示面：

- 数据源表单把类型做成只读「MySQL」，JDBC 键写死在 `DataSourceForm.vue` / `validation.ts` / `JdbcProperties`。
- 创建/更新硬编码 `engine: 'MYSQL'`；未保存测试体没有 `engine`，后端缺省 MYSQL。
- 资源树文案和过滤都是「数据库」，内部节点 kind 也叫 `database`；元数据仍走 `GET .../databases` 与 `?database=`。
- Monaco 只注册并使用 `mysql`。

约束不变：Java 8、Spring Boot 2.7、单实例、目标连接走现有 CIDR 与动态 Hikari 池。本变更仍不注册第二种引擎。

## Goals / Non-Goals

**Goals:**

- 用 `GET /api/v1/engines` 把每个已注册引擎的展示、表单、树层级、编辑器 language 交给前端渲染。
- 数据源表单变成目录渲染器；MySQL 控件与校验结果与现在一致。
- 资源树以 `NAMESPACE` 为第一层产品概念，MySQL 继续调用现有 databases/tables 接口。
- 编辑器 language 取自当前数据源引擎目录。
- 未保存测试可携带 `engine`；省略时行为与阶段 0 相同。

**Non-Goals:**

- 不注册 PostgreSQL / Hive / GBase，不加对应驱动或 Testcontainers。
- 不把 `/databases` 改成 `/namespaces`，不把 `defaultDatabase` / URL `database` 改名。
- 不引入通用 `/resources` 树，不加 `PARTITION` 层。
- 不把前端 `sql.ts` 扫描器做成按引擎切换（仍按 MySQL 拆语句；后端仍是多语句拒绝的权威）。
- 不抽协议族基类，不改网段、池容量、执行超时。

## Decisions

### 1. 目录由 `EngineSupport.descriptor()` 提供，主干只汇总

在 `engine` 包增加不可变 `EngineDescriptor`（id、displayName、family、defaultPort、editorLanguage、capabilities、connectionFields、propertyFields、resourceTree）。每个 `EngineSupport` 返回自己的描述；`EngineRegistry.descriptors()` 按注册顺序给出列表。

`GET /api/v1/engines`（已认证即可，SQL 工作区也要读 language 和树标签）返回 `{ items: EngineDescriptor[] }`。不按产品过滤：目录是服务能力，不是产品数据。本阶段 `items` 恰好一项 `MYSQL`。

不把 JSON 放在 `resources/*.json`：表单白名单必须和 `MysqlJdbc.validateProperties` 同源，描述与实现同包更不容易漂。

### 2. 连接字段 `name` 对齐现有 JSON，用 `kind` 表达语义

公共列不改。目录里的 `connectionFields[].name` 必须是现有请求字段：`host`、`port`、`username`、`password`、`defaultDatabase`、`sslMode`、`connectTimeoutSeconds`。`kind` 取稳定枚举：`HOST` / `PORT` / `USERNAME` / `PASSWORD` / `DEFAULT_NAMESPACE` / `SSL_MODE` / `TIMEOUT`。

前端按 `widget`（`TEXT` / `NUMBER` / `PASSWORD` / `SELECT`）渲染，按 `kind` 套少量通用规则（Host 禁止带协议、密码创建必填/编辑可空）。`DEFAULT_NAMESPACE` 的 label 对 MYSQL 仍是「默认数据库」，字段名仍是 `defaultDatabase`。

`propertyFields` 描述 `properties` 袋子：MYSQL 仍是现在的五个键及取值。未出现在目录里的键不得画控件、不得提交；后端白名单仍是最终闸门。

Hive 的 principal / SASL、GBase 额外 JDBC 键以后只加 `propertyFields`，不加列。本阶段 MYSQL 描述不得包含这些键。

示例（MYSQL，字段集合与今天一致）：

```json
{
  "id": "MYSQL",
  "displayName": "MySQL",
  "family": "MYSQL_WIRE",
  "defaultPort": 3306,
  "editorLanguage": "mysql",
  "capabilities": {
    "defaultNamespaceRequired": false,
    "canSwitchNamespaceOnConnection": true
  },
  "connectionFields": ["host", "port", "username", "password", "defaultDatabase", "sslMode", "connectTimeoutSeconds"],
  "propertyFields": ["serverTimezone", "characterSetResults", "zeroDateTimeBehavior", "tinyInt1isBit", "sendFractionalSeconds"],
  "resourceTree": [
    { "kind": "NAMESPACE", "label": "数据库", "filterLabel": "筛选数据库", "listEndpoint": "databases" },
    { "kind": "TABLE", "label": "表", "parentKind": "NAMESPACE" },
    { "kind": "VIEW", "label": "视图", "parentKind": "NAMESPACE" }
  ]
}
```

`listEndpoint: "databases"` 把产品层 `NAMESPACE` 钉在现有接口上。本阶段前端只实现 `NAMESPACE` + `TABLE` / `VIEW`；遇到未知 `kind` 跳过且不崩溃，为后续 PARTITION 留位置但不实现。

### 3. 动态表单：先拉目录，再填默认值

创建页：`GET /api/v1/engines` 成功后，默认选中唯一（或列表第一项）引擎，用其 `defaultPort`、SSL 缺省、`propertyFields.defaultValue` 初始化表单（MYSQL 仍是 3306 / PREFERRED / `serverTimezone=Asia/Shanghai`）。类型控件是 `<select>`，即使只有 MYSQL。

编辑页：目录 + 详情都到齐后再渲染；`engine` 以详情为准；切换引擎（本阶段无法真正换到另一种库）会重置端口缺省和 properties 为该引擎默认值，避免把 MYSQL 的 JDBC 键带到别的引擎。名称、描述、host、username 不属于引擎私有项，切换时保留。

`DataSourceFormModel.engine` 改为目录 id（TypeScript `engine: string`）。创建/更新/测试映射使用表单上的 `engine`，不再写死 `'MYSQL'`。列表「类型」列显示 `displayName`，找不到目录时回退显示原始 id。

共享 chrome（名称、描述）仍由页面提供，不进引擎目录。

### 4. 未保存测试增加可选 `engine`

`ConnectionRequest` 增加可选 `engine`。校验：有值则必须已注册；空/缺省则与阶段 0 相同，派发 MYSQL。未知字段（含 `productId`）仍拒绝。

前端测试新表单和未保存编辑时始终带上当前 `engine`。无 body 的已保存测试仍用库里的 `engine`。

这比「测试体强制 engine」兼容更好：阶段 0 的集成测试和任何旧客户端不必立刻改 payload。

### 5. 资源树：NAMESPACE 概念，databases 适配

不改路径和查询参数：

| 产品概念 | 现有接口 |
| --- | --- |
| 列出 NAMESPACE | `GET /api/v1/data-sources/{id}/databases` |
| 列出表/视图 | `GET .../tables?database=` |
| 表详情 | `GET .../table-detail?database=&table=` |
| 工作区选中项 | URL `database`、执行/历史 JSON `database` |

`DatabaseItem` 增加 `"kind": "NAMESPACE"`（只加字段，不改 `name`）。`TableItem` 继续用 `type=TABLE|VIEW` 和 `database` 字段（值为父 NAMESPACE 名）。

前端资源树：

- 数据源子节点按该源 `engine` 的 `resourceTree` 渲染；MYSQL 第一层仍是 databases 列表。
- 侧栏过滤、空态、aria 使用 NAMESPACE 的 `label` / `filterLabel`，不再写死「数据库」。
- 选中 NAMESPACE 仍调用现有 `select-connection(dataSourceId, database)`。
- 内部变量可以改名为 namespace，对外事件和 URL 保持 `database`。

本阶段 MYSQL 的可见文案应与现在相同（「数据库」「表」「视图」），行为回归用现有 ResourceBrowser 测试即可。

### 6. 编辑器 language 来自目录

`SqlMonacoEditor` 增加 `language`（来自绑定数据源引擎的 `editorLanguage`）。未绑定数据源或目录未加载时用 `mysql`（本阶段唯一已注册语言）。本阶段仍只 `import` Monaco MySQL language；不注册 `pgsql`。目录返回未知 language 时回退 `mysql` 并保持可编辑，避免空白编辑器。

前端脚本拆句仍用现有 `sql.ts`（MySQL）。目录暂不发布 scan dialect。

### 7. ArchUnit 与文档

`EngineCatalogController` 放在 `engine` 包（或 `engine.api`），只依赖 `EngineRegistry`。现有「主干不依赖 `engine.mysql`」规则保持。目录测试断言 MYSQL 描述的字段集合、默认端口、resourceTree `listEndpoint=databases`、`editorLanguage=mysql`。

`docs/backend-development-spec.md` 增加引擎目录一节，并写明 `/databases` 是 NAMESPACE 适配，路径不改。

## Risks / Trade-offs

- [目录与 JDBC 白名单漂移] → MYSQL 描述与 `MysqlJdbc` 属性表同一来源（同一类或同一常量）；单测比对两边的键集。
- [表单渲染器过于通用、MYSQL 回归难看] → widget 种类限制为 TEXT/NUMBER/PASSWORD/SELECT；MYSQL 默认值与现网一致；保留现有表单测试并改为喂目录 fixture。
- [可选 `engine` 让测试体继续「像只有 MySQL」] → 前端始终提交；缺省 MYSQL 只兼容旧客户端。下一引擎落地时再考虑改为必填。
- [NAMESPACE 与 `database` 双词] → 接受。改 URL/执行字段会破坏草稿和历史；PG 阶段仍把 schema 填进 `database` 参数。
- [未知 tree kind 被跳过，Hive 分区以后仍要改前端] → 本阶段目标是契约和标签，不是通用树框架。PARTITION 留到 Hive 变更。
- [Monaco 只注册 mysql] → 与「本阶段只有 MYSQL」一致；PG 变更再注册 `pgsql`。

## Migration Plan

1. 前后端同发：新目录 API、表单渲染器、`DatabaseItem.kind`、测试体可选 `engine`。
2. 无需 Flyway；`sql_data_source.engine` 已存在。
3. 回滚：回退应用版本。旧前端忽略 `kind`、不调用 `/engines` 仍可把类型当 MySQL；新前端依赖目录，不能配旧后端。

## Open Questions

- 列表「默认数据库」列表头是否改成「默认命名空间」。决定：不改。该列对应连接字段 `defaultDatabase`；PG 的钉死库仍是这个字段，schema 只出现在树上。
- 编辑已存数据源时是否禁止改 `engine`。决定：PUT 仍接受已注册 id（与今天相同）。只有 MYSQL 时没有实际切换。
