export type TaskStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export type CreateTaskRequest = {
  name?: string
  datasourceId: string
  database: string
  sql: string
}

export type TaskSummary = {
  policyIssueCount: number
  performanceIssueCount: number
  limitationCount: number
}

export type AcceptedTask = {
  id: string
  status: TaskStatus
  createdAt: string
}

export type TaskListItem = {
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

export type TaskError = {
  code: string
  message: string
  requestId: string
}

export type TaskDetail = {
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

export type TaskListResponse = {
  items: TaskListItem[]
  nextPageToken: string | null
}

export type OptimizationReport = {
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
