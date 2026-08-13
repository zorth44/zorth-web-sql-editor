import { http, HttpResponse } from 'msw'
import { appEnv } from '@/env'
import { initialDataSources, mockDataSources, mockSession } from '@/mocks/fixtures'
import type {
  ApiErrorBody,
  ConnectionFields,
  ConnectionTestResult,
  CreateDataSourceRequest,
  DataSourceDetail,
  DataSourceListItem,
  UpdateDataSourceRequest,
} from '@/types/contracts'

const sql = (path: string) => `${appEnv.sqlApiBase}${path}`
const auth = (path: string) => `${appEnv.authApiBase}${path}`
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
  http.post(sql('/api/v1/data-sources:test'), async ({ request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const body = (await request.json()) as ConnectionFields
    if (!body.password)
      return error(400, 'VALIDATION_FAILED', '请求参数不合法', {
        fieldErrors: [{ field: 'password', code: 'REQUIRED', message: '请输入密码' }],
      })
    return HttpResponse.json(testResult(body))
  }),
  http.post(sql('/api/v1/data-sources/:id\\:test'), async ({ params, request }) => {
    const denied = authorized(request)
    if (denied) return denied
    const detail = mockDataSources().find((item) => item.id === params.id)
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
]

export { initialDataSources }
