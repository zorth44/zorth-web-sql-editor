# SQL 优化器前后端真实链路验收

日期：2026-10-13。范围：本机真实服务链路的前后端联合验收——前端关闭 Mock、后端真实数据库与 Worker、真实 Agent Platform 与真实模型、真实目标 MySQL。本文件是 `add-sql-optimizer-frontend` FE-06 与 `add-sql-optimizer-backend` 8.x 的验收证据，运行方式见 [`tools/optimizer-live/README.md`](../../tools/optimizer-live/README.md)。

生产身份适配器、短期凭证托管政策审批、生产开关与保留期确认仍未验收，见文末第 7 节。本文不代表生产环境已启用。

## 1. 验收链路

```text
浏览器(Vite 5173, VITE_ENABLE_API_MOCK=false)
  └─ /sql-api → SQL service 8080（本仓库 service/，优化器能力已开启）
        ├─ 鉴权上下文 → auth-service 8090（临时本地授权服务，产品 local-product）
        ├─ 元数据库 → MySQL 8.0.46 sqleditor_dev（Flyway V1–V8，含 V8 优化任务表）
        ├─ Worker → Agent Platform 8081（bddf-agentscope，真实模型 deepseek-flash）
        │     └─ 回调 /internal/api/v1/agent/**（用户 Bearer + 配对 X-Internal-Service-Key）
        └─ 目标数据源 → MySQL 8.0.46 optimizer_demo
```

| 组件 | 版本/配置 |
| --- | --- |
| Web | Vite 7.0.6 dev server，`VITE_ENABLE_API_MOCK=false`，`/sql-api`、`/auth-api`、`/ai-api` 同源代理 |
| SQL service | 本仓库打包 JAR，JDK 17 运行、Java 8 字节码；`sql-editor.optimization.enabled=true`、`integration-ready=true`、显式 user/product 白名单、独立 AES-GCM 凭证密钥与游标密钥、`development-user-header=true`（本地 profile） |
| Agent Platform | bddf-agentscope，`dev` profile，`DATASOURCE_SERVICE_ENABLED=true`、`DATASOURCE_QUERY_ENABLED=false`（不挂载 `execute_query`） |
| MySQL | 常驻开发服务器，元数据库 `sqleditor_dev`，目标库 `optimizer_demo`（customers 2000 / orders 20000 / order_items 30000 行，仅主键，用于制造真实计划问题） |

## 2. 已验证场景与结果

| # | 场景 | 结果 |
| --- | --- | --- |
| 1 | 真实登录后 `GET /api/v1/session` 由后端白名单授予 `SQL_OPTIMIZE` | 通过：能力出现在真实 SessionResponse，导航入口可见 |
| 2 | 通过“数据源管理”真实创建目标数据源并测试连接 | 通过：`连接成功 · MySQL 8.0.46 · 619ms`，保存后写入元数据库 |
| 3 | 页面上提交 JOIN 扫描，Worker 调真实 Agent 并回收结构化报告 | 通过：202 → QUEUED → RUNNING → SUCCEEDED（约 27s），`reportSchemaVersion=1` |
| 4 | Agent 通过内部 API 向本服务取证（校验/计划/表结构/关系） | 通过：`sql_execution_history` 记录 5 条 `AI_AGENT_EXPLAIN` 实际 EXPLAIN；Agent 侧日志含 validate/explain/schema/relationships 工具调用 |
| 5 | 报告在页面按 verdict/问题/建议/证据缺口渲染，含只读 SQL 快照与关联建议 | 通过：真实报告 2012 字符，规则 #32 + EXPLAIN 证据 + 9 条 limitations |
| 6 | 关闭页面后任务继续执行 | 通过：任务在 RUNNING 时关闭页面，仍在服务端完成 SUCCEEDED |
| 7 | 换标签页/刷新后仍能读取同一任务（不依赖浏览器本地状态） | 通过：新标签页列表可见、详情可读、刷新保持 |
| 8 | 终态任务“再次扫描”产生新任务并关联原任务 | 通过：202 + Location，`sourceTaskId` 指向原任务，新任务同样 SUCCEEDED |
| 9 | 凭证失效：排队任务的 Bearer 在授权服务被真实登出后重启服务 | 通过：恢复队列后重新授权失败 → FAILED / `CREDENTIAL_EXPIRED`，报告与 summary 均为 null |
| 10 | 跨用户/产品隔离 | 通过：他人任务详情与列表均 404 `OPTIMIZATION_TASK_NOT_FOUND`（API 与页面“任务不存在或不可见。”一致） |
| 11 | 未授予能力的账号 | 通过：导航隐藏、页面提示无能力、直接 POST 返回 403 `SQL_OPTIMIZE_FORBIDDEN`（隐藏入口不替代后端校验） |
| 12 | 游标与分页契约 | 通过：`pageSize=2` 返回 `nextPageToken`；篡改游标与换筛选条件复用游标均返回 400 `INVALID_PAGE_TOKEN` |
| 13 | 失败任务重扫授权链路 | 通过：FAILED 任务仍以 200 返回详情，重扫使用当前请求凭证重新校验并返回 202 新任务，该任务真实执行成功 |
| 14 | 内部 Agent API 边界 | 通过：配对 key 单独调用 401；有效 Bearer + 错误/缺失 key 401；有效 Bearer + 配对 key 200（内部 key 不能替代用户身份） |

## 3. 任务与报告证据

下表来自一次完整运行；随后用入库的 `tools/optimizer-live/` 在全新栈上复跑了一遍第 2 节全部场景（含内部 API 边界四项断言），结果一致，复跑记录在 `/private/tmp/optimizer-live/`。

元数据库 `sqleditor_dev` 中的实际任务（2026-10-13）：

| 任务名 | 状态 | error_code | 由重扫产生 | 问题/建议/缺口 | 耗时 |
| --- | --- | --- | --- | --- | --- |
| 真实联调 JOIN 10141 | SUCCEEDED | — | 否 | 1 / 1 / 6 | 30s |
| 真实联调 JOIN 06587 | SUCCEEDED | — | 否 | 1 / 1 / 9 | 25s |
| 真实联调 JOIN 06587（重扫） | SUCCEEDED | — | 是 | 1 / 1 / 9 | 29s |
| 关页后继续（真实联调） | SUCCEEDED | — | 否 | 1 / 1 / 6 | 27s |
| 凭证过期（真实联调） | FAILED | CREDENTIAL_EXPIRED | 否 | 无报告 | — |
| 其他用户任务（真实联调） | FAILED | CREDENTIAL_EXPIRED | 否 | 无报告 | — |
| 凭证过期（真实联调）（重扫） | SUCCEEDED | — | 是 | 1 / 1 / 7 | 26s |

真实报告要点（`真实联调 JOIN 06587`）：

- verdict：`validate_sql` 返回 `valid=true, readOnly=true`、单语句、`tables=[orders, customers]`；仅 1 个有证据的性能问题。
- 问题：组织规则 #32（PERFORMANCE / 重要 / HIGH）。证据为 `explain_sql` 的 `fullScan=true`、`warnings=[{"code":"FULL_SCAN"}]`、`riskLevel=HIGH`，`orders` 的 `accessType=ALL`、`estimatedRows≈20297`——与本仓库 20000 行 orders 表一致。
- 建议：评估 `status/created_at/amount` 复合索引，并明确要求人工验证列顺序与写入影响，未直接给出 DDL。
- limitations（9 条）：组织规则 7/33 清单未定义、字符集未返回、无工作负载无法判定排序与临时结构、关联方式不适用等，均记为证据缺口而非“通过”。

## 4. 契约核对

- 创建/重扫：`202` + `Location` 指向新任务详情；重扫请求无请求体；幂等键作用域为 userId + productId + key。
- 状态与空值：QUEUED/RUNNING 无报告；SUCCEEDED 返回 `reportSchemaVersion=1` + report + summary；FAILED 返回安全 message 与 requestId，报告/summary 为 null。
- 失败任务查询仍为 HTTP 200；页面展示“扫描失败”与“请重新登录后再次扫描”，不伪造报告。
- 列表投影不含完整 SQL 与报告；`sqlPreview` 为截断预览。
- Agent 调用仅使用只读工具，未出现 `execute_query`；执行历史中只有 plan-only EXPLAIN，没有查询或 ANALYZE 记录。

## 5. 已知问题与观察

1. **上游模型结构化输出回退**：Agent Platform 对 `api.deepseek.com` 请求原生 `response_format` 时收到 400 `This response_format type is unavailable now`，随后按非结构化路径继续并仍产出合法 `structuredOutput`；本次 5 个成功任务均未受影响。属于 Agent Platform 与模型提供方的行为，不在本仓库修复范围。
2. **前端两个既有单测失败**：`web/src/api/executions.test.ts` 与 `web/src/api/phase-two.test.ts` 各有 1 个 CSV 导出测试失败（`csv.blob?.text is not a function`）。根因是 vitest 的 jsdom 环境用 jsdom 的 `Blob` 覆盖了 Node 的 `Blob`，而 jsdom 26.1.0 的 `Blob` 未实现 `text()`/`arrayBuffer()`，`Response.prototype.blob()` 因此返回无该方法的对象。被测代码（`web/src/api/executions.ts`、两个测试文件）自本次优化器工作之前就未改动，同一失败在原前端 worktree 交付提交上可复现，属既有环境问题而非本次回归；未删除测试或放宽断言。FE-05 记录曾记为 339 全通过，该结果在当前环境无法复现。
3. **会话缓存**：`GET /api/v1/session` 使用 60 秒鉴权上下文缓存，因此刚被登出的 Token 在该窗口内仍可读到会话信息；优化器 Worker 不受影响，它按设计走未缓存的重新授权，凭证过期在任务上表现为 `CREDENTIAL_EXPIRED`。
4. **limitations 偏多**：报告稳定输出 6–9 条证据缺口（组织规则清单未定义、无工作负载等），这是设计中的“未作结论”行为，不代表规则全部通过，也不代表执行失败。

## 6. 复现方式

```bash
# 1. 元数据库与目标库（可复用已有实例，勿使用生产库）
#    CREATE DATABASE sqleditor_dev;  CREATE DATABASE optimizer_demo;
# 2. 启动顺序：auth-service(8090) → SQL service(8080) → Agent Platform(8081) → Web(5173)
# 3. 按 tools/optimizer-live/README.md 注入环境变量后运行驱动脚本
node tools/optimizer-live/live-e2e.mjs          # 主链路：创建、轮询、报告、重扫、换页
node tools/optimizer-live/live-failures.mjs pageclose   # 关页继续 + 能力校验
node tools/optimizer-live/live-failures.mjs stage-expiry  # 队列中撤销凭证（需关闭调度器重启）
node tools/optimizer-live/live-failures.mjs verify        # 重启后恢复与契约核对
```

截图与运行记录（未入库）：`/private/tmp/optimizer-integration/`（本次验收）与 `/private/tmp/optimizer-live/`（复跑），均为 `live-01..05*.png`、`live-report.txt`、`live-failure.txt`、`live-result.json`、`failure-state.json`、`logs/`。

## 7. 验收当时未完成项与外部依赖

以下保留验收时的范围边界，后续状态统一维护在[生产待验收项](../sql-optimizer-production-readiness.md)。

| 项 | 状态 | 所需输入 |
| --- | --- | --- |
| 生产 `CurrentUserPort` | 未验收 | 部署层正式身份适配器；本次使用 `dev` 开发头身份，仅证明 Bearer + 回调链路 |
| 生产凭证托管政策 | 未验收 | 短期 Token 落库批准、真实 `tokenExpiresAt`、独立密钥来源与轮换流程、90 天保留确认 |
| Agent 会话状态保留 | 未验收 | 平台侧保留策略（`releaseSession` 不清除持久状态） |
| 生产开关与额度 | 未验收 | 生产白名单、Agent 地址、内部 caller key 配对与容量预算审批 |
