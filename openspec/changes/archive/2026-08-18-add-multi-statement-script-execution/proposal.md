## Why

编辑器目前一次只能执行一条语句：选区或光标所在语句可以运行，但选中一整段脚本或按“运行全部”时，多条语句会被前端提示“暂不支持批量执行”或被后端 `400 MULTI_STATEMENT_NOT_SUPPORTED` 拒绝。用户无法粘贴一段建表/初始化脚本直接跑完，只能逐条手工执行，这是当前工作台最影响日常使用的缺口。同时“运行选中”缺少可见入口，只写在按钮 `title` 里，用户不知道它存在。

## What Changes

- 前端新增脚本执行编排：用现有 `splitSql` 切分文本后**逐条串行**调用现有执行接口，每条语句生成独立 `executionId`。后端接口、契约与 `allowMultiQueries=false` 安全基线保持不变。
- 运行入口改为 Navicat 语义：编辑器无选区时按钮显示“运行”并执行**全部语句**，有选区时按钮文案切换为“运行选中”并执行**选区内全部语句**。
- `Cmd/Ctrl + Enter` 保留为“运行当前语句”（选区单条或光标所在语句），`Cmd/Ctrl + Shift + Enter` 改为脚本执行，不再提示“暂不支持批量执行”。
- 结果区改为多结果承载：每条语句一个 `Result N` 子页签，外加一个汇总页签展示每条语句的状态、耗时、影响行数与失败位置。
- 出错处理为“遇错即停”：某条语句失败后不再执行后续语句，保留已完成结果并标注失败语句位置，UI 明确说明 `autoCommit=true` 下已执行语句不可回滚。
- 切分失败（如未闭合引号）时退化为把整段文本当作单条语句发送，由后端给出权威错误，而不是前端自行拦截。
- 明确声明**不支持跨语句会话状态**：`SET @var`、`CREATE TEMPORARY TABLE`、`USE` 等不会在语句间延续，因为每条语句独立借用连接池连接。前端在脚本中检测到这类语句时给出显式警告。
- **BREAKING**（仅前端交互契约）：“运行”按钮在无选区时的语义从“执行光标所在单条语句”变为“执行编辑器全部语句”。

## Capabilities

### New Capabilities

无。本次改动不引入新能力，全部落在既有工作台能力内。

### Modified Capabilities

- `frontend-sql-editor-workbench`: `Monaco MySQL editing` 的执行快捷键场景改为脚本语义并移除“暂不支持批量执行”拦截；`Execution and cancellation interaction` 增加串行脚本编排、遇错即停与脚本级取消；`Accessible typed result presentation` 增加多结果子页签与汇总呈现；新增一条 requirement 声明脚本执行的会话状态与事务边界限制。

## Impact

- 受影响前端代码：`web/src/components/editor/SqlMonacoEditor.vue`（选区状态上抛、快捷键与动作调整）、`web/src/pages/SqlEditorPage.vue`（脚本串行编排、停止语义、按钮文案）、`web/src/stores/editor.ts`（`EditorTab` 由单结果扩展为语句结果集合）、`web/src/sql-editor/sql.ts`（切分失败兜底）、结果区新增多结果容器与汇总面板组件，复用现有 `ResultGrid`。
- 后端：无代码改动。`SqlStatementScanner.requireSingle` 与 `MULTI_STATEMENT_NOT_SUPPORTED` 保留为权威兜底，仅在前端切分失败时才会触发。
- 并发与配额：脚本串行执行期间整个页签视为一个运行中执行，仅占用 1 个并发额度，不触碰 `max-concurrent-per-user: 3`。
- 执行历史：一条语句一条历史记录，跑长脚本会显著增加历史条目数。本次不引入脚本分组 ID，作为后续观察项。
- 文档：`docs/frontend-development-spec.md` 的 7.4 Monaco 功能、7.5 执行交互、7.6 查询结果需同步更新。`docs/backend-development-spec.md` 无需改动。
