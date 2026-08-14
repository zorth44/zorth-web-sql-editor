## Why

SQL 工作台侧边栏把数据库当成根节点，数据源选择被藏在编辑器工具栏下拉框里。多数据源时用户无法在导航树里看到连接层级，也无法按 CloudBeaver 的习惯先选数据源再展开库列表。

## What Changes

- 将资源树根节点改为可见数据源（连接），展开后懒加载该源下的数据库，再展开表/视图。
- 侧边栏搜索覆盖数据源名称和已展开的数据库名称；同名数据源用 host/port 区分。
- 允许同时展开多个数据源；点击库或表才把当前页签绑定到该连接。
- 编辑器工具栏保留数据源/数据库下拉框，与树的当前绑定双向同步。
- 无数据源时在侧边栏给出空状态，而不是要求用户先去工具栏选择。

## Capabilities

### New Capabilities

- （无）本变更只调整已有工作台导航，不引入新能力域。

### Modified Capabilities

- `frontend-sql-editor-workbench`: 资源浏览器以数据源为根，展开后才列出数据库；工具栏连接选择与树保持同步。

## Impact

- 前端：`web/src/components/resource-tree/ResourceBrowser.vue` 与 `web/src/pages/SqlEditorPage.vue` 的数据源/数据库加载与选择流。
- API：复用现有数据源列表和 metadata 接口，不改后端契约。
- 测试：更新工作台 E2E/组件断言，覆盖树根为数据源、展开后出现数据库、工具栏与树同步。
