# SQL 优化器前后端共享契约 v1

更新：2026-10-13。状态：四个任务 API 已实现，本地真实链路已验收；生产未启用。

行为规格见[后端规格](../openspec/specs/backend-sql-optimization/spec.md)与[UI 规格](../openspec/specs/sql-optimizer-ui/spec.md)。本文补充精确线协议；维护入口见[SQL 优化器](subsystems/sql-optimizer.md)，生产状态见[待验收项](sql-optimizer-production-readiness.md)。

后端 DTO/接口与前端 TypeScript/MSW 按此协议同步维护。部署地址、后台凭证与 Agent 调用细节属于后端，不进入浏览器业务 DTO。契约变化必须双方同步，不能各自添加互不兼容的字段或状态。

## 1. 请求约定与功能范围

- 根路径：`/api/v1/sql-optimization/tasks`；前端使用 `sqlApiBase` 和现有 sql-client。
- 携带 `Authorization: Bearer ...`、`X-Request-Id`。创建/重扫还需 `Idempotency-Key: <UUID>`。
- 用户/产品从登录态读取，不提交 userId、productId、agentId、sessionId 或内部 service key。
- capability 为 `SQL_OPTIMIZE`：后端负责 SessionResponse 与真实授予/校验；前端负责类型、入口与页面访问体验。后端未开启时不授予该能力，Mock 可授予。
- 一条完整只读 SQL 一个任务；SQL 长度 1～20000 字符且不能纯空白，不自动拆分多语句。
- name 可省略或为空，后端生成；有值时 trim 后 1～100 字符。
- datasourceId 必填；database 首版要求选择/解析为明确的命名空间并提交非空值。沿用 editor 的 NAMESPACE，不自创 database.schema 协议。后端最终校验引擎语义与数据源可见性。
- 坐标不得含换行/控制字符。所有时间是 UTC ISO 8601 字符串，尚未发生的时间为 null。
- 必需字段完整返回；约定 null 的字段不能随意省略。无条目集合为 []。

## 2. 精确 DTO

```ts
type TaskStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

type CreateTaskRequest = {
  name?: string
  datasourceId: string
  database: string
  sql: string
}

type TaskSummary = {
  policyIssueCount: number
  performanceIssueCount: number
  limitationCount: number
}

type AcceptedTask = {
  id: string
  status: TaskStatus
  createdAt: string
}

type TaskListItem = {
  id: string
  name: string
  datasourceId: string
  datasourceName: string
  database: string
  sqlPreview: string
  status: TaskStatus
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  summary: TaskSummary | null
}

type TaskError = {
  code: string
  message: string
  requestId: string
}

type TaskDetail = {
  id: string
  name: string
  datasourceId: string
  datasourceName: string
  database: string
  sql: string
  status: TaskStatus
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  sourceTaskId: string | null
  reportSchemaVersion: 1 | null
  report: OptimizationReport | null
  summary: TaskSummary | null
  error: TaskError | null
}

type TaskListResponse = {
  items: TaskListItem[]
  nextPageToken: string | null
}

type OptimizationReport = {
  verdict: string
  issues: Array<{
    id: string
    ruleId: number
    category: 'POLICY' | 'PERFORMANCE'
    priority: '重要' | '一般'
    severity: 'HIGH' | 'MEDIUM' | 'LOW' | null
    evidence: Array<{ kind: 'sql' | 'tool'; content: string }>
    detail: string
  }>
  suggestions: Array<{ id: string; issueIds: string[]; detail: string }>
  limitations: Array<{ ruleId: number | null; reason: string }>
}
```

新增创建通常返回 QUEUED，但幂等重放可返回同一任务的当前状态，因此 AcceptedTask.status 不限定 QUEUED。

sqlPreview 由后端将换行转空格、截取前 160 字符；前端只展示为文本，不将其用于重扫。列表不能返回完整 SQL/报告。summary 只在 SUCCEEDED 非空，直接按报告条目计数，不用它计算风险总分。

## 3. 四个接口

| 方法 | 完整路径 | 请求 | 成功响应 |
| --- | --- | --- | --- |
| POST | `/api/v1/sql-optimization/tasks` | CreateTaskRequest + Idempotency-Key | 202 AcceptedTask + Location 指向详情 API |
| GET | `/api/v1/sql-optimization/tasks` | status/pageSize/pageToken 查询参数 | 200 TaskListResponse |
| GET | `/api/v1/sql-optimization/tasks/{taskId}` | 无请求体 | 200 TaskDetail |
| POST | `/api/v1/sql-optimization/tasks/{taskId}/retry` | 无请求体 + Idempotency-Key | 202 AcceptedTask + Location 指向新任务 |

列表 status 可省略；有值时是单个 TaskStatus。pageSize 默认 20，范围 1～100；pageToken 为不透明游标，与当前用户/产品/筛选条件绑定，非法或条件不匹配返回 400。顺序 `createdAt DESC, id DESC`。前端维护游标栈支持上一页/下一页，不假设 total 或数字页码。

创建先校验、写库，提交成功后才返回 202。幂等同键同请求返回原任务，不创建第二个任务；同键不同请求返回 409。指纹需覆盖操作类型、原任务 ID（retry 时）及规范化创建参数，SQL 保留原文。服务端唯一作用域为 userId + productId + key。

重扫仅允许 SUCCEEDED/FAILED，复用原任务的 SQL/坐标输入快照与名称，重新检查权限，使用当前请求的用户凭证，新任务记录 sourceTaskId。想修改输入则使用创建表单。幂等重放不重复新建，也不能被配额拒绝而使用户找不回已经创建的任务；身份与当前读权限仍必须验证。

## 4. 状态、空值与失败

| 状态 | report/schemaVersion/summary | error | 时间 |
| --- | --- | --- | --- |
| QUEUED | 全为 null | null | startedAt/completedAt=null |
| RUNNING | 全为 null | null | startedAt 非空，completedAt=null |
| SUCCEEDED | 全为非空，schemaVersion=1 | null | startedAt/completedAt 非空 |
| FAILED | 全为 null | 非空 | completedAt 非空，startedAt 可为 null |

QUEUED 可因凭证到期直接 FAILED；RUNNING 只能进入 SUCCEEDED/FAILED。终态不回到排队，不被重扫覆盖。报告包含 limitations 或没有 issues 仍为 SUCCEEDED，不表示全部规则通过。首版没有 CANCELLED、PARTIAL、百分比或实时工具阶段字段。

任务失败码基线：AGENT_TIMEOUT、AGENT_UNAVAILABLE、AGENT_AUTH_FAILED、REPORT_INVALID、REPORT_TOO_LARGE、CREDENTIAL_EXPIRED、CREDENTIAL_UNAVAILABLE、IDENTITY_CHANGED、DATA_SOURCE_UNAVAILABLE、WORKER_LOST、INTERNAL_ERROR。后端将上游错误映射为这些业务分类；前端展示服务端安全 message，并对未知 code 提供通用回退，不据错误码自动重发任务。

HTTP 请求失败沿用本项目 ApiErrorBody，不与 TaskDetail.error 混用：

| HTTP | 建议 code | 行为 |
| --- | --- | --- |
| 400 | 沿用 VALIDATION_FAILED，游标错误 INVALID_PAGE_TOKEN | 展示字段/请求问题，不创建任务 |
| 401 | UNAUTHENTICATED | 沿用登录跳转与安全回跳 |
| 403 | SQL_OPTIMIZE_FORBIDDEN | 页面无能力提示；隐藏入口不能替代后端校验 |
| 404 | OPTIMIZATION_TASK_NOT_FOUND / DATA_SOURCE_NOT_FOUND | 不存在与越权统一 |
| 409 | IDEMPOTENCY_CONFLICT / TASK_NOT_TERMINAL | 提示冲突，不隐式修改原任务 |
| 429 | OPTIMIZATION_QUOTA_EXCEEDED | 告知排队配额满，不循环重试创建 |

GET 查询到 FAILED 任务仍为 HTTP 200。暂时网络故障不等于任务失败，前端应保留已知状态并提示刷新。

## 5. 契约夹具与联合验证

前端 MSW 与后端契约测试各自保留对应目录中的夹具，遵循相同场景而不竞争共享 fixture 文件：QUEUED、RUNNING、无问题报告、POLICY + PERFORMANCE + linked suggestions、只有 limitations 的报告、凭证过期、越权 404、配额 429、幂等重放与冲突。

联合核对 JSON 字段/空值、状态枚举、retry 无请求体、列表游标、202/Location、HTTP 失败与任务失败的区别。真实联调关闭 Mock，验证刷新和离页后能够读取同一任务，前后端均不得依赖客户端 sessionId 或前端本地持久任务。
