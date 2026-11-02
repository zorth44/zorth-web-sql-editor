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

## 现行规格

规格正文在 `openspec/specs/<name>/spec.md`。下面只按子系统归组，便于从这里跳进去。各入口里的规格链接与此表一致。

### 应用与认证

- [应用外壳](../../openspec/specs/frontend-application-shell/spec.md)
- [前端认证](../../openspec/specs/frontend-auth-session/spec.md)
- [后端认证](../../openspec/specs/backend-auth-session/spec.md)
- [本地授权服务](../../openspec/specs/local-auth-service/spec.md)

### 数据源与引擎

- [产品行为](../../openspec/specs/data-source-management/spec.md)
- [后端管理](../../openspec/specs/backend-data-source-management/spec.md)
- [引擎目录](../../openspec/specs/backend-engine-catalog/spec.md)
- [Engine SPI](../../openspec/specs/backend-engine-spi/spec.md)
- [连接安全](../../openspec/specs/backend-connection-security/spec.md)
- [PostgreSQL](../../openspec/specs/backend-postgresql-engine/spec.md)
- [GBase 8a](../../openspec/specs/backend-gbase-8a-engine/spec.md)
- [Hive](../../openspec/specs/backend-hive-engine/spec.md)
- [Hive Kerberos](../../openspec/specs/backend-hive-kerberos-engine/spec.md)
- [Iceberg](../../openspec/specs/backend-iceberg-engine/spec.md)
- [Kerberos 与 ZooKeeper 连接](../../openspec/specs/backend-kerberos-zk-connection/spec.md)

### SQL 工作台

- [前端工作台](../../openspec/specs/frontend-sql-editor-workbench/spec.md)
- [后端执行](../../openspec/specs/backend-sql-editor/spec.md)
- [脚本](../../openspec/specs/backend-sql-scripts/spec.md)
- [历史与导出](../../openspec/specs/backend-sql-history-export/spec.md)

### SQL Copilot

- [SQL Copilot](../../openspec/specs/frontend-sql-copilot/spec.md)

### Agent 数据库 API

- [Agent 数据库 API](../../openspec/specs/backend-agent-database-api/spec.md)

### SQL 优化器

- [后端](../../openspec/specs/backend-sql-optimization/spec.md)
- [UI](../../openspec/specs/sql-optimizer-ui/spec.md)

