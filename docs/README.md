# docs 索引

本目录只保留仍在使用中的运行与实施文档。产品与接口规格的唯一来源是 [`openspec/specs/`](../openspec/specs/)；每个已完成变更的提案、设计与任务记录在 [`openspec/changes/archive/`](../openspec/changes/archive/)。

## 本地运行

- [第二阶段本地完整链路](./local-development.md)：本地启动 auth-service、SQL service、Web，以及对接 AI Agent Platform 的步骤。
- [SQL 优化器真实链路验收](./sql-optimizer-live-acceptance.md)：关闭 Mock 的前后端联合验收范围、结果与未完成项；驱动工具见 [`tools/optimizer-live/`](../tools/optimizer-live/README.md)。

## 对外契约

- [Agent Database Tool API](./agent-database-api.md)：`/internal/api/v1/agent/**` 的鉴权、端点、字段与错误约定。

## SQL 优化器（本地交付完成，生产未启用）

- [并行实施入口](./plan-sql-optimizer-async-tasks.md)
- [前后端共享契约 v1](./sql-optimizer-api-contract.md)
- [后端实施方案](./plan-sql-optimizer-backend.md)
- [前端实施方案](./plan-sql-optimizer-frontend.md)
- [启动与验收约定](./sql-optimizer-kickoff.md)
- [真实链路验收记录](./sql-optimizer-live-acceptance.md)

## 其他位置

- 部署与安全配置、凭据轮换、厂商 JAR 交付方式见 [`service/docs/`](../service/docs/)。
- 工程说明：前端 [`web/README.md`](../web/README.md)，后端 [`service/README.md`](../service/README.md)。

## 历史文档

立项期的《前端开发说明书》《后端与 API 开发说明书》以及两份 Copilot 计划（本仓库侧、AI 服务侧）已完成使命：内容或与现状不符，或已全部落地，现由 `openspec/specs/` 下的 spec 取代，已从本目录删除。需要回溯时可用 git 历史：

```bash
git log --diff-filter=D --name-only -- docs/
```
