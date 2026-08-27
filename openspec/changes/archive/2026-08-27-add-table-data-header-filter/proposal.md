## Why

双击表名打开 Data 后，网格只能在已经返回的预览行上做客户端筛选和排序。大表上按字段找行几乎没用，右键筛选也不容易发现。需要像 dbgate 那样在表头下提供按列输入框，回车后把条件下推到数据库，并带上当前表头排序，否则重查后顺序会回到库默认，LIMIT 也会切到另一批行。

## What Changes

- 仅在表对象 Data 页的列头下方显示每列一个筛选输入框。
- 在筛选框回车时，用当前各列筛选条件重查该表：把条件编进 `WHERE`，把当前单列排序编进 `ORDER BY`，再走现有预览执行（含 `rowLimit`）。
- 在表 Data 上点击类型标记或右键改排序时，同样用当前 `WHERE` + 新 `ORDER BY` 重查，而不是只排已返回行。
- SQL 结果页保持现有客户端筛选/排序（底栏「过滤已返回结果」、右键列筛选、表头排序），不出现表头筛选行，也不改写用户 SQL。
- 第一期筛选语法收窄：空值忽略；裸文本对字符串 `LIKE %…%`、对数字/日期等值；显式 `=` `>` `<` `>=` `<=`；`NULL` / `NOT NULL`。不做单框 AND/OR、关联表筛选或字典下拉。

## Capabilities

### New Capabilities

- 无。本次只扩展表对象 Data 预览，不引入新的能力域。

### Modified Capabilities

- `frontend-sql-editor-workbench`: 表对象 Data 预览从「`SELECT *` + 客户端筛排」改为表头筛选行驱动的 `WHERE`/`ORDER BY` 重查；SQL 结果网格的客户端筛排保持不变。

## Impact

- 前端：`selectTableData` 及表 Data 重查路径；`ResultGrid` 表头布局与命中测试（筛选行只在表 Data 启用）；表对象 tab 记住每列筛选草稿和当前排序。
- 复用现有 `/api/v1/sql/executions` 与 `rowLimit`，不新增后端 API。
- SQL 编辑器、Properties 页、CSV 导出、值面板、固定列/列宽保持现有行为。
- 不引入 Handsontable / AG Grid，不复刻 dbgate 完整筛选语言。
