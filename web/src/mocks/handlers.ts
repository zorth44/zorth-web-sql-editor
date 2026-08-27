import { delay, http, HttpResponse } from 'msw'
import { appEnv } from '@/env'
import { initialDataSources, mockDataSources, mockSession } from '@/mocks/fixtures'
import { mockEngineCatalog } from '@/mocks/engines'
import type { AgentRequest } from '@/api/ai-agent'
import type {
  ApiErrorBody,
  ConnectionFields,
  ConnectionTestResult,
  CreateDataSourceRequest,
  DataSourceDetail,
  DataSourceListItem,
  UpdateDataSourceRequest,
  HistorySummary,
  ScriptDetail,
  ScriptSummary,
  ScriptWriteRequest,
  SqlExecutionRequest,
} from '@/types/contracts'

const sql = (path: string) => `${appEnv.sqlApiBase}${path}`
const auth = (path: string) => `${appEnv.authApiBase}${path}`
const ai = (path: string) => `${appEnv.aiApiBase}${path}`
const sqlRpc = (pattern: string) =>
  new RegExp(`${appEnv.sqlApiBase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}${pattern}$`)
function rpcId(request: Request, suffix: ':test' | ':cancel'): string {
  const name = new URL(request.url).pathname.split('/').pop() || ''
  return decodeURIComponent(name.endsWith(suffix) ? name.slice(0, -suffix.length) : name)
}
const mockHistory: HistorySummary[] = []
const mockScripts: ScriptDetail[] = loadMockScripts()
const cancelledExecutions = new Set<string>()

interface MockAgentMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  tools?: { id: string; toolName: string; status: 'STARTED' | 'SUCCESS' | 'FAILURE' }[]
  createdAt: string
}

interface MockAgentConversation {
  id: string
  userId: string
  title: string
  datasourceId: string | null
  database: string | null
  updatedAt: string
  messages: MockAgentMessage[]
}

let agentConversations: MockAgentConversation[] = []

export function resetAgentConversations(): void {
  agentConversations = []
}

export function resetMockScripts(): void {
  mockScripts.splice(0, mockScripts.length)
  try {
    sessionStorage.removeItem('zorth.mock.sql-scripts')
  } catch {
    /* ignore */
  }
}

const error = (status: number, code: string, message: string, details?: ApiErrorBody['details']) =>
  HttpResponse.json<ApiErrorBody>(
    { requestId: crypto.randomUUID(), code, message, ...(details ? { details } : {}) },
    { status },
  )

function authorized(request: Request): Response | null {
  const header = request.headers.get('Authorization')
  if (!header?.startsWith('Bearer ') || header === 'Bearer invalid-token')
    return error(401, 'UNAUTHENTICATED', '登录已过期，请重新登录')
  return null
}

function mockUserId(request: Request): string {
  const token = request.headers.get('Authorization')?.slice('Bearer '.length) || ''
  return token === 'other-token' ? '1002' : '1001'
}

function clipTitle(text: string): string {
  const trimmed = text.trim()
  return trimmed.length <= 80 ? trimmed : `${trimmed.slice(0, 80)}…`
}

function summarySql(text: string): string {
  const compact = text.replace(/\s+/g, ' ').trim()
  return compact.length <= 240 ? compact : `${compact.slice(0, 240)}…`
}

function loadMockScripts(): ScriptDetail[] {
  try {
    const raw = sessionStorage.getItem('zorth.mock.sql-scripts')
    return raw ? (JSON.parse(raw) as ScriptDetail[]) : []
  } catch {
    return []
  }
}

function persistMockScripts(): void {
  try {
    sessionStorage.setItem('zorth.mock.sql-scripts', JSON.stringify(mockScripts))
  } catch {
    /* ignore quota in tests */
  }
}

function visibleUserText(body: AgentRequest): string {
  return body.userText?.trim() || body.message
}

function ownedConversation(id: string, userId: string): MockAgentConversation | undefined {
  return agentConversations.find((item) => item.id === id && item.userId === userId)
}

function conversationPayload(item: MockAgentConversation) {
  return {
    id: item.id,
    title: item.title,
    datasourceId: item.datasourceId,
    database: item.database,
    updatedAt: item.updatedAt,
  }
}

function agentReply(
  body: AgentRequest,
  previousUserText?: string,
): { content: string; conversationId: string } | Response {
  if (!body.message?.trim() || !body.datasourceId || !body.database) {
    return error(400, 'VALIDATION_FAILED', '请求参数不合法')
  }
  if (body.message.includes('__FAIL__')) return error(400, 'VALIDATION_FAILED', 'message 超限')
  const conversationId = body.conversationId || crypto.randomUUID()
  if (body.message.includes('__NO_SQL__')) {
    return { content: '当前没有可插入的 SQL。', conversationId }
  }
  if (body.message.includes('请修复')) {
    return {
      content: '已改正未知列问题：\n\n```sql\nSELECT id, amount FROM order_item;\n```',
      conversationId,
    }
  }
  if (previousUserText) {
    return {
      content: `接着「${previousUserText}」，可以再加上过滤：\n\n\`\`\`sql\nSELECT id, amount FROM order_item WHERE created_at >= CURRENT_DATE;\n\`\`\``,
      conversationId,
    }
  }
  return {
    content:
      '可以用下面的语句查询订单：\n\n```sql\nSELECT id, amount FROM order_item LIMIT 20;\n```',
    conversationId,
  }
}

function persistAgentTurn(
  userId: string,
  body: AgentRequest,
  reply: { content: string; conversationId: string },
): Response | null {
  const existing = agentConversations.find((item) => item.id === reply.conversationId)
  if (existing && existing.userId !== userId) {
    return error(404, 'CONVERSATION_NOT_FOUND', '对话不存在')
  }
  const now = new Date().toISOString()
  const userContent = visibleUserText(body)
  const userMessage: MockAgentMessage = {
    id: crypto.randomUUID(),
    role: 'user',
    content: userContent,
    createdAt: now,
  }
  const assistantMessage: MockAgentMessage = {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: reply.content,
    tools: [{ id: crypto.randomUUID(), toolName: 'listTables', status: 'SUCCESS' }],
    createdAt: now,
  }
  if (!existing) {
    agentConversations.unshift({
      id: reply.conversationId,
      userId,
      title: clipTitle(userContent),
      datasourceId: body.datasourceId,
      database: body.database,
      updatedAt: now,
      messages: [userMessage, assistantMessage],
    })
    return null
  }
  existing.datasourceId = body.datasourceId
  existing.database = body.database
  existing.updatedAt = now
  existing.messages.push(userMessage, assistantMessage)
  return null
}

function resolveAgent(request: Request, body: AgentRequest) {
  const userId = mockUserId(request)
  if (body.conversationId) {
    const found = agentConversations.find((item) => item.id === body.conversationId)
    if (found && found.userId !== userId) {
      return error(404, 'CONVERSATION_NOT_FOUND', '对话不存在')
    }
  }
  const current = body.conversationId ? ownedConversation(body.conversationId, userId) : undefined
  const previousUser = [...(current?.messages || [])].reverse().find((item) => item.role === 'user')
  const reply = agentReply(body, previousUser?.content)
  if (isHttpResponse(reply)) return reply
  const denied = persistAgentTurn(userId, body, reply)
  if (denied) return denied
  return reply
}

function isHttpResponse(
  value: { content: string; conversationId: string } | Response,
): value is Response {
  return value instanceof Response
}

function agentSse(reply: { content: string; conversationId: string }): Response {
  const encoder = new TextEncoder()
  const frames = [
    { event: 'start', data: { type: 'start', conversationId: reply.conversationId } },
    { event: 'tool', data: { type: 'tool', toolName: 'listTables', status: 'STARTED' } },
    { event: 'tool', data: { type: 'tool', toolName: 'listTables', status: 'SUCCESS' } },
    { event: 'delta', data: { type: 'delta', content: reply.content } },
    { event: 'completed', data: { type: 'completed', conversationId: reply.conversationId } },
  ]
  const stream = new ReadableStream({
    start(controller) {
      for (const frame of frames) {
        controller.enqueue(
          encoder.encode(`event:${frame.event}\ndata:${JSON.stringify(frame.data)}\n\n`),
        )
      }
      controller.close()
    },
  })
  return new HttpResponse(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
    },
  })
}

function listItem(detail: DataSourceDetail): DataSourceListItem {
  return {
    id: detail.id,
    name: detail.name,
    engine: detail.engine,
    host: detail.host,
    port: detail.port,
    username: detail.username,
    passwordConfigured: detail.passwordConfigured,
    defaultDatabase: detail.defaultDatabase,
    sslMode: detail.sslMode,
    lastTestStatus: detail.lastTestStatus,
    lastTestAt: detail.lastTestAt,
    version: detail.version,
    updatedBy: detail.updatedBy,
    updatedByName: detail.updatedByName,
    updatedAt: detail.updatedAt,
  }
}

function testResult(fields: Partial<ConnectionFields>): ConnectionTestResult {
  if (fields.host?.includes('timeout'))
    return {
      status: 'FAILED',
      serverVersion: null,
      durationMs: (fields.connectTimeoutSeconds || 10) * 1000,
      message: '连接超时，请检查主机、端口和网络策略',
      failureCode: 'CONNECTION_TIMEOUT',
    }
  if (fields.host?.includes('fail'))
    return {
      status: 'FAILED',
      serverVersion: null,
      durationMs: 24,
      message: '身份验证失败',
      failureCode: 'AUTHENTICATION_FAILED',
    }
  return {
    status: 'SUCCESS',
    serverVersion: '8.0.36',
    durationMs: 128,
    message: '连接成功',
    failureCode: null,
  }
}

const allowedCreateKeys = new Set([
  'name',
  'engine',
  'host',
  'port',
  'username',
  'password',
  'defaultDatabase',
  'sslMode',
  'connectTimeoutSeconds',
  'properties',
  'description',
])

export const handlers = [
  http.post(auth('/ldap/login'), async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>
    if (!body.username || !body.password)
      return HttpResponse.json({
        code: 500,
        msg: '用户名或密码不能为空',
        pwd: 'must-never-propagate',
      })
    if (body.username === 'failed')
      return HttpResponse.json({
        code: 500,
        msg: '用户名或密码错误',
        ldapUser: { pwd: 'sensitive' },
      })
    if (body.username === 'bind')
      return HttpResponse.json({ code: 200, needBind: true, ldapUser: { pwd: 'sensitive' } })
    if (body.username === 'multi' && !body.selectUserId)
      return HttpResponse.json({
        code: 200,
        needSelectAccount: true,
        bindAccounts: [
          { id: '1001', username: 'zhangsan', displayName: '张三' },
          { userId: '1002', userName: 'lisi', nickName: '李四' },
        ],
        ldapUser: { pwd: 'sensitive' },
      })
    return HttpResponse.json({
      code: 200,
      token: 'mock-token',
      ldapUser: { pwd: 'sensitive' },
      pwd: 'sensitive',
    })
  }),
  http.post(auth('/logout'), () => HttpResponse.json({ code: 200, msg: '退出成功' })),
  http.get(
    sql('/api/v1/session'),
    ({ request }) => authorized(request) || HttpResponse.json(mockSession),
  ),
  http.get(sql('/api/v1/engines'), ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    return HttpResponse.json(mockEngineCatalog)
  }),
  http.get(sql('/api/v1/data-sources'), ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const url = new URL(request.url)
    const keyword = (url.searchParams.get('keyword') || '').toLowerCase()
    if (keyword === '__401__') return error(401, 'UNAUTHENTICATED', '登录已过期，请重新登录')
    if (keyword === '__empty__') return HttpResponse.json({ items: [], nextPageToken: null })
    const pageSize = Math.min(Number(url.searchParams.get('pageSize') || 20), 100)
    const offset =
      Number((url.searchParams.get('pageToken') || 'cursor:0').replace('cursor:', '')) || 0
    const filtered = mockDataSources().filter(
      (item) =>
        !keyword ||
        item.name.toLowerCase().includes(keyword) ||
        item.host.toLowerCase().includes(keyword),
    )
    return HttpResponse.json({
      items: filtered.slice(offset, offset + pageSize).map(listItem),
      nextPageToken: offset + pageSize < filtered.length ? `cursor:${offset + pageSize}` : null,
    })
  }),
  http.get(sql('/api/v1/data-sources/:id'), ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    if (params.id === 'invisible' || params.id === 'missing')
      return error(404, 'DATA_SOURCE_NOT_FOUND', '数据源不存在或已不可见')
    const detail = mockDataSources().find((item) => item.id === params.id)
    return detail
      ? HttpResponse.json(detail)
      : error(404, 'DATA_SOURCE_NOT_FOUND', '数据源不存在或已不可见')
  }),
  http.post(sql('/api/v1/data-sources'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const raw = (await request.json()) as Record<string, unknown>
    const unexpected = Object.keys(raw).find((key) => !allowedCreateKeys.has(key))
    if (unexpected || !raw.name)
      return error(400, 'VALIDATION_FAILED', '请求参数不合法', {
        fieldErrors: [
          {
            field: unexpected || 'name',
            code: 'INVALID',
            message: unexpected ? '不允许提交该字段' : '请输入数据源名称',
          },
        ],
      })
    const body = raw as unknown as CreateDataSourceRequest
    const timestamp = new Date().toISOString()
    const detail: DataSourceDetail = {
      id: crypto.randomUUID(),
      name: body.name,
      engine: body.engine,
      host: body.host,
      port: body.port,
      username: body.username,
      passwordConfigured: true,
      defaultDatabase: body.defaultDatabase,
      sslMode: body.sslMode,
      connectTimeoutSeconds: body.connectTimeoutSeconds,
      properties: { ...body.properties },
      description: body.description,
      lastTestStatus: null,
      lastTestAt: null,
      lastTestMessage: null,
      version: 1,
      createdBy: '1001',
      createdByName: '张三',
      createdAt: timestamp,
      updatedBy: '1001',
      updatedByName: '张三',
      updatedAt: timestamp,
    }
    mockDataSources().unshift(detail)
    return HttpResponse.json(detail, {
      status: 201,
      headers: { Location: `/api/v1/data-sources/${detail.id}` },
    })
  }),
  http.put(sql('/api/v1/data-sources/:id'), async ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const index = mockDataSources().findIndex((item) => item.id === params.id)
    if (index < 0) return error(404, 'DATA_SOURCE_NOT_FOUND', '数据源不存在或已不可见')
    const body = (await request.json()) as UpdateDataSourceRequest
    const current = mockDataSources()[index]!
    if (params.id === 'ds-conflict' || body.version !== current.version)
      return error(409, 'VERSION_CONFLICT', '数据源已被其他用户更新', {
        currentVersion: current.version + 1,
        currentUpdatedAt: '2026-08-13T07:00:00Z',
        currentUpdatedByName: '王五',
      })
    const updated: DataSourceDetail = {
      ...current,
      ...body,
      passwordConfigured: current.passwordConfigured || Boolean(body.password),
      version: current.version + 1,
      updatedBy: '1001',
      updatedByName: '张三',
      updatedAt: new Date().toISOString(),
    }
    delete (updated as Partial<UpdateDataSourceRequest>).password
    mockDataSources()[index] = updated
    return HttpResponse.json(updated)
  }),
  http.post(sqlRpc('/api/v1/data-sources:test'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const body = (await request.json()) as ConnectionFields
    if (!body.password)
      return error(400, 'VALIDATION_FAILED', '请求参数不合法', {
        fieldErrors: [{ field: 'password', code: 'REQUIRED', message: '请输入密码' }],
      })
    return HttpResponse.json(testResult(body))
  }),
  http.post(sqlRpc('/api/v1/data-sources/[^/]+:test'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const detail = mockDataSources().find((item) => item.id === rpcId(request, ':test'))
    if (!detail) return error(404, 'DATA_SOURCE_NOT_FOUND', '数据源不存在或已不可见')
    const text = await request.text()
    const result = testResult(text ? (JSON.parse(text) as ConnectionFields) : detail)
    if (!text) {
      detail.lastTestStatus = result.status
      detail.lastTestAt = new Date().toISOString()
      detail.lastTestMessage = result.message
    }
    return HttpResponse.json(result)
  }),
  http.delete(sql('/api/v1/data-sources/:id'), ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    if (params.id === 'ds-in-use')
      return error(409, 'DATA_SOURCE_IN_USE', '数据源正在使用中', { runningTaskCount: 2 })
    const index = mockDataSources().findIndex((item) => item.id === params.id)
    if (index < 0) return error(404, 'DATA_SOURCE_NOT_FOUND', '数据源不存在或已不可见')
    mockDataSources().splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
  http.get(sql('/api/v1/data-sources/:id/databases'), ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    if (!mockDataSources().some((item) => item.id === params.id))
      return error(404, 'DATA_SOURCE_NOT_FOUND', '数据源不存在或已不可见')
    return HttpResponse.json({
      items: [
        { name: 'orders', kind: 'NAMESPACE' },
        { name: 'analytics', kind: 'NAMESPACE' },
      ],
      nextPageToken: null,
    })
  }),
  http.get(sql('/api/v1/data-sources/:id/tables'), ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const database = new URL(request.url).searchParams.get('database') || 'orders'
    return HttpResponse.json({
      items: [
        { database, name: 'order_item', type: 'TABLE', comment: '订单明细' },
        { database, name: 'order_view', type: 'VIEW', comment: '订单视图' },
      ],
      nextPageToken: null,
    })
  }),
  http.get(sql('/api/v1/data-sources/:id/table-detail'), ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const url = new URL(request.url),
      database = url.searchParams.get('database') || 'orders',
      table = url.searchParams.get('table') || 'order_item'
    return HttpResponse.json({
      database,
      table,
      columns: [
        {
          name: 'id',
          typeName: 'BIGINT',
          jdbcType: 'BIGINT',
          length: 20,
          precision: 20,
          scale: 0,
          nullable: false,
          defaultValue: null,
          extra: 'auto_increment',
          comment: '主键',
          ordinal: 1,
          primaryKey: true,
        },
        {
          name: 'amount',
          typeName: 'DECIMAL',
          jdbcType: 'DECIMAL',
          length: 12,
          precision: 12,
          scale: 2,
          nullable: false,
          defaultValue: '0.00',
          extra: '',
          comment: '金额',
          ordinal: 2,
          primaryKey: false,
        },
      ],
      primaryKey: { name: 'PRIMARY', columns: ['id'] },
      indexes: [{ name: 'PRIMARY', unique: true, type: 'OTHER', columns: ['id'] }],
      ddl: `CREATE TABLE \`${table}\` (
  \`id\` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  \`amount\` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '金额',
  PRIMARY KEY (\`id\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细'`,
    })
  }),
  http.post(sql('/api/v1/sql/executions'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const body = (await request.json()) as SqlExecutionRequest
    const upper = body.statement.trim().toUpperCase()
    const startedAt = new Date().toISOString()
    let resultKind: 'RESULT_SET' | 'UPDATE_COUNT' | 'DDL' = 'RESULT_SET'
    let statementType: HistorySummary['statementType'] = 'SELECT'
    if (upper.includes('MOCK_ERROR')) {
      mockHistory.unshift({
        id: body.executionId,
        dataSourceId: body.dataSourceId,
        dataSourceName: '订单测试库',
        database: body.database,
        operation: 'EXECUTE',
        source: body.source ?? 'WEB_SQL_EDITOR',
        statementSummary: body.statement,
        statementType: 'SELECT',
        status: 'FAILED',
        resultKind: null,
        returnedRows: null,
        affectedRows: null,
        durationMs: 18,
        truncated: false,
        startedAt,
        finishedAt: new Date().toISOString(),
      })
      return error(422, 'SQL_EXECUTION_FAILED', "Table 'orders.mock_error' doesn't exist", {
        executionId: body.executionId,
        sqlState: '42S02',
        vendorErrorCode: 1146,
      })
    }
    if (upper.includes('SLEEP')) {
      await delay(1500)
      if (cancelledExecutions.has(body.executionId))
        return error(409, 'SQL_EXECUTION_CANCELLED', 'SQL 执行已取消', {
          executionId: body.executionId,
        })
    }
    if (/^(CREATE|ALTER|DROP|TRUNCATE|RENAME)/.test(upper)) {
      resultKind = 'DDL'
      statementType = 'DDL'
    } else if (/^(INSERT|UPDATE|DELETE|REPLACE)/.test(upper)) {
      resultKind = 'UPDATE_COUNT'
      statementType = (upper.match(/^\w+/)?.[0] || 'OTHER') as HistorySummary['statementType']
    }
    const summary: HistorySummary = {
      id: body.executionId,
      dataSourceId: body.dataSourceId,
      dataSourceName: '订单测试库',
      database: body.database,
      operation: 'EXECUTE',
      source: body.source ?? 'WEB_SQL_EDITOR',
      statementSummary: body.statement,
      statementType,
      status: 'SUCCESS',
      resultKind,
      returnedRows: resultKind === 'RESULT_SET' ? 3 : null,
      affectedRows: resultKind === 'UPDATE_COUNT' ? 1 : null,
      durationMs: 28,
      truncated: false,
      startedAt,
      finishedAt: new Date().toISOString(),
    }
    mockHistory.unshift(summary)
    if (resultKind === 'RESULT_SET')
      return HttpResponse.json({
        executionId: body.executionId,
        kind: 'RESULT_SET',
        columns: [
          { name: 'id', label: 'id', jdbcType: 'BIGINT', typeName: 'BIGINT' },
          { name: 'amount', label: 'amount', jdbcType: 'DECIMAL', typeName: 'DECIMAL' },
          { name: 'note', label: 'note', jdbcType: 'VARCHAR', typeName: 'VARCHAR' },
        ],
        rows: [
          ['9007199254740993', '12.30', '中文,逗号'],
          ['2', '0.10', null],
          ['3', '99.00', { binary: true, size: 12, base64: null }],
        ],
        rowCount: 3,
        truncated: false,
        durationMs: 28,
      })
    return HttpResponse.json({
      executionId: body.executionId,
      kind: resultKind,
      affectedRows: resultKind === 'DDL' ? null : 1,
      durationMs: 28,
      message: '执行成功',
    })
  }),
  http.post(sqlRpc('/api/v1/sql/executions/[^/]+:cancel'), ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    cancelledExecutions.add(rpcId(request, ':cancel'))
    return new HttpResponse(null, { status: 202 })
  }),
  http.post(sql('/api/v1/sql/exports'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const body = (await request.json()) as { executionId: string }
    if (
      !mockHistory.some((item) => item.id === body.executionId && item.resultKind === 'RESULT_SET')
    )
      return error(400, 'SQL_NOT_EXPORTABLE', '该执行结果不可导出')
    return new HttpResponse('\ufeffid,note\r\n1,"中文,逗号"\r\n2,"\'=SUM(A1:A2)"\r\n', {
      headers: {
        'Content-Type': 'text/csv; charset=utf-8',
        'Content-Disposition': 'attachment; filename="mock-orders.csv"',
      },
    })
  }),
  http.get(sql('/api/v1/sql/history'), ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const q = (new URL(request.url).searchParams.get('keyword') || '').toLowerCase()
    return HttpResponse.json({
      items: mockHistory.filter((item) => item.statementSummary.toLowerCase().includes(q)),
      nextPageToken: null,
    })
  }),
  http.get(ai('/api/v1/ai/agent/conversations'), ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const userId = mockUserId(request)
    const items = agentConversations
      .filter((item) => item.userId === userId)
      .sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1))
      .slice(0, 50)
      .map(conversationPayload)
    return HttpResponse.json({ items })
  }),
  http.get(ai('/api/v1/ai/agent/conversations/:id'), ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const item = ownedConversation(String(params.id), mockUserId(request))
    if (!item) return error(404, 'CONVERSATION_NOT_FOUND', '对话不存在')
    return HttpResponse.json({ ...conversationPayload(item), messages: item.messages })
  }),
  http.delete(ai('/api/v1/ai/agent/conversations/:id'), ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const id = String(params.id)
    const userId = mockUserId(request)
    const index = agentConversations.findIndex((item) => item.id === id && item.userId === userId)
    if (index < 0) return error(404, 'CONVERSATION_NOT_FOUND', '对话不存在')
    agentConversations.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
  http.post(ai('/api/v1/ai/agent'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const body = (await request.json()) as AgentRequest
    const reply = resolveAgent(request, body)
    if (isHttpResponse(reply)) return reply
    if (body.message.includes('__SLOW__')) await delay(2000)
    return HttpResponse.json(reply)
  }),
  http.post(ai('/api/v1/ai/agent/stream'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const body = (await request.json()) as AgentRequest
    const reply = resolveAgent(request, body)
    if (isHttpResponse(reply)) return reply
    if (body.message.includes('__SLOW__')) await delay(2000)
    return agentSse(reply)
  }),
  http.get(sql('/api/v1/sql/history/:id'), ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const item = mockHistory.find((entry) => entry.id === params.id)
    return item
      ? HttpResponse.json({
          ...item,
          statement: item.statementSummary,
          sqlState: null,
          vendorErrorCode: null,
          errorMessage: null,
          connectionAvailable: true,
        })
      : error(404, 'EXECUTION_NOT_FOUND', '执行不存在')
  }),
  http.get(sql('/api/v1/sql/scripts'), ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const userId = mockUserId(request)
    const url = new URL(request.url)
    const keyword = (url.searchParams.get('keyword') || '').trim().toLowerCase()
    const dataSourceId = url.searchParams.get('dataSourceId')
    const database = url.searchParams.get('database')
    const owned = mockScripts.filter(
      (item) =>
        item.id.startsWith(userId === '1002' ? 'other-' : 'me-') ||
        (userId === '1001' && !item.id.startsWith('other-')),
    )
    const filtered = owned.filter((item) => {
      if (dataSourceId && item.dataSourceId !== dataSourceId) return false
      if (database && item.database !== database) return false
      if (!keyword) return true
      return (
        item.name.toLowerCase().includes(keyword) || item.statement.toLowerCase().includes(keyword)
      )
    })
    return HttpResponse.json({
      items: filtered.map(
        (item): ScriptSummary => ({
          id: item.id,
          name: item.name,
          dataSourceId: item.dataSourceId,
          dataSourceName: item.dataSourceName,
          database: item.database,
          statementSummary: item.statementSummary,
          version: item.version,
          createdAt: item.createdAt,
          updatedAt: item.updatedAt,
        }),
      ),
      nextPageToken: null,
    })
  }),
  http.get(sql('/api/v1/sql/scripts/:id'), ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const item = mockScripts.find((entry) => entry.id === params.id)
    if (!item) return error(404, 'SCRIPT_NOT_FOUND', '脚本不存在')
    if (mockUserId(request) === '1002' && !item.id.startsWith('other-'))
      return error(404, 'SCRIPT_NOT_FOUND', '脚本不存在')
    return HttpResponse.json(item)
  }),
  http.post(sql('/api/v1/sql/scripts'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const body = (await request.json()) as ScriptWriteRequest
    if (!body.name?.trim() || !body.statement?.trim())
      return error(400, 'VALIDATION_FAILED', '请求参数不合法', {
        fieldErrors: [{ field: 'statement', code: 'REQUIRED', message: 'SQL 不能为空' }],
      })
    const source = body.dataSourceId
      ? mockDataSources().find((item) => item.id === body.dataSourceId)
      : undefined
    if (body.dataSourceId && !source)
      return error(404, 'DATA_SOURCE_NOT_FOUND', '数据源不存在或已不可见')
    const now = new Date().toISOString()
    const created: ScriptDetail = {
      id: `${mockUserId(request) === '1002' ? 'other-' : ''}${crypto.randomUUID()}`,
      name: body.name.trim(),
      dataSourceId: source?.id || null,
      dataSourceName: source?.name || null,
      database: body.database || null,
      statementSummary: summarySql(body.statement),
      version: 1,
      createdAt: now,
      updatedAt: now,
      statement: body.statement,
      connectionAvailable: Boolean(source),
    }
    mockScripts.unshift(created)
    persistMockScripts()
    return HttpResponse.json(created, {
      status: 201,
      headers: { Location: `/api/v1/sql/scripts/${created.id}` },
    })
  }),
  http.put(sql('/api/v1/sql/scripts/:id'), async ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const index = mockScripts.findIndex((item) => item.id === params.id)
    if (index < 0) return error(404, 'SCRIPT_NOT_FOUND', '脚本不存在')
    const current = mockScripts[index]!
    if (mockUserId(request) === '1002' && !current.id.startsWith('other-'))
      return error(404, 'SCRIPT_NOT_FOUND', '脚本不存在')
    const body = (await request.json()) as ScriptWriteRequest
    if (body.version !== current.version)
      return error(409, 'VERSION_CONFLICT', '脚本已被更新', {
        currentVersion: current.version,
        currentUpdatedAt: current.updatedAt,
        currentUpdatedByName: '张三',
      })
    const source = body.dataSourceId
      ? mockDataSources().find((item) => item.id === body.dataSourceId)
      : undefined
    if (body.dataSourceId && !source)
      return error(404, 'DATA_SOURCE_NOT_FOUND', '数据源不存在或已不可见')
    const updated: ScriptDetail = {
      ...current,
      name: body.name.trim(),
      statement: body.statement,
      statementSummary: summarySql(body.statement),
      dataSourceId: source?.id || null,
      dataSourceName: source?.name || null,
      database: body.database || null,
      connectionAvailable: Boolean(source),
      version: current.version + 1,
      updatedAt: new Date().toISOString(),
    }
    mockScripts[index] = updated
    persistMockScripts()
    return HttpResponse.json(updated)
  }),
  http.delete(sql('/api/v1/sql/scripts/:id'), ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const index = mockScripts.findIndex((item) => item.id === params.id)
    if (index < 0) return error(404, 'SCRIPT_NOT_FOUND', '脚本不存在')
    const current = mockScripts[index]!
    if (mockUserId(request) === '1002' && !current.id.startsWith('other-'))
      return error(404, 'SCRIPT_NOT_FOUND', '脚本不存在')
    const version = Number(new URL(request.url).searchParams.get('version'))
    if (version !== current.version)
      return error(409, 'VERSION_CONFLICT', '脚本已被更新', {
        currentVersion: current.version,
        currentUpdatedAt: current.updatedAt,
        currentUpdatedByName: '张三',
      })
    mockScripts.splice(index, 1)
    persistMockScripts()
    return new HttpResponse(null, { status: 204 })
  }),
]

export { initialDataSources }
