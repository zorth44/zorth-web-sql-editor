import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TaskList from '@/components/sql-optimizer/TaskList.vue'
import SqlOptimizationTaskPage from './SqlOptimizationTaskPage.vue'
import SqlOptimizerPage from './SqlOptimizerPage.vue'
import { mockSession } from '@/mocks/fixtures'
import { useAuthStore } from '@/stores/auth'
import { queryClient } from '@/query/client'
import { optimizerKey } from '@/sql-optimizer/queries'
import type { TaskDetail, TaskListResponse } from '@/sql-optimizer/contracts'
import {
  getOptimizationTask,
  listOptimizationTasks,
  retryOptimizationTask,
} from '@/api/sql-optimization'
import { ApiError } from '@/api/api-error'

vi.mock('@/api/sql-optimization', () => ({
  getOptimizationTask: vi.fn(),
  listOptimizationTasks: vi.fn(),
  retryOptimizationTask: vi.fn(),
}))
vi.mock('@/components/editor/SqlMonacoEditor.vue', () => ({ default: { template: '<div />' } }))
const detail: TaskDetail = {
  id: 't1',
  name: '任务 A',
  datasourceId: 'ds',
  datasourceName: '测试库',
  database: 'orders',
  sql: 'select 1',
  status: 'QUEUED',
  createdAt: '2026-10-13T00:00:00Z',
  startedAt: null,
  completedAt: null,
  sourceTaskId: null,
  reportSchemaVersion: null,
  report: null,
  summary: null,
  error: null,
}
async function render(
  component: typeof TaskList | typeof SqlOptimizationTaskPage | typeof SqlOptimizerPage,
  capabilities = mockSession.capabilities,
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().session = { ...structuredClone(mockSession), capabilities }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sql-optimizer', component: SqlOptimizerPage },
      { path: '/sql-optimizer/tasks/:taskId', component: SqlOptimizationTaskPage },
    ],
  })
  await router.push(
    component === SqlOptimizationTaskPage ? '/sql-optimizer/tasks/t1' : '/sql-optimizer',
  )
  const wrapper = mount(component, {
    global: { plugins: [pinia, [VueQueryPlugin, { queryClient }], router] },
  })
  await flushPromises()
  return { wrapper, router }
}
beforeEach(() => {
  queryClient.clear()
  vi.useFakeTimers()
  vi.mocked(getOptimizationTask).mockResolvedValue(structuredClone(detail))
  vi.mocked(listOptimizationTasks).mockResolvedValue({ items: [], nextPageToken: null })
})
afterEach(() => {
  queryClient.clear()
  vi.useRealTimers()
  vi.resetAllMocks()
})
describe('optimizer queries', () => {
  it('polls active detail, pauses hidden pages, refreshes on return, stops on terminal and unmount', async () => {
    const { wrapper } = await render(SqlOptimizationTaskPage)
    expect(getOptimizationTask).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()
    expect(getOptimizationTask).toHaveBeenCalledTimes(2)
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(10000)
    await flushPromises()
    expect(getOptimizationTask).toHaveBeenCalledTimes(2)
    vi.mocked(getOptimizationTask).mockResolvedValue({
      ...detail,
      status: 'FAILED',
      completedAt: '2026-10-13T00:01:00Z',
      error: { code: 'NEW_CODE', message: '安全的失败信息', requestId: 'r1' },
    })
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(getOptimizationTask).toHaveBeenCalledTimes(3)
    expect(wrapper.text()).toContain('安全的失败信息')
    await vi.advanceTimersByTimeAsync(10000)
    await flushPromises()
    expect(getOptimizationTask).toHaveBeenCalledTimes(3)
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(10000)
    expect(getOptimizationTask).toHaveBeenCalledTimes(3)
  })
  it('keeps recent server state when GET fails temporarily', async () => {
    const { wrapper } = await render(SqlOptimizationTaskPage)
    vi.mocked(getOptimizationTask).mockRejectedValue(
      new ApiError(0, { code: 'NETWORK_ERROR', message: '网络中断', requestId: 'r' }),
    )
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '刷新任务')!
      .trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1500)
    await flushPromises()
    expect(wrapper.text()).toContain('查询错误不代表任务失败')
    expect(wrapper.text()).toContain('排队中')
    expect(wrapper.text()).toContain('select 1')
    wrapper.unmount()
  })
  it('uses cursor stack, resets status and scopes query cache to identity', async () => {
    const page: TaskListResponse = {
      items: [{ ...detail, sqlPreview: 'select 1' }],
      nextPageToken: 'opaque-next',
    }
    vi.mocked(listOptimizationTasks).mockImplementation(async (params) => ({
      ...page,
      nextPageToken: params.pageToken ? null : 'opaque-next',
    }))
    const { wrapper } = await render(TaskList)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '下一页')!
      .trigger('click')
    await flushPromises()
    expect(listOptimizationTasks).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageToken: 'opaque-next' }),
      expect.any(AbortSignal),
    )
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '上一页')!
      .trigger('click')
    await flushPromises()
    await wrapper.get('[aria-label="任务状态"]').setValue('FAILED')
    await flushPromises()
    expect(listOptimizationTasks).toHaveBeenLastCalledWith(
      { status: 'FAILED', pageSize: 20 },
      expect.any(AbortSignal),
    )
    expect(
      queryClient.getQueryCache().findAll({ queryKey: optimizerKey('1001', '12') }).length,
    ).toBeGreaterThan(0)
    useAuthStore().clearAuth()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
    wrapper.unmount()
  })
  it('does not poll empty lists or expose operations without capability', async () => {
    const { wrapper } = await render(TaskList)
    await vi.advanceTimersByTimeAsync(10000)
    expect(listOptimizationTasks).toHaveBeenCalledTimes(1)
    wrapper.unmount()
    vi.mocked(listOptimizationTasks).mockClear()
    const denied = await render(SqlOptimizerPage, [])
    expect(denied.wrapper.text()).toContain('没有 SQL 优化器能力')
    expect(listOptimizationTasks).not.toHaveBeenCalled()
    denied.wrapper.unmount()
  })
  it('recovers lost retry with the same key and navigates to a new task', async () => {
    vi.mocked(getOptimizationTask).mockResolvedValue({
      ...detail,
      status: 'FAILED',
      completedAt: '2026-10-13T00:01:00Z',
      error: { code: 'INTERNAL_ERROR', message: '失败', requestId: 'r' },
    })
    vi.mocked(retryOptimizationTask)
      .mockRejectedValueOnce(
        new ApiError(0, { code: 'NETWORK_ERROR', message: 'lost', requestId: 'r' }),
      )
      .mockResolvedValue({ id: 'new-id', status: 'QUEUED', createdAt: detail.createdAt })
    const { wrapper, router } = await render(SqlOptimizationTaskPage)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '再次扫描')!
      .trigger('click')
    await flushPromises()
    const original = vi.mocked(retryOptimizationTask).mock.calls[0]
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '用原请求找回重扫任务')!
      .trigger('click')
    await flushPromises()
    expect(vi.mocked(retryOptimizationTask).mock.calls[1]).toEqual(original)
    expect(router.currentRoute.value.path).toBe('/sql-optimizer/tasks/new-id')
    wrapper.unmount()
  })
})
