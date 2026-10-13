# SQL 工作台

## 代码与规格

- 前端：`web/src/pages/SqlEditorPage.vue`、`sql-editor/`、`stores/editor.ts`、`components/editor/`、`result-grid/`、`table-viewer/`、`history/`、`scripts/` 及对应 API。
- 后端：`service/src/main/java/com/bocsoft/sqleditor/execution/`、`history/`、`export/`、`script/`；SQL 安全与计划由 execution 和 engine 协作。
- 规格：[前端工作台](../../openspec/specs/frontend-sql-editor-workbench/spec.md)、[后端执行](../../openspec/specs/backend-sql-editor/spec.md)、[脚本](../../openspec/specs/backend-sql-scripts/spec.md)、[历史与导出](../../openspec/specs/backend-sql-history-export/spec.md)。

## 维护边界与验证

复用 SQL scanner、执行 registry 与取消机制，保留串行脚本、遇错停止、只读限制和超时语义。结果页过滤已返回行与表数据重新查询的过滤是不同操作，不能混用。执行、历史、脚本和导出始终校验当前身份与产品。

CSV 导出保留流式路径、资源释放与浏览器回退，不能为方便改成无界缓存整个结果。Agent plan-only EXPLAIN 不等于业务查询，也不允许 ANALYZE。

验证 SQL scanner、script-runner、result-grid、CSV 与执行 API 单测，以及 `web/e2e/phase-two.spec.ts`、`web/e2e/scripts.spec.ts`；后端验证执行/取消、历史权限与导出集成测试。
