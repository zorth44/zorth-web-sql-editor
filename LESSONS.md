# 可复用经验

仅记录已有证据的陷阱与处理方向。通用规则见 AGENTS 路由的代码标准和执行流程；新增经验应包含适用范围、证据及处理方式，已失效内容及时更新或删除。

| 适用范围 | 经验与处理方向 | 证据 |
| --- | --- | --- |
| SQL scanner | 末尾纯注释不能算第二条语句；修改分句逻辑时保留注释、引号等边界测试。 | [SQL 测试](web/src/sql-editor/sql.test.ts)、[前端交付记录](docs/archive/2026-10-13-sql-optimizer-delivery.md) |
| 前端 CSV 测试环境 | jsdom 26.1.0 的 Blob 缺少 `text()`/`arrayBuffer()`/`stream()`，与 Node 的 fetch/Response 混用会产生实现差异。已在 Vitest setup 显式使用 `node:buffer` 的 Blob，并覆盖构造与响应读取；保留 CSV 内容断言。排查复发时检查 Blob/Response 来源，不将环境失败当作业务回归。 | [测试初始化](web/tests/setup.ts)、[环境回归测试](web/tests/web-platform.test.ts)、[历史联调记录第 5 节](docs/archive/2026-10-13-sql-optimizer-live-acceptance.md#5-已知问题与观察) |
| 优化器并发 | 心跳若改变领取时的 version，可能导致自己的完成 CAS 失败；领取、续租和终态写入必须一起检查，保留迟到结果保护。 | [后端交付记录](docs/archive/2026-10-13-sql-optimizer-delivery.md)、[后端规格](openspec/specs/backend-sql-optimization/spec.md) |
| Agent 报告 | HTTP 200 或 SUCCEEDED 不代表取证完整；limitations 表示未作结论，不能显示“全部规则通过”。 | [真实联调记录](docs/archive/2026-10-13-sql-optimizer-live-acceptance.md)、[UI 规格](openspec/specs/sql-optimizer-ui/spec.md) |
| 前端资源树过滤测试 | 表与视图分组默认折叠，断言某个表名“不可见”可能只说明分组未展开，而不是过滤生效。验证过滤时应展开对应分组，或先断言过滤前的可见性基线。 | [资源树测试](web/src/components/resource-tree/ResourceBrowser.test.ts) |
