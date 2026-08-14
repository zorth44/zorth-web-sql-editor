## Context

工作台侧边栏 `ResourceBrowser` 目前只渲染当前选中数据源下的数据库，数据源选择在 `SqlEditorPage` 工具栏的 `<select>`。第二阶段规格假定「先选数据源，再展开数据库」，但选择入口不在树上。本变更只改前端导航结构，复用已有 `listDataSources` / `listDatabases` / `listTables` / 表结构接口。

## Goals / Non-Goals

**Goals:**

- 资源树以可见数据源为根，展开后懒加载数据库，再展开表/视图。
- 可同时展开多个数据源；当前执行连接由点击库/表或工具栏下拉框决定。
- 工具栏数据源/数据库选择与树的当前绑定双向同步。
- 同名数据源用 host/port 区分；表缓存按 `dataSourceId + database` 隔离。

**Non-Goals:**

- 不增加 Connect/Disconnect、Project/文件夹、Simple/Advanced 视图。
- 不把字段/索引挂进树节点（结构抽屉保持原样）。
- 不改后端 metadata API、连接池或页签绑定语义。

## Decisions

### 1. 树持有全部可见数据源，按源懒加载数据库

`ResourceBrowser` 接收数据源列表，而不是只接收当前源的 `databases[]`。展开某个源时才调用 `listDatabases(id)`，结果缓存在 `databasesBySource[id]`。

备选：侧边栏顶部再放一个数据源下拉，树仍从数据库开始。这只是把工具栏控件挪位置，达不到 CloudBeaver 的连接树。

### 2. 浏览与当前连接分离

允许同时展开多个数据源。点击数据源只展开/高亮，不强制把页签绑到该源的默认库；点击数据库或表才 `applyConnection(sourceId, database)`。工具栏下拉仍可直接改当前绑定，并展开对应源。

备选：手风琴，同时只展开一个源。实现更简单，但切换源会丢掉已展开的库/表，和 CloudBeaver 不一致。

### 3. 元数据 key 带上 dataSourceId

表、结构、展开状态从 `database` 改为 `dataSourceId + database`（及 group/table）。两个源可以有同名库，不能共用 `tablesByDb['orders']`。

### 4. 工具栏下拉保留并双向同步

树是主导航；工具栏继续显示并允许快速切换当前页签连接。树选中库/表时更新下拉；下拉变更时展开并高亮对应源和库，不清除其他源已加载的子节点。

备选：改成只读面包屑。少一个入口，但现有 E2E 和「页签绑定可见」都会变，收益不大。

### 5. 搜索分层

顶栏搜索过滤数据源名称（及 host），已展开源下同时过滤数据库名。表/视图过滤仍挂在展开的数据库下，避免一个输入框同时扫三层造成误伤。

### 6. 初始态

进入工作台仍为编辑器选中第一个可见数据源（或 URL 指定源）及其默认库，并自动展开该源。树上其余源保持折叠，直到用户展开。没有数据源时显示空状态，指向数据源管理，不再提示「请先选择数据源」。

## Risks / Trade-offs

- [同时展开多个源会打多次 metadata] → 仅在展开时请求，按源缓存，刷新只重拉已展开源。
- [同名数据源/同名库造成选错连接] → 源节点展示 host:port；所有缓存 key 带 dataSourceId。
- [工具栏与树两处选择可能不同步] → 单一 `applyConnection`，树与下拉都走它；页签切换仍回写这两处。

## Migration Plan

纯前端。部署新静态资源即可；回滚到上一版前端即可恢复旧树。无需数据迁移。

## Open Questions

无。工具栏保持可切换，已在提案中确认。
