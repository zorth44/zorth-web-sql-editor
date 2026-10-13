# 子系统维护入口

按本次改动选择入口，再读入口引用的现行规格。跨子系统改动阅读所有受影响入口；代码标准和执行流程由根 AGENTS 路由。

| 子系统 | 范围 |
| --- | --- |
| [应用与认证](application-auth.md) | 应用外壳、路由、登录、会话、运行时配置、本地 auth-service |
| [数据源与引擎](data-sources-engines.md) | 数据源管理、连接安全、元数据、EngineSupport、厂商驱动 |
| [SQL 工作台](sql-workbench.md) | Monaco、执行/取消、表数据、脚本、历史与 CSV 导出 |
| [SQL Copilot](sql-copilot.md) | 对话状态、AI API/SSE、工具展示与 Markdown |
| [Agent 数据库 API](agent-database-api.md) | 内部鉴权、只读取证、元数据与关系分页 |
| [SQL 优化器](sql-optimizer.md) | 持久任务、Worker、短期凭证、报告、轮询与重扫 |

入口只保存代码定位、维护边界、规格链接和专项验证。产品行为以 `openspec/specs/` 为准，历史方案和验收结果见归档，不在这里复制完整 DTO 或任务清单。
