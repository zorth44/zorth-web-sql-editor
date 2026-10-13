import { http, HttpResponse } from 'msw'
import { appEnv } from '@/env'
import { randomUUID } from '@/uuid'
import { mockDataSources, mockSession } from '@/mocks/fixtures'
import { cleanReport, limitedReport, mixedReport } from '@/mocks/sql-optimization-fixtures'
import type { CreateTaskRequest, TaskDetail, TaskListItem } from '@/sql-optimizer/contracts'
import { scanSql } from '@/sql-editor/sql'

type RecordTask = { owner: string; task: TaskDetail }
type Replay = { fingerprint: string; id: string }
const storageKey = 'zorth.mock.sql-optimization'
let records: RecordTask[] = []
let replays: Record<string, Replay> = {}
try {
  const raw = sessionStorage.getItem(storageKey)
  if (raw) ({ records, replays } = JSON.parse(raw))
} catch {
  /* Mock storage is optional. */
}
function persist(): void {
  try {
    sessionStorage.setItem(storageKey, JSON.stringify({ records, replays }))
  } catch {
    /* Mock only. */
  }
}
export function resetOptimizationMock(): void {
  records = []
  replays = {}
  persist()
}
const path = `${appEnv.sqlApiBase}/api/v1/sql-optimization/tasks`
function error(request: Request, status: number, code: string, message: string): Response {
  return HttpResponse.json(
    { code, message, requestId: request.headers.get('X-Request-Id') || randomUUID() },
    { status },
  )
}
function owner(request: Request): string {
  return request.headers.get('Authorization') === 'Bearer other-token' ? '1002:12' : '1001:12'
}
function authorize(request: Request): Response | null {
  const token = request.headers.get('Authorization')
  if (!token?.startsWith('Bearer ') || token === 'Bearer invalid-token')
    return error(request, 401, 'UNAUTHENTICATED', '登录已过期，请重新登录')
  if (token === 'Bearer no-optimize-token' || !mockSession.capabilities.includes('SQL_OPTIMIZE'))
    return error(request, 403, 'SQL_OPTIMIZE_FORBIDDEN', '当前账号没有 SQL 优化器能力。')
  return null
}
function advance(task: TaskDetail): TaskDetail {
  const elapsed = Date.now() - Date.parse(task.createdAt)
  if (task.status === 'QUEUED' && elapsed >= 1000) {
    task.status = 'RUNNING'
    task.startedAt = new Date(Date.parse(task.createdAt) + 1000).toISOString()
  }
  if (task.status === 'RUNNING' && elapsed >= 3000) {
    task.completedAt = new Date(Date.parse(task.createdAt) + 3000).toISOString()
    if (task.name === 'mock:expired') {
      task.status = 'FAILED'
      task.error = {
        code: 'CREDENTIAL_EXPIRED',
        message: '后台凭证已过期，请重新登录后再次扫描。',
        requestId: randomUUID(),
      }
    } else {
      task.status = 'SUCCEEDED'
      task.reportSchemaVersion = 1
      task.report = structuredClone(
        task.name === 'mock:clean'
          ? cleanReport
          : task.name === 'mock:limited'
            ? limitedReport
            : mixedReport,
      )
      task.summary = {
        policyIssueCount: task.report.issues.filter((i) => i.category === 'POLICY').length,
        performanceIssueCount: task.report.issues.filter((i) => i.category === 'PERFORMANCE')
          .length,
        limitationCount: task.report.limitations.length,
      }
    }
  }
  return task
}
function accepted(task: TaskDetail): Response {
  return HttpResponse.json(
    { id: task.id, status: task.status, createdAt: task.createdAt },
    { status: 202, headers: { Location: `${path}/${task.id}` } },
  )
}
function submit(
  request: Request,
  payload: CreateTaskRequest,
  sourceTaskId: string | null,
): Response {
  const key = request.headers.get('Idempotency-Key')
  if (!key || !/^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(key))
    return error(request, 400, 'VALIDATION_FAILED', '需要有效的幂等键。')
  const scope = `${owner(request)}:${key}`
  const fingerprint = JSON.stringify({
    operation: sourceTaskId ? 'retry' : 'create',
    sourceTaskId,
    ...payload,
    name: payload.name?.trim() || '',
  })
  const replay = replays[scope]
  if (replay) {
    if (replay.fingerprint !== fingerprint)
      return error(request, 409, 'IDEMPOTENCY_CONFLICT', '幂等键对应其他请求，请检查输入。')
    const task = records.find((r) => r.task.id === replay.id && r.owner === owner(request))?.task
    if (task) return accepted(advance(task))
  }
  if (payload.name === 'mock:conflict')
    return error(request, 409, 'IDEMPOTENCY_CONFLICT', '幂等操作冲突。')
  if (payload.name === 'mock:quota')
    return error(request, 429, 'OPTIMIZATION_QUOTA_EXCEEDED', '排队配额已满，请稍后提交。')
  const datasource = mockDataSources().find((d) => d.id === payload.datasourceId)
  if (!datasource) return error(request, 404, 'DATA_SOURCE_NOT_FOUND', '数据源不可见或不存在。')
  const scanner = scanSql(payload.sql || '')
  if (
    !payload.database?.trim() ||
    /[\u0000-\u001f\u007f]/.test(payload.database + payload.datasourceId) ||
    !payload.sql?.trim() ||
    payload.sql.length > 20000 ||
    (payload.name?.trim().length || 0) > 100 ||
    (scanner.reliable && scanner.statements.length > 1)
  )
    return error(request, 400, 'VALIDATION_FAILED', '请选择命名空间并输入一条完整 SQL。')
  const task: TaskDetail = {
    id: randomUUID(),
    name: payload.name?.trim() || 'SQL 扫描',
    datasourceId: payload.datasourceId,
    datasourceName: datasource.name,
    database: payload.database,
    sql: payload.sql,
    status: 'QUEUED',
    createdAt: new Date().toISOString(),
    startedAt: null,
    completedAt: null,
    sourceTaskId,
    reportSchemaVersion: null,
    report: null,
    summary: null,
    error: null,
  }
  records.push({ owner: owner(request), task })
  replays[scope] = { fingerprint, id: task.id }
  persist()
  // Simulate an accepted request whose response was lost, once per key.
  if (payload.name === 'mock:lost') return HttpResponse.error()
  return accepted(task)
}
function item(task: TaskDetail): TaskListItem {
  const {
    id,
    name,
    datasourceId,
    datasourceName,
    database,
    status,
    createdAt,
    startedAt,
    completedAt,
    summary,
  } = task
  return {
    id,
    name,
    datasourceId,
    datasourceName,
    database,
    status,
    createdAt,
    startedAt,
    completedAt,
    summary,
    sqlPreview: task.sql.replace(/[\r\n]/g, ' ').slice(0, 160),
  }
}
export const optimizationHandlers = [
  http.post(path, async ({ request }) => {
    const denied = authorize(request)
    if (denied) return denied
    return submit(request, (await request.json()) as CreateTaskRequest, null)
  }),
  http.get(path, ({ request }) => {
    const denied = authorize(request)
    if (denied) return denied
    const query = new URL(request.url).searchParams
    const status = query.get('status') || ''
    const size = Number(query.get('pageSize') || 20)
    if (
      (status && !['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(status)) ||
      !Number.isInteger(size) ||
      size < 1 ||
      size > 100
    )
      return error(request, 400, 'VALIDATION_FAILED', '列表参数无效。')
    let offset = 0
    const token = query.get('pageToken')
    if (token) {
      try {
        const cursor = JSON.parse(atob(token)) as { owner: string; status: string; offset: number }
        if (
          cursor.owner !== owner(request) ||
          cursor.status !== status ||
          !Number.isInteger(cursor.offset) ||
          cursor.offset < 0
        )
          throw new Error()
        offset = cursor.offset
      } catch {
        return error(request, 400, 'INVALID_PAGE_TOKEN', '分页游标无效，请刷新列表。')
      }
    }
    const all = records
      .filter((r) => r.owner === owner(request))
      .map((r) => advance(r.task))
      .filter((t) => !status || t.status === status)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id))
    persist()
    return HttpResponse.json({
      items: all.slice(offset, offset + size).map(item),
      nextPageToken:
        offset + size < all.length
          ? btoa(JSON.stringify({ owner: owner(request), status, offset: offset + size }))
          : null,
    })
  }),
  http.get(`${path}/:id`, ({ request, params }) => {
    const denied = authorize(request)
    if (denied) return denied
    const task = records.find((r) => r.owner === owner(request) && r.task.id === params.id)?.task
    if (!task) return error(request, 404, 'OPTIMIZATION_TASK_NOT_FOUND', '任务不存在或不可见。')
    const result = advance(task)
    persist()
    return HttpResponse.json(result)
  }),
  http.post(`${path}/:id/retry`, async ({ request, params }) => {
    const denied = authorize(request)
    if (denied) return denied
    if (await request.text())
      return error(request, 400, 'VALIDATION_FAILED', '重扫请求不得包含请求体。')
    const task = records.find((r) => r.owner === owner(request) && r.task.id === params.id)?.task
    if (!task) return error(request, 404, 'OPTIMIZATION_TASK_NOT_FOUND', '任务不存在或不可见。')
    advance(task)
    if (task.status === 'QUEUED' || task.status === 'RUNNING')
      return error(request, 409, 'TASK_NOT_TERMINAL', '任务尚未完成。')
    return submit(
      request,
      { name: task.name, datasourceId: task.datasourceId, database: task.database, sql: task.sql },
      task.id,
    )
  }),
]
