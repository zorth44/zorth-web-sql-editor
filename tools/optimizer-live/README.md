# SQL 优化器真实链路验收工具

本目录是 [`docs/archive/2026-10-13-sql-optimizer-live-acceptance.md`](../../docs/archive/2026-10-13-sql-optimizer-live-acceptance.md) 记录的验收驱动：在本机拉起 **真实** 链路——真实 SQL service、真实 auth-service、真实 Agent Platform（真实模型）、真实 MySQL 与关闭 Mock 的前端——然后用 Playwright 驱动浏览器和接口走完契约场景。

不是 Mock/Stub 验收，也不能替代生产验收：身份使用 `dev` 开发头，凭证托管政策与生产开关仍未批准。

## 前置条件

- JDK 17（脚本按 Homebrew 路径自动探测，可用 `JAVA_HOME_17` 覆盖）、Maven、Node/pnpm、`shasum`、`curl`、Python 3.9+。
- 可访问的 MySQL 8.0 服务器，具备建库权限。脚本不会创建数据库，需要先准备两个库：
  - 元数据库（默认 `sqleditor_dev`）：Flyway V1–V8 由服务启动时应用。
  - 目标库（默认 `optimizer_demo`）：被 SQL 编辑器连接并执行 plan-only EXPLAIN 的库。建议放几张只有主键、数据量达万级的表，这样真实计划才会出现全表扫描等可解释的问题。
- 兄弟仓库 `bddf-agentscope`（Agent Platform）与模型 API Key。可用 `AGENT_REPO` 指定路径。
- 端口 `8080`（SQL service）、`8090`（auth-service）、`8081`（Agent Platform）、`5173`（Web）空闲。

## 环境变量

```bash
export MYSQL_HOST=... MYSQL_PORT=... MYSQL_USER=... MYSQL_PASSWORD=...
export LLM_API_KEY=...            # Agent Platform 模型 Key
# 可选
export AGENT_REPO=$HOME/Code/ai/bddf-agentscope
export OPTIMIZER_LIVE_RUNTIME=/private/tmp/optimizer-live   # 密钥、日志与证据目录（默认）
export OPTIMIZER_USERS=zhangsan,lisi                        # 授予 SQL_OPTIMIZE 的账号
export OPTIMIZER_DENIED_USER=wangwu                         # 用于校验 403 的未授权账号
export OPTIMIZER_PASSWORD=LocalDevPass123!                  # 本地登录口令（>12 字符）
```

## 步骤

```bash
# 1. 拉起整套栈（首次会打包 service 与 Agent Platform 的 JAR）
tools/optimizer-live/stack.sh up

# 2. 主链路：登录、数据源、提交、轮询、报告、关页、重扫
MYSQL_PASSWORD="$MYSQL_PASSWORD" node tools/optimizer-live/live-e2e.mjs

# 3. 关页继续执行 + 无能力账号 403
node tools/optimizer-live/live-failures.mjs pageclose

# 4. 凭证过期：关调度器重启 → 排队 → 真实登出 → 开调度器重启 → 恢复队列
OPTIMIZATION_SCHEDULER_ENABLED=false tools/optimizer-live/stack.sh restart
node tools/optimizer-live/live-failures.mjs stage-expiry
tools/optimizer-live/stack.sh restart            # 调度器恢复默认 true
node tools/optimizer-live/live-failures.mjs verify

# 5. 收尾
tools/optimizer-live/stack.sh down
```

`stack.sh status` 查看各服务状态。密钥与运行状态写在 `$OPTIMIZER_LIVE_RUNTIME`（`keys.json`、`stack.json`、`logs/`），权限为仅当前用户可读，**不要提交**。重复运行会复用同一份密钥——删掉 `keys.json` 后已保存的数据源口令将无法解密，需要重新创建数据源。

## 证据

驱动脚本把结果写到 `$OPTIMIZER_LIVE_RUNTIME`：

| 文件 | 内容 |
| --- | --- |
| `live-result.json` | 主链路每一步的实际结果、任务 ID、终态与报告计数 |
| `live-01-submitted.png` / `live-02-report.png` | 提交页与真实报告截图 |
| `live-report.txt` | 报告页渲染出的完整文本 |
| `failure-state.json` | 关页、403、跨用户、凭证过期与契约核对的分步结果 |
| `live-03-credential-expired.png` / `live-04-not-found.png` / `live-05-forbidden.png` | 失败、不可见与无能力页面截图 |
| `live-failure.txt` | 失败任务的页面文本 |
| `logs/*.log` | 四个服务的日志（含 Agent 工具调用与内部 API 回调） |

验证报告是否真的来自 Agent 取证，可查元数据库：

```sql
SELECT source, operation, statement_type, status, COUNT(*)
FROM sqleditor_dev.sql_execution_history GROUP BY source, operation, statement_type, status;
```

`AI_AGENT_EXPLAIN` 行即 Agent 通过 `/internal/api/v1/agent/...` 取回的 plan-only 计划；不应出现查询执行或 ANALYZE。

## 约束

- 只使用本机与脚本自建的库；不要指向生产库，`MYSQL_*` 指向的实例会被建表（仅限所选的两个库）。
- `stack.sh down` 只结束本脚本记录的 PID，不影响其他进程与容器。
- 未授权的账号必须留在 `OPTIMIZER_USERS` 之外，否则 403 场景失效。
- 真实模型调用会产生费用：一次成功扫描约 25–30 秒，包含多次只读工具调用。
