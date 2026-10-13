# SQL 优化器异步扫描：并行实施入口

日期：2026-10-13。状态：开工准备；业务代码尚未实施。
代码阅读基线：`zorth-web-sql-editor@9ffb143`、`bddf-agentscope@a904736`。

## 1. 文档与实施分工

| 文档 | 用途 | 负责人 |
| --- | --- | --- |
| [共享契约](sql-optimizer-api-contract.md) | API、DTO、状态、错误、幂等与 capability 的共同基线 | 开工前统一；两侧不得独立更改 |
| [后端实施方案](plan-sql-optimizer-backend.md) | 数据库、任务 API、后台执行、Agent 客户端、认证与后端验收 | 后端 worktree |
| [前端实施方案](plan-sql-optimizer-frontend.md) | 页面、请求层、MSW、轮询、报告和前端验收 | 前端 worktree |

核心流程：浏览器提交单条只读 SQL → SQL service 持久化任务并返回 202 → Worker 调用现有 SQL Lite 同步接口 → 保存报告 → 浏览器稍后查询。异步任务归本项目 service；Agent 不新增任务 API。前端可直接按共享契约使用 MSW 开工，不必等待后端或生产认证接入完成。

首版只做单 SQL、任务列表/详情、报告与重扫。暂不做批量/整库扫描、自动改写/执行、索引操作、运行中取消、实时进度、定时任务或团队共享。关页后继续不等于退出登录后一定继续；后台凭证策略见后端方案。

## 2. 两个 worktree 的文件边界

| 范围 | 后端 | 前端 |
| --- | --- | --- |
| `service/**` | 独占，包括 SessionResponse 与 SQL_OPTIMIZE 服务端授予/校验 | 不改 |
| `web/**` | 不改 | 独占，包括前端 Capability 类型、导航、MSW、E2E |
| `docs/plan-sql-optimizer-backend.md` | 可更新进度/实施说明 | 不改 |
| `docs/plan-sql-optimizer-frontend.md` | 不改 | 可更新进度/实施说明 |
| `docs/sql-optimizer-api-contract.md`、本入口文档 | 共同基线，不在实现分支各改一份 | 同左 |
| 通用 README、现有通用 docs、根配置 | 实现期避免同时修改，联调后统一同步 | 同左 |
| OpenSpec | 独立 backend change 目录与后端 delta | 独立 frontend change 目录与前端 delta |

建议两个 OpenSpec change 分别为 `add-sql-optimizer-backend`、`add-sql-optimizer-frontend`，各自 proposal/design/tasks/specs 在自己的 worktree 内生成。Session capability 的后端规格归后端、前端 Session 类型归前端。共有契约如需变更，先由双方确认版本并在独立协调改动中落地，再同步到两个分支，不能只改 MSW 或只改后端 DTO。

## 3. 开工顺序与分支基线

1. 将四份方案文档、根 AGENTS.md 与启动约定形成共同的本地基线提交，再从该提交建两个 worktree。不以 origin/main 代替尚未推送的本地文档基线；准备提交不代表已完成远端评审、合并或发布。
2. 后端执行 BE-01～BE-07；前端同时执行 FE-01～FE-06。前端以共享契约模拟 SQL_OPTIMIZE 与全部任务状态。
3. 后端交付 API 后，用真实接口替换 MSW 联调，不改页面字段语义。
4. 两个实现 PR 分别跑各自门禁；首个合并后，另一分支同步新 main 并重新验证。两侧均合并后关闭 Mock 做完整真实链路验收。

本次准备采用项目内 `.worktrees/`（已忽略），避免 sibling 目录超出当前会话可写范围。创建命令在主工作区执行；两个实现会话分别以对应 worktree 为工作区启动：

```bash
git worktree add .worktrees/sql-optimizer-backend -b feat/sql-optimizer-backend HEAD
git worktree add .worktrees/sql-optimizer-frontend -b feat/sql-optimizer-frontend HEAD
```

执行前检查目录/分支是否已存在。主工作目录保持 main；不要将两个实现放进同一工作区。两个 worktree 启动服务需使用不同端口；数据库破坏性测试不得共用同一测试 schema 并发运行，采用独立测试库或串行执行。前端 MSW 开发不依赖真实数据库。

## 4. 合并与交付边界

两侧都按 capability/服务端配置控制功能启用，缺少 SQL_OPTIMIZE 时前端隐藏入口且后端拒绝请求。前端可能先合并，但后端未开放能力前不向真实用户暴露页面；后端可能先合并，必须仍独立保护新 API。

生产上线依赖真实 Agent 身份接入、用户凭证托管和内部取证回调；这些由后端跟进，不阻塞前端 Mock 开发，但不能被标成已完成。最终联合验收：创建 → 关页 → 返回 → 报告；过期失败 → 重新登录 → 重扫；重启恢复排队任务、越权 404、迟到结果不覆盖终态。

## 5. 自动推进约定与开工证据

FE/BE 编号是 Agent 内部步骤，按依赖持续实现、验证、修复和阶段提交；阶段完成无需用户再次下达“继续”。根 [AGENTS.md](../AGENTS.md) 定义停止条件、文件边界和长任务恢复方式。

两个独立会话各接一次完整任务；开工准备不启动业务实现或额外 Agent。启动文本、本地交付范围、环境检查和真实联调依赖见 [启动与验收约定](sql-optimizer-kickoff.md)。

各侧实施时创建自己的 OpenSpec change。完成状态维护在各自 tasks.md，execution.md 保存提交、测试证据、下一步和阻塞；原方案清单作为范围基线，不再重复更新勾选。
