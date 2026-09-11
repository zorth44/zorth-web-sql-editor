## Context

当前补全是一张平铺 `string[]`：资源树把已展开的数据源名、库名、表名，以及**已经打开过的表**的字段名 `emit` 给 `SqlEditorPage`，再原样塞进 Monaco。`SqlMonacoEditor` 没有 `triggerCharacters`，也不看光标前的标识符。

因此：

- 只展开到表列表时，用户能补全表名（符合预期）。
- 输入 `orders.` 不会弹出该表字段：`.` 不是触发字符；字段往往还没加载；即使加载了也会和表名混在同一列表里。

`GET /api/v1/data-sources/{id}/table-detail` 已经能返回列（外加主键、索引、DDL）。资源树只在打开表时调用它，并在刷新时清空 `details`。

本变更只改前端补全数据形状和 Monaco 提供者，不改元数据 API，不预拉全库。

## Goals / Non-Goals

**Goals:**

- 输入当前页签所绑库中**已加载表名**后的 `.` 时，补全该表字段。
- 支持未加引号的标识符，以及当前引擎引号（MySQL/GBase `` ` ``、PostgreSQL `"`）包裹的表名。
- 字段按需拉取并缓存；同一张表并发补全只打一次 `table-detail`。
- 刷新资源树或页签连接变化后立即丢弃当前补全字段缓存，旧异步响应不得重新写回。
- 资源树因切换侧栏卸载后，此前加载过的当前连接表目录仍可用于解析限定符。
- 限定符解析、表名匹配、插入文本引用与内部引用符转义，都是可单测纯函数。
- 字符串、行注释和块注释中的类似文本不触发限定字段补全。

**Non-Goals:**

- 不解析 `FROM` / `JOIN` 别名、CTE、子查询输出列。
- 不在展开库时预热全部表的 `table-detail`。
- 不新增「只返回列」的后端接口。
- 不补全 `库.表.` 中的库段（`a.b.` 只把 `b` 当表名，在当前绑库的表清单里查找）。
- 不替换 Monaco 语言自带的关键字补全。
- 不把补全项做成可执行片段或带凭证的文本。

## Decisions

### 1. 补全目录按当前连接结构化，不再摊成 `string[]`

`ResourceBrowser` 继续拥有树的加载时机，但 `suggestions` 改为带绑定身份和元数据代次的目录快照，而不是全局名字袋：

```
{
  dataSourceId: string | null
  namespace: string | null
  generation: number
  namespaces: string[]   // 当前数据源已加载的库/schema 名
  tables: string[]       // 当前绑库已加载的表/视图名
  columnsByTable: { [tableName]: string[] }  // 仅当前代次已缓存结构的表
}
```

`SqlEditorPage` 按 `(dataSourceId, namespace)` 保存已经收到的目录快照，active catalog 必须根据当前 SQL 页签的绑定选择，不能把一次迟到的事件归到另一个页签。绑定 key 使用嵌套 `Map` 或等价的无碰撞元组编码，不能直接用含数据库/表名分隔符的字符串拼接。目录中的 `namespaces`/`tables` 可以跨侧栏卸载保留；字段缓存和 `columnsByTable` 受第 2 节的 generation 约束。

completion generation 由 `SqlEditorPage` 统一持有并作为 prop 传给 `ResourceBrowser`；页面发起的列请求和资源树发布的快照必须使用同一个值，避免形成两个无法比较的失效计数器。

因此切到历史/脚本侧栏、`ResourceBrowser` 被卸载后，只要该 binding 的表清单此前加载过，编辑器仍能解析表名并直接按需拉列。若该 binding 从未加载过表清单，仍按 Non-Goal 不在输入时调用 `listAllTables`；用户打开数据库侧栏加载一次即可。

无 `.` 限定时，编辑器提供 active catalog 的 `namespaces` + `tables` + 当前代次已缓存字段（去重）。有 `表.` 限定时，**只**提供该表字段，不再混入表名。

备选是继续平铺、靠 Monaco 文本过滤。`.` 之后当前词为空，平铺列表会弹出全部表名，这正是现在的失败模式，因此不采用。

### 2. 字段按需走现有 `table-detail`，缓存在编辑器页而不是预热资源树

`SqlEditorPage` 持有以 `(generation, dataSourceId, database, table)` 元组隔离的内存缓存，并实现 `resolveColumns(table)`：

1. 命中 `columnsByTable` 或页级缓存则立即返回。
2. 表名能在当前 `tables` 里解析出来，且尚未缓存，则调用现有 `getTableDetail`。
3. 当前 generation 中进行中的同一元组共用同一个 Promise。
4. 失败返回空列表，不弹错误条（资源树打开表失败仍走现有 notice）。

`resourceNonce` 递增或页签绑定的数据源/库变化时，先同步递增 completion generation、清空字段缓存及 in-flight 索引，再开始任何异步刷新。每个 `table-detail` 请求捕获发起时的 generation 和 binding；响应只有在二者仍与当前请求上下文一致时才能写缓存，否则仅结束该 Promise，不发布字段。

`ResourceBrowser` 的补全投影也必须在刷新开始时同步清空 `columnsByTable`，不能等数据库/表清单请求结束后才清空。其异步加载结果携带发起时的 binding/generation，只有仍有效时才能发布；资源树为展示目的保留的其他 UI 元数据不能越过这一补全失效边界。这样 DDL 后刷新期间以及刷新前请求迟到时，都不会短暂恢复旧列。

不把 `resolveColumns` 绑死在 `ResourceBrowser` 的 `ref` 上：切到历史/脚本侧栏时树会卸载，SQL 页签仍应能按需拉列。

备选是展开库时对每张表打 `table-detail`。大库会占连接池、放大 JDBC `getColumns`/`getIndexInfo`/`SHOW CREATE TABLE`，明确拒绝。

不新增列清单 API：第一期可以接受 `table-detail` 偏重。若以后补全成为热点，另开 change 做轻量 columns 接口。

### 3. 用纯函数识别 `标识符.`，不引入 SQL 解析器

在 `web/src/sql-editor/` 增加补全辅助模块（名称实现时确定），输入为编辑器文本、光标 offset、引擎引号字符。复用现有 SQL scanner 的状态规则或等价的轻量扫描，判断光标是否处于普通 SQL 代码；不引入完整 SQL AST。

光标位于 `qualifier.prefix` 时（`prefix` 可为空）：

- `qualifier` 可以是未加引号的 `[A-Za-z_][A-Za-z0-9_$]*`，或由引擎引号包裹、内部引号按 SQL 规则加倍的标识符。
- 只取**紧挨着这个 `.` 左边**的那一段。`db.orders.` 的 qualifier 是 `orders`，在当前绑库表清单里查找 `orders`。
- `prefix` 作为 Monaco 替换范围（从 `.` 之后到光标），用来过滤字段。
- 光标位于单引号字符串、PostgreSQL dollar-quoted 字符串、行注释或块注释时返回 `non-code`，不形成 qualifier、不调用 `resolveColumns`，也不回退到无限定元数据补全。双引号仅在当前引擎的 `identifierQuote` 为 `"` 时按标识符引用处理，否则按字符串上下文处理。

表名解析：先精确匹配 `tables`；否则大小写不敏感且唯一则用那张表；0 个或多个不敏感匹配则视为未解析，不发请求、不给字段。

普通 SQL 代码中未形成 `ident.` 形态时走无限定补全。已形成该形态但解析失败（别名、打错）返回空字段列表，不回退到全量表名；`non-code` 上下文同样返回空列表。

备选是扫描整句 `FROM`/`JOIN` 建别名表。没有 AST，边界（子查询、逗号交叉 join、引号）容易错，放到后续 change。

### 4. Monaco：`.` 为触发字符；提供者读最新目录，按需 async

`registerCompletionItemProvider` 设置 `triggerCharacters: ['.']`（`suggestOnTriggerCharacters` 已经是 true）。

`provideCompletionItems` 允许返回 Promise：首次 `orders.` 可以等这一次 `table-detail`，但不得因此卡住按键处理。语言切换时重注册提供者；目录更新时改闭包/ref 指向的数据，避免每次 `suggestions` 变化都 dispose/重建。

插入文本：列名符合未加引号标识符规则则原样插入，否则复用现有 `quoteIdentifier`，用当前引擎 `identifierQuote` 包裹并把名称内部的同类引用符加倍（例如 `` a`b → `a``b` ``、`a"b → "a""b"`）。不插入多语句或函数调用。

`CompletionItemKind`：表用 `Class`（或等价的表语义），字段用 `Field`，NAMESPACE 用 `Module`。不再把所有项都标成 `Field`。

### 5. 补全范围跟着当前 SQL 页签的连接走

表清单和 `table-detail` 使用**当前页签**绑定的 `dataSourceId` + `database`，与运行 SQL 的连接一致。未绑库时没有表级字段补全（与必须选库才能访问表对象的现有规则一致）。

未在资源树展开过当前库、因而 `tables` 仍为空时，无法解析表名，也就不发 `table-detail`。用户需要先选库（现有 `watch` 会 `expandDatabase` 并加载表）。不在输入时偷偷 `listAllTables`。

## Risks / Trade-offs

- [首次 `表.` 要等一次偏重的 `table-detail`（列+索引+DDL）] → 只打当前这一张表；缓存后不再打；失败则空列表。不在输入路径上预拉。
- [别名 `FROM orders o` 再输入 `o.` 没有字段] → 第一期明确不做；空列表优于把所有表名弹出来。
- [表名大小写在 MySQL/PostgreSQL 上不一致] → 精确优先，其次唯一的忽略大小写匹配；仍然歧义则不猜。
- [侧栏切走后资源树卸载] → 页级缓存 + 直接 `getTableDetail`，不依赖树组件存活。
- [缓存列在未点刷新时过期] → 与资源树现有元数据缓存相同；刷新已有入口。
- [并发补全重复打同一张表] → 按 table key 合并 in-flight Promise。
- [刷新前的请求在失效后迟到] → generation + binding 校验阻止旧响应写回，资源树的补全投影在异步刷新前先清空。
- [字符串或注释包含 `orders.`] → 轻量词法状态识别非代码上下文，不触发元数据请求。
- [字段名包含引擎引用符] → 复用 `quoteIdentifier` 加倍内部引用符，确保只插入一个合法标识符。

## Migration Plan

纯前端。随 Web 发布即可；回滚即回到平铺词表。无 API 版本、无数据迁移。已打开的页签在下一次补全请求时走新提供者。

## Open Questions

无。别名解析、轻量 columns API、输入时补拉表清单若需要，另开 change。
