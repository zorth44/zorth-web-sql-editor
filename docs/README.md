# 文档索引与维护约定

## 当前维护入口

- [子系统索引](subsystems/README.md)：代码定位、维护边界、规格链接与专项验证。
- [代码标准](code-standards.md)：实现、审查、测试和通用安全要求。
- [执行流程](workflow.md)：连续执行、授权、工作区隔离、提交、恢复与交付。
- [本地开发](local-development.md)：auth-service、SQL service、Web 与外部 Agent 的运行方式。
- [优化器生产待验收项](sql-optimizer-production-readiness.md)：本地交付之后仍需完成的生产输入与验收。

## 规格与接口参考

产品行为的权威来源为 [OpenSpec 当前规格](../openspec/specs/)。以下接口参考补充消费方需要的精确字段、示例与运行约定，变更时与规格和实现一起核对，不另设一套产品要求：

- [SQL 优化器共享契约 v1](sql-optimizer-api-contract.md)：四个任务 API、DTO、空值、幂等、游标与错误分类。
- [Agent Database Tool API](agent-database-api.md)：内部鉴权、端点、字段、限制与消费方黑盒接入。

工程运行与配置说明见 [web README](../web/README.md)、[service README](../service/README.md)、[auth-service README](../auth-service/README.md)。部署、安全配置、凭据轮换和厂商 JAR 交付见 `service/docs/`。

## 历史记录

已完成变更的提案、设计、tasks/execution 位于 [OpenSpec 归档](../openspec/changes/archive/)。跨变更的验收证据见 [docs 归档](archive/README.md)。历史记录只描述当时状态，不作为当前实施入口，也不证明当前环境的测试结果。

优化器并行启动入口、kickoff 与前后端实施方案已删除；长期维护信息进入子系统入口，交付证据归档，生产待办独立保留。更早删除的开发说明书和 Copilot 计划也可通过 git 历史回溯：

```bash
git log --diff-filter=D --name-only -- docs/
```

## 文档维护约定

- 根 AGENTS 只维护任务到文档的路由；CLAUDE 只引用 AGENTS 和 LESSONS。
- 子系统入口保持简短，引用现行规格、代码和验证方式，不复制整份 spec、DTO 或历史任务清单。
- 新行为先更新相应规格；精确线协议参考随接口同步。发现文档与实现冲突时核对并明确修正，不能静默发明兼容行为。
- 完成开发后提取仍有效的约束、运行方式与待办，再删除一次性计划。历史设计依赖 OpenSpec/git，实际验收证据保留归档；不把未完成生产事项随计划一起删除。
- 移动文件同步有效入口和工具 README 的链接；归档引用迁移仅修正定位，不重写历史结果。检查 Markdown 相对链接和过期引用，工作区搜索排除 `.worktrees/` 与构建/依赖目录。
