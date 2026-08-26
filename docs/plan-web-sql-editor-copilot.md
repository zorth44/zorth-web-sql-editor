# Plan：SQL 编辑器 Copilot（本仓库）

状态：草案。对应 sibling 仓库配合项：[plan-ai-server-sql-editor-copilot.md](./plan-ai-server-sql-editor-copilot.md)。

在 `/sql-editor` 增加右侧 Copilot：自然语言生成 SQL、一键插入当前页签、执行失败后一键修复。对标 Snowflake Copilot 的 Add / 报错旁修复，不把聊天当主界面。

本仓库几乎只动 `web/`。SQL service 契约不改。模型、Tool、只读试跑都在 `zorth-ai-service` 的 `POST /api/v1/ai/agent`。

## 1. 目标与非目标

**用户能完成：**

1. 打开右侧面板，在已绑定数据源 + NAMESPACE 的 SQL 页签上用自然语言要一条 SQL。
2. 回复里的 SQL 代码块可以 **插入编辑器**（追加到末尾）或 **插入并运行**。
3. 执行失败时，结果区有 **用 AI 修复**；修复后的 SQL **替换失败的那一条**。

**本仓库不做：**

- 自建 LLM、新的执行/元数据 API、Agent Tool。
- 行内 Cmd+K、幽灵补全、树上 @ 表 Scope。
- 欢迎页 / 表对象页签上的 Copilot。
- Agent 流式 SSE（等 AI 服务提供后再接）。
- 把 Agent 试跑结果渲染进结果网格（网格只反映用户在编辑器里跑的）。
- 自动执行 DML/DDL。

## 2. 布局与入口

左侧活动栏仍只切换「数据库 / 历史」。Copilot 在 **右边**，避免和资源树抢同一侧。

```text
[数据库|历史] │ 资源树/历史 │ 页签 + 工具栏        │ Copilot
              │            │ Monaco               │ 上下文条（只读）
              │            │ ───────────────────  │ 消息 + ```sql 按钮
              │            │ 结果 [用 AI 修复]    │ 输入框
```

三个入口打开同一块面板：

| 入口 | 行为 |
| --- | --- |
| 工具栏「Copilot」 | 开合右侧面板 |
| `Cmd/Ctrl+L` | 同上；避开已有的 Enter 运行 |
| 结果区「用 AI 修复」 | 打开面板并 **自动发送** 修复请求 |

无数据源或无 NAMESPACE：输入禁用，提示先在左侧选择。欢迎页、表对象页签不提供 Copilot。

面板开合是工作台级状态；**对话按 SQL 页签隔离**。切页签换对话。不写入 Session Storage。

## 3. 调用哪个接口

只调用 Agent，不调用 `/api/v1/ai/chat`（Chat 没有 Database Tools）。

```http
POST /api/v1/ai/agent
Authorization: Bearer <与 SQL 请求同一 Token>
Content-Type: application/json

{
  "message": "<上下文块 + 用户原话或修复模板>",
  "conversationId": "<当前 SQL 页签 id，供审计>",
  "datasourceId": "<页签 dataSourceId>",
  "database": "<页签 NAMESPACE>"
}
```

成功体：`{ "content": "...", "conversationId": "..." }`（以 AI 服务实际字段为准）。`content` 为 Markdown；插入按钮依赖其中的 ` ```sql ` 代码块。

Agent 今日无多轮记忆。每次请求由前端附带 **当前编辑器 SQL** 和 **最近一次失败信息**。用户先插入再追问「加上时间过滤」，模型看到的是编辑器里的语句，而不是服务端会话。

`message` 上限 10,000 字符。上下文必须截断。

## 4. 每次请求附带的上下文

用户只看到自己输入的那一句。下面这块静默拼进 `message` 前部。

| 字段 | 来源 | 截断 |
| --- | --- | --- |
| 方言 | 引擎 catalog（`mysql` / `pgsql` / GBase） | 短 |
| 数据源名 + NAMESPACE | 当前页签 | 短 |
| 当前 SQL | 有选区用选区，否则整页 | 约 4,000 字 |
| 失败语句 + 错误 | `statements[]` 中 `FAILED` 那条；否则页签 `error` | 错误约 1,000 字 |

并写明对模型的交付要求（不改 AI 服务也能先跑）：

- 给出的 SQL 必须放在 `sql` 代码块里。
- 可以只读试跑验证，但回答以 SQL 为主，不要把结果行当最终产物。

「用 AI 修复」的用户句为固定模板，例如：请修复下面这条失败的 SQL，给出改正后的完整语句。失败语句和错误已在上下文块中。

前端 **不** 把整库 DDL 塞进 prompt。查表结构由 Agent 的 `listTables` / `getTableSchema` 完成。

## 5. 插入与替换

从 `content` 解析所有 ` ```sql ` 块（无 language 标记但内容像 SQL 的块可作为兜底）。每块两个按钮：

- **插入**（主按钮）
- **插入并运行**（需 `SQL_EXECUTE`）

| 场景 | 写入方式 |
| --- | --- |
| 生成出的新语句 | **追加到编辑器末尾**，前面空一行（避免插进半句；对标 Snowflake Add） |
| 报错修复 | **替换** `StatementRun.sql` 在全文中的那一次出现；找不到或用户已改过编辑器则退回追加 |
| 编辑器为空 | 直接写入 |

插入后 focus 编辑器，**不自动执行**。「插入并运行」= 写入后走现有 `executeStatements`，来源仍是 `WEB_SQL_EDITOR`。

Agent 自己 `executeQuery` 的历史是 `AI_AGENT`，与手跑分开。结果网格只更新用户这次运行。

`SqlMonacoEditor` 现有 `insertAtCursor` 给资源树用。Copilot 需要 `appendSql` 和按文本替换失败语句。

## 6. 报错修复

`ResultGrid` 失败态目前只有红框和 `<pre>`。改为：

1. 单条失败：错误卡片上 **用 AI 修复**。
2. 脚本汇总：`FAILED` 行同样有按钮。
3. 点击：打开面板 → loading → 自动发 Agent 请求。
4. 返回代码块后，插入 = 替换失败语句。

正在跑 Agent、未绑定连接时按钮禁用。v1 用页签已有的失败 SQL 和 `error` 字符串即可；把 `sqlState` / `vendorErrorCode` 写入页签是加分项。

## 7. 工程改动（本仓库）

| 位置 | 做什么 |
| --- | --- |
| `web/src/env.ts`、`vite-env.d.ts`、`.env.example` | `VITE_AI_API_BASE`；开发用 `VITE_DEV_AI_PROXY_TARGET` |
| `web/vite.config.ts` | `/ai-api` → AI 服务（本地默认 `8081`），与 `/sql-api`、`/auth-api` 同模式 |
| `docs/local-development.md` | 补上 AI 代理变量；指向 AI 仓库联调文档 |
| `web/src/api/ai-agent.ts` | Bearer 与 SQL 请求同一套；`AbortController` 可取消 |
| `web/src/sql-editor/sql-fences.ts` | 从 Markdown 抽出 SQL 代码块 |
| `web/src/stores/copilot.ts` | 开合、按页签对话、inflight、conversationId |
| `web/src/components/copilot/CopilotPanel.vue` | 上下文条、消息、代码块按钮、输入、空态、loading |
| `web/src/pages/SqlEditorPage.vue` | 右侧 Splitpane、工具栏、快捷键、插入/替换接到 Monaco |
| `web/src/components/editor/SqlMonacoEditor.vue` | `appendSql`；按文本替换 |
| `web/src/components/result-grid/ResultGrid.vue` | 「用 AI 修复」 |
| `web/src/components/result-grid/ScriptResultPanel.vue` | 脚本失败行同样入口 |
| `web/src/mocks/handlers.ts` | Agent 成功（含代码块）、无代码块、校验失败 |
| 测试 | 抽 fence、追加/替换、无连接禁用、修复自动发送；E2E 走 MSW |

生产由网关把 AI 做成同源路径，**不要**把 `VITE_DEV_AI_PROXY_TARGET` 打进生产包（与现有 SQL/Auth 代理规则一致）。

## 8. 实现顺序

1. 环境变量、开发代理、Agent 客户端、MSW。右侧空面板：无连接禁用，有连接可开合。
2. 发消息、渲染 Markdown、抽出代码块、**插入追加**。
3. 结果区「用 AI 修复」+ **替换失败语句**。
4. 「插入并运行」。
5. 与 AI 服务按 [配合文档](./plan-ai-server-sql-editor-copilot.md) 联调真实库。白名单/提示词不齐时，线上会大量 `DATASOURCE_NOT_ALLOWED` 或插不进编辑器。

1–4 可只靠 MSW 在本仓库闭环。第 5 步依赖 sibling 仓库。

## 9. 验收

- 未选数据源或 NAMESPACE：不能发送。
- 绑定后提问：面板出现回复；含 ` ```sql ` 时可插入；插入后 Monaco 末尾为该 SQL，结果区不变。
- 「插入并运行」：编辑器有 SQL，结果区出现该次用户执行结果；历史 `source=WEB_SQL_EDITOR`。
- 故意跑失败：出现「用 AI 修复」；点过后面板自动请求；插入后失败语句被替换而不是再追加一份。
- 切 SQL 页签：对话与上下文跟着页签走。
- 取消进行中的请求：面板回到可输入，不插入半截内容。
- Mock 与真实 Agent 都走同一套插入/替换逻辑。

## 10. 风险

| 风险 | 处理 |
| --- | --- |
| 回复没有 SQL 代码块 | 插入按钮不出；空态提示「请让我用代码块给出 SQL」。依赖 AI 侧提示词加固 |
| Agent 同步 10–30 秒 | 面板 loading + 取消；不做假流式 |
| `message` 超 10,000 | 截断 SQL/错误；超限仍失败则提示缩短选区 |
| 静态数据源白名单 | 本仓库无法绕过；见 AI 服务 plan 第 1 项 |
| 修复时用户已改编辑器 | 替换找不到则追加，并提示已追加而非替换 |
