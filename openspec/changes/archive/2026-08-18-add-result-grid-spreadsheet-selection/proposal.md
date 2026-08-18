## Why

SQL 执行结果和双击表名打开的 Data 页共用同一套结果网格，但交互仍是单单元格点选：鼠标划过没有行/格反馈，不能拖出矩形选区，复制也只能覆盖当前格、整行或整表。日常对照数据和往 Excel 粘贴时，这和 CloudBeaver 一类编辑器的预期差一截，需要把网格补成可框选的数据表。

## What Changes

- 为结果网格增加鼠标划过时的行/单元格悬停高亮。
- 支持按下拖拽框选一块矩形单元格区域，并用 Shift 从锚点扩展选区。
- 点击行号选整行、点击列头选整列，且不破坏现有的列头排序手势。
- 将 `Ctrl/⌘C` 和右键复制改为复制当前选区（TSV，可贴进电子表格），并保留整表复制与 CSV 导出。
- SQL 结果页和表对象 Data 页共用同一组件，两处同时具备上述能力。

## Capabilities

### New Capabilities

- 无。本次只扩展已有工作台结果网格，不引入新的能力域。

### Modified Capabilities

- `frontend-sql-editor-workbench`: 结果网格从单单元格点选扩展为悬停跟踪、矩形选区和选区复制，并覆盖 SQL 结果与表数据预览两处入口。

## Impact

- 前端：`web/src/components/result-grid/` 的网格状态、指针事件、键盘复制和样式；`ResultGrid.test.ts` 覆盖选区与复制。
- 复用：`TableViewer` Data 页已嵌入 `ResultGrid`，无需单独实现。
- 后端、导出 API、单元格值语义、排序/筛选/固定列、值面板均保持不变。
- 不引入 Handsontable / AG Grid 等新表格依赖。
