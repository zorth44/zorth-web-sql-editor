# 可复用经验

仅记录已有证据的陷阱与处理方向。通用规则见 AGENTS 路由的代码标准和执行流程；新增经验应包含适用范围、证据及处理方式，已失效内容及时更新或删除。

| 适用范围 | 经验与处理方向 | 证据 |
| --- | --- | --- |
| SQL scanner | 末尾纯注释不能算第二条语句；修改分句逻辑时保留注释、引号等边界测试。 | [SQL 测试](web/src/sql-editor/sql.test.ts)、[前端交付记录](docs/archive/2026-10-13-sql-optimizer-delivery.md) |
| 前端 CSV 测试环境 | 2026-10-13 的 jsdom 26.1.0 环境曾返回没有 `text()` 的 Blob，导致两个既有导出测试失败；复现时检查 Blob/Response 的实现来源，并区分环境基线与业务回归，不放宽断言。 | [真实联调记录第 5 节](docs/archive/2026-10-13-sql-optimizer-live-acceptance.md#5-已知问题与观察) |
| 优化器并发 | 心跳若改变领取时的 version，可能导致自己的完成 CAS 失败；领取、续租和终态写入必须一起检查，保留迟到结果保护。 | [后端交付记录](docs/archive/2026-10-13-sql-optimizer-delivery.md)、[后端规格](openspec/specs/backend-sql-optimization/spec.md) |
| Agent 报告 | HTTP 200 或 SUCCEEDED 不代表取证完整；limitations 表示未作结论，不能显示“全部规则通过”。 | [真实联调记录](docs/archive/2026-10-13-sql-optimizer-live-acceptance.md)、[UI 规格](openspec/specs/sql-optimizer-ui/spec.md) |
