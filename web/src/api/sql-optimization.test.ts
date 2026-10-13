import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import {
  createOptimizationTask,
  getOptimizationTask,
  listOptimizationTasks,
  retryOptimizationTask,
  tasksPath,
} from '@/api/sql-optimization'
import { appEnv } from '@/env'
import { saveToken } from '@/auth/token-storage'
import { resetOptimizationMock } from '@/mocks/sql-optimization-handlers'
import { server } from '@/mocks/server'
import { randomUUID } from '@/uuid'

const payload = { datasourceId: 'ds-orders-a', database: 'orders', sql: 'select * from orders' }
beforeEach(() => {
  saveToken('mock-token', false)
  resetOptimizationMock()
  vi.restoreAllMocks()
})
describe('SQL optimizer contract', () => {
  it('sends only contract inputs, bearer/request/key headers, accepts 202 and no retry body', async () => {
    const key = randomUUID()
    server.use(
      http.post(`${appEnv.sqlApiBase}${tasksPath}`, async ({ request }) => {
        expect(request.headers.get('Authorization')).toBe('Bearer mock-token')
        expect(request.headers.get('X-Request-Id')).toBeTruthy()
        expect(request.headers.get('Idempotency-Key')).toBe(key)
        expect(await request.json()).toEqual(payload)
        return HttpResponse.json(
          { id: 'accepted', status: 'RUNNING', createdAt: new Date().toISOString() },
          { status: 202 },
        )
      }),
      http.post(`${appEnv.sqlApiBase}${tasksPath}/accepted/retry`, async ({ request }) => {
        expect(await request.text()).toBe('')
        expect(request.headers.has('Content-Type')).toBe(false)
        expect(request.headers.get('Idempotency-Key')).toBe(key)
        return HttpResponse.json(
          { id: 'new', status: 'QUEUED', createdAt: new Date().toISOString() },
          { status: 202 },
        )
      }),
    )
    expect((await createOptimizationTask(payload, key)).status).toBe('RUNNING')
    expect((await retryOptimizationTask('accepted', key)).id).toBe('new')
  })
  it('keeps exact null fields and transitions by server time, replays current status and creates a separate retry', async () => {
    const now = Date.now()
    const clock = vi.spyOn(Date, 'now').mockReturnValue(now)
    const key = randomUUID()
    const accepted = await createOptimizationTask(payload, key)
    const queued = await getOptimizationTask(accepted.id)
    expect(queued).toMatchObject({
      status: 'QUEUED',
      startedAt: null,
      completedAt: null,
      report: null,
      reportSchemaVersion: null,
      summary: null,
      error: null,
      sourceTaskId: null,
    })
    clock.mockReturnValue(now + 1500)
    expect((await getOptimizationTask(accepted.id)).status).toBe('RUNNING')
    await expect(retryOptimizationTask(accepted.id, randomUUID())).rejects.toMatchObject({
      status: 409,
      code: 'TASK_NOT_TERMINAL',
    })
    clock.mockReturnValue(now + 5000)
    const done = await getOptimizationTask(accepted.id)
    expect(done).toMatchObject({
      status: 'SUCCEEDED',
      reportSchemaVersion: 1,
      error: null,
      summary: { policyIssueCount: 1, performanceIssueCount: 1, limitationCount: 1 },
    })
    expect((await createOptimizationTask(payload, key)).id).toBe(accepted.id)
    expect((await createOptimizationTask(payload, key)).status).toBe('SUCCEEDED')
    const retry = await retryOptimizationTask(accepted.id, randomUUID())
    expect(retry.id).not.toBe(accepted.id)
    expect((await getOptimizationTask(retry.id)).sourceTaskId).toBe(accepted.id)
    expect((await getOptimizationTask(accepted.id)).report).toEqual(done.report)
  })
  it('recovers an accepted request after response loss and rejects same key changed SQL', async () => {
    const key = randomUUID()
    const input = { ...payload, name: 'mock:lost' }
    await expect(createOptimizationTask(input, key)).rejects.toMatchObject({ status: 0 })
    const recovered = await createOptimizationTask(input, key)
    expect((await listOptimizationTasks({})).items.map((i) => i.id)).toEqual([recovered.id])
    await expect(createOptimizationTask({ ...input, sql: 'select 2' }, key)).rejects.toMatchObject({
      status: 409,
      code: 'IDEMPOTENCY_CONFLICT',
    })
  })
  it('uses scoped opaque cursors and excludes full SQL/report from summaries', async () => {
    await createOptimizationTask(payload, randomUUID())
    await createOptimizationTask({ ...payload, sql: 'select 2' }, randomUUID())
    const first = await listOptimizationTasks({ pageSize: 1 })
    expect(first.nextPageToken).toBeTruthy()
    expect(first.items[0]).not.toHaveProperty('sql')
    expect(first.items[0]).not.toHaveProperty('report')
    const next = await listOptimizationTasks({ pageSize: 1, pageToken: first.nextPageToken! })
    expect(next.items[0]?.id).not.toBe(first.items[0]?.id)
    expect(next.nextPageToken).toBeNull()
    await expect(
      listOptimizationTasks({ status: 'FAILED', pageToken: first.nextPageToken! }),
    ).rejects.toMatchObject({ code: 'INVALID_PAGE_TOKEN' })
    saveToken('other-token', false)
    expect((await listOptimizationTasks({})).items).toEqual([])
    await expect(getOptimizationTask(first.items[0]!.id)).rejects.toMatchObject({ status: 404 })
    await expect(listOptimizationTasks({ pageToken: first.nextPageToken! })).rejects.toMatchObject({
      status: 400,
    })
  })
  it.each(['mock:clean', 'mock:limited', 'mock:expired'])(
    'returns valid terminal fixture %s',
    async (name) => {
      const task = await createOptimizationTask({ ...payload, name }, randomUUID())
      vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 6000)
      const detail = await getOptimizationTask(task.id)
      if (name === 'mock:expired')
        expect(detail).toMatchObject({
          status: 'FAILED',
          report: null,
          summary: null,
          error: { code: 'CREDENTIAL_EXPIRED' },
        })
      else {
        expect(detail.status).toBe('SUCCEEDED')
        expect(detail.report?.issues).toEqual([])
        expect(detail.report?.limitations.length).toBe(name === 'mock:limited' ? 1 : 0)
      }
    },
  )
  it('maps 403/404/429 separately from HTTP 200 task failure', async () => {
    await expect(
      createOptimizationTask({ ...payload, name: 'mock:quota' }, randomUUID()),
    ).rejects.toMatchObject({ status: 429 })
    await expect(getOptimizationTask('missing')).rejects.toMatchObject({ status: 404 })
    saveToken('no-optimize-token', false)
    await expect(listOptimizationTasks({})).rejects.toMatchObject({ status: 403 })
  })
  it('can recover a temporary GET interruption without changing terminal state', async () => {
    const task = await createOptimizationTask({ ...payload, name: 'mock:network' }, randomUUID())
    vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 6000)
    expect((await getOptimizationTask(task.id)).status).toBe('SUCCEEDED')
    await expect(getOptimizationTask(task.id)).rejects.toMatchObject({ status: 0 })
    expect((await getOptimizationTask(task.id)).status).toBe('SUCCEEDED')
  })
})
