# SQL 优化器：前端实施方案

日期：2026-10-13。状态：待实施。负责 worktree：`feat/sql-optimizer-frontend`。

先读 [共享契约 v1](sql-optimizer-api-contract.md) 与 [并行实施入口](plan-sql-optimizer-async-tasks.md)。本分支拥有 `web/**`，不修改 `service/**`。直接使用 MSW 完成页面与流程，无须等待 Worker、数据库或生产认证接入。

## 前端目标与技术边界

交付“SQL 优化器”导航、创建单 SQL 任务、我的任务列表、任务详情、报告、失败展示与再次扫描。任务持久化和执行全部在后端；用户关页后前端无需维持请求。

沿用 Vue 3 + TypeScript、Vue Router、TanStack Vue Query、Pinia、Monaco、现有 sql-client 和主题组件。请求使用 sqlApiBase，不改 Copilot ai-agent，不新增直连 Agent/SSE 方案，不把任务当对话存储。

建议文件：

```text
web/src/pages/SqlOptimizerPage.vue
web/src/pages/SqlOptimizationTaskPage.vue
web/src/components/sql-optimizer/{TaskCreateForm,TaskList,OptimizationReport}.vue
web/src/api/sql-optimization.ts
web/src/sql-optimizer/{contracts,queries,report-model}.ts
web/src/mocks/ 下的优化器 handlers/fixtures
web/e2e/sql-optimizer.spec.ts
```

同时改 `web/src/router/index.ts`、`layouts/AppShell.vue` 和 `types/contracts.ts` 的前端 Capability。后端 SessionResponse 由另一个 worktree 负责，不在本分支改它。

## FE-01：类型、API 与 Mock

- [ ] 按共享契约建立 CreateTaskRequest、AcceptedTask、TaskListItem/Response、TaskDetail、OptimizationReport 类型。
- [ ] 实现 create/list/detail/retry 四个请求，retry 无 body，创建/重扫发送 Idempotency-Key。
- [ ] 请求通过现有 sql-client，沿用 401 处理和安全登录回跳；不提交用户、产品、Agent、sessionId。
- [ ] MSW 支持 QUEUED→RUNNING→终态，导航离开不丢模拟任务，幂等重放返回同一 id/当前状态。
- [ ] 夹具覆盖无问题、只有 limitations、混合问题与 linked suggestions、凭证过期、404/409/429、暂时网络错误。
- [ ] 为 Mock Session 添加 SQL_OPTIMIZE，缺能力时隐藏入口并阻止页面操作；真实授权由后端负责。

Mock 用于开发与测试，生产必须关闭。Mock 的数据持久性仅服务测试，不构成真实用户离页/重启执行保证。

## FE-02：导航、表单与编辑器草稿

- [ ] 顶栏入口与列表/详情路由，路由守卫保留任务详情登录后回跳。
- [ ] 数据源与命名空间选择复用现有接口，更换源清空旧 namespace，database 首版必填。
- [ ] Monaco 输入复用封装，按引擎选择语言；任务 name 可选，SQL 长度上限 20000。
- [ ] 使用现有 SQL 分句能力做多语句提示，后端权威校验；不引入分号 split 或正则解析器。
- [ ] 提交成功 202+id 后导航详情；校验/配额失败保留输入。
- [ ] 同一次提交保存 payload + key，在网络响应丢失时重发原 payload/key；编辑输入视作新操作并生成新 key，禁止复用旧 key 提交新 payload。
- [ ] 查询 mutation 默认不透明自动重试；用户恢复操作后用原 key 找回任务。不把“不知道是否提交成功”渲染成服务器任务失败。
- [ ] 可选“送去优化器”只带入当前单 SQL 草稿/坐标，不自动提交，不写 URL 或 localStorage。

详细交互如下（字段与 API 以共享契约为准）：

### 页面与用户流程

#### 入口与路由

- `AppShell` 顶部增加“SQL 优化器”。
- `/sql-optimizer`：创建入口、我的扫描任务、状态过滤与游标分页。
- `/sql-optimizer/tasks/:taskId`：任务状态、只读 SQL 快照、报告或失败原因。
- SQL 编辑器可增加“送去优化器”，将当前选中 SQL 或整段单 SQL、数据源和命名空间带入创建表单，但不自动提交。

跨页面草稿只在内存 store 中传递；URL 不携带 SQL 和 Token。详情页通过 taskId 从后端加载，支持刷新、收藏地址、重新登录后返回。首版不把 SQL 草稿持久化进 localStorage。

#### 创建表单

字段：可选任务名称（1–100 字符；空时由后端生成）、数据源、数据库/命名空间、Monaco SQL 输入。数据源列表和库列表复用现有受权限过滤接口；更换数据源后清除旧命名空间。

数据库选择沿用现有 editor 的引擎/命名空间语义。当前内部 Agent API 将 database/schema 视为同一 NAMESPACE 的别名，不允许不相等的双值；不能为 PostgreSQL 自创 database.schema 拼接协议。跨 schema 对象由 SQL 合法限定名表达，必要时提示证据范围。

提交前检查必填、长度和明显多语句；权威校验在后端，复用已有 `SqlSafetyEvaluator` / 引擎校验能力，不用按分号字符串拆分或正则实现 SQL 解析。创建阶段不取执行计划；Agent 执行时仍必须先 validate_sql。

点击提交后禁用重复点击；若网络响应丢失，使用相同 Idempotency-Key 重发并找回同一任务。只有收到 202 和 taskId 才显示提交成功，然后跳转任务详情，提示“任务已提交，可稍后在扫描任务中查看报告”。

#### 列表、状态与轮询

列表展示名称、数据源/命名空间快照、SQL 摘要、状态、创建/完成时间、问题数与证据缺口数。默认 `createdAt DESC, id DESC`，不在列表响应中携带完整 SQL 或报告。

有 QUEUED/RUNNING 任务时约 5 秒轮询，离开页面/浏览器后台暂停，回到页面主动刷新；终态详情停止轮询。服务端状态由 Vue Query 管理，查询键包含当前用户与产品；退出登录清空缓存。

只显示“排队中/分析中/已完成/失败”，不显示工具实时进度、预计剩余时间或虚构百分比。当前同步 Agent 接口无法提供这些信息。

#### 报告呈现

依次展示总体结论 verdict、问题 issues、建议 suggestions、证据缺口 limitations。

| 字段 | 展示规则 |
| --- | --- |
| category=POLICY | “规范问题”；priority 显示组织重要性；severity=null 不显示性能严重度 |
| category=PERFORMANCE | “性能问题”；severity 显示 HIGH/MEDIUM/LOW，同时单独展示 priority |
| ruleId | 组织规则编号；不从编号硬编码或重新判定规则 |
| evidence | 区分 SQL 片段与工具事实，按普通文本展示 |
| suggestions.issueIds | 点击定位到对应问题；仅文字建议，不提供直接执行/建索引按钮 |
| limitations | 明确显示“证据缺口/未作结论”，不能默认折叠到用户看不见 |

`issues=[]` 表示“本次未发现有证据支持的问题”。如果 limitations 非空，不展示绿色“完全通过”，也不声称性能最佳或全部规则通过。扫描完成表示获得有效报告，即使报告表明证据不足；不能把它等同于 SQL 检查通过。

内容通过 Vue 文本绑定展示，首版不使用 v-html/Markdown HTML。SQL 高亮只做展示。报告不包含优化后 SQL、实测提速百分比、全套原始工具调用或详细 plan 树，UI 不发明这些字段。

## FE-03：列表、详情与轮询

- [ ] 列表展示共享 TaskListItem：名称、坐标、sqlPreview、状态、时间、成功任务的 summary。
- [ ] status 为单值筛选，变更筛选重置游标；下一页用 nextPageToken，上一页用缓存的游标栈。
- [ ] query key 绑定 userId/productId/过滤/分页；退出清缓存，与现有 auth 行为衔接。
- [ ] QUEUED/RUNNING 详情约 5 秒轮询，终态停止；组件卸载或后台暂停，聚焦立即刷新。
- [ ] 列表空页/终态页无须持续轮询；恢复聚焦或手动刷新发现新变化。
- [ ] 网络暂不可用时保留最近服务端状态，显示查询错误；GET 到 FAILED 仍是 200 的业务任务失败。
- [ ] 404 统一不可见；403 无能力提示；不展示堆栈、凭证、内部地址。

## FE-04：报告与重扫

- [ ] SUCCEEDED 才渲染 report，先 verdict 后 issues/suggestions/limitations。
- [ ] POLICY 不显示性能 severity，PERFORMANCE 的 severity 与组织 priority 分开展示。
- [ ] suggestion 点击关联问题定位；evidence 区分 sql/tool，按普通文本安全展示。
- [ ] issues=[] + limitations 非空展示证据不足，不能绿色“全部通过”。不硬编码规则裁决、风险评分或提速比例。
- [ ] schemaVersion 不支持或响应形状明显不合法时显示报告格式异常，不从 message/Markdown 猜报告。
- [ ] SQL 快照只读，复制文字建议；不提供执行 SQL、改写 SQL、建删索引按钮。
- [ ] 终态“再次扫描”调用 retry，以当前登录凭证创建新任务，保留原报告；新操作 key 与原创建 key 不同。
- [ ] 重扫响应丢失沿用同一个 retry key 找回新任务；RUNNING/QUEUED 不提供重扫。

## FE-05：前端验收

- [ ] API 层检查地址、headers、retry 无 body、202/200/null/空数组与错误映射。
- [ ] 组件覆盖字段必填、数据源变更清坐标、重复点击与幂等恢复、游标重置、终态停轮询。
- [ ] 报告覆盖混合问题、只有缺口、没有问题、建议关联跳转、HTML/脚本字符串按文本展示。
- [ ] E2E：提交→离开→返回→报告；刷新详情；401 登录回跳；重扫创建新 id；网络响应丢失幂等找回；403/404/409/429。
- [ ] `cd web && pnpm typecheck && pnpm lint && pnpm test && pnpm build`，再运行优化器 Playwright 场景。

测试目前未跑，checkbox 表示未来实施任务。Mock 验收能证明页面行为，不能代替真实 Worker 或授权链路的联合验收。

## FE-06：真实联调交付清单

- [ ] 后端提供接口后关闭 API Mock，使用真实 SQL_OPTIMIZE 用户和可见数据源/命名空间。
- [ ] 对照共享契约核对字段/空值/状态/summary/分页/幂等/重扫，不临时在页面兼容另一套 DTO 掩盖契约差异。
- [ ] 验证关页后任务继续、返回看到报告、凭证过期提示重新登录后重扫。
- [ ] 功能未授予 capability 时页面入口与操作受控，直接 URL 访问不绕过后端权限。
- [ ] 更新本前端文档与前端 OpenSpec change；后端源码、共用 docs/契约不在本分支独自改动。

## 后端负责、前端只需正确呈现的事项

数据库、Worker、租约/重启恢复、Token 加密托管、Agent 认证、内部数据源回调、报告事实/引用验证、配额权威校验均归后端。前端无需保存后台凭证、维持长请求或补造分析进度。

UI 只承诺“关页后可回来查看”；不承诺退出撤销 Token 后任务仍完成。失败用服务端安全 message 呈现，并提供终态重扫。报告来自当前 Agent 的 17 条组织规则与实际取证范围，不表述为全库/所有规则扫描。
