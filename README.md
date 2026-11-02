# Zorth Web SQL Editor

浏览器里的 SQL 工作台。前端是 Vue 3 应用（`web/`），后端是 Spring Boot 服务（`service/`），负责数据源、元数据、SQL 执行、历史与导出。登录身份来自外部授权服务；仓库里的 `auth-service/` 只给本地开发和 E2E 用，不能部署到生产。

当前功能包括数据源管理、SQL 编辑器、SQL Copilot，以及默认关闭的 SQL 优化器。支持的引擎以服务端引擎目录为准。产品行为以 [OpenSpec 现行规格](openspec/specs/) 为准。

## 仓库

| 目录 | 内容 |
| --- | --- |
| `web/` | 前端。本地启动、环境变量与运行时配置见 [web/README.md](web/README.md) |
| `service/` | SQL 服务。构建与验证见 [service/README.md](service/README.md)；部署、凭据轮换、厂商 JAR 见 [service/docs/](service/docs/deployment.md) |
| `auth-service/` | 本地临时授权服务，见 [auth-service/README.md](auth-service/README.md) |
| `tools/` | 优化器真实链路联调驱动 |
| `openspec/` | 现行规格与已归档变更 |
| `docs/` | 索引、运行说明、接口参考和维护入口 |

本机把授权服务、SQL 服务和前端一起跑起来，见 [本地开发](docs/guides/local-development.md)。

## 文档怎么找

| 你要做的事 | 打开 |
| --- | --- |
| 本机跑通整条链路 | [本地开发](docs/guides/local-development.md)，组件命令看各目录 README |
| 改某一块功能 | [子系统索引](docs/subsystems/README.md)，再读对应入口和它链接的规格 |
| 对接 Agent 数据库工具 | [Agent Database Tool API](docs/reference/agent-database-tool-api.md) |
| 对接优化器任务 API | [共享契约 v1](docs/reference/sql-optimizer-api-contract.md) |
| 部署、轮换密钥、交付厂商 JAR | [部署](service/docs/deployment.md)、[凭据轮换](service/docs/credential-rotation.md)、[厂商依赖](service/docs/vendor-dependencies.md) |
| 看优化器还缺哪些生产验收 | [生产待验收项](docs/readiness/sql-optimizer.md) |
| 写代码、审查、选验证命令 | [代码标准](docs/engineering/code-standards.md) |
| 实施、提交、交付或恢复任务 | [执行流程](docs/engineering/workflow.md) |

维护索引和文档约定见 [docs/README.md](docs/README.md)。给编码代理的任务路由见 [AGENTS.md](AGENTS.md)。
