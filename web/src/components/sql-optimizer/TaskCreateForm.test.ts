import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import TaskCreateForm from './TaskCreateForm.vue'
import { mockSession } from '@/mocks/fixtures'
import { useAuthStore } from '@/stores/auth'
import { queryClient } from '@/query/client'
import { saveToken } from '@/auth/token-storage'
import { server } from '@/mocks/server'
import { appEnv } from '@/env'

vi.mock('@/components/editor/SqlMonacoEditor.vue', () => ({
  default: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template:
      '<textarea aria-label="SQL" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}))
async function render() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().session = structuredClone(mockSession)
  saveToken('mock-token', false)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sql-optimizer', component: TaskCreateForm },
      { path: '/sql-optimizer/tasks/:taskId', component: { template: '<div />' } },
    ],
  })
  await router.push('/sql-optimizer')
  const wrapper = mount(TaskCreateForm, {
    global: { plugins: [pinia, [VueQueryPlugin, { queryClient }], router] },
  })
  await flushPromises()
  return { wrapper, router }
}
beforeEach(() => queryClient.clear())
describe('optimizer create form', () => {
  it('requires fields and clears namespace when datasource changes', async () => {
    const { wrapper } = await render()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('请输入 SQL')
    await wrapper.findAll('select')[0]!.setValue('ds-orders-a')
    await flushPromises()
    await wrapper.get('[aria-label="数据库 / 命名空间"]').setValue('orders')
    await wrapper.findAll('select')[0]!.setValue('ds-orders-b')
    await flushPromises()
    expect(
      (wrapper.get('[aria-label="数据库 / 命名空间"]').element as HTMLSelectElement).value,
    ).toBe('')
    wrapper.unmount()
  })
  it('retains original payload/key after response loss, disables duplicates and generates a new operation after edit', async () => {
    const calls: { key: string | null; body: unknown }[] = []
    let finish: (() => void) | undefined
    server.use(
      http.post(`${appEnv.sqlApiBase}/api/v1/sql-optimization/tasks`, async ({ request }) => {
        calls.push({ key: request.headers.get('Idempotency-Key'), body: await request.json() })
        if (calls.length === 1) {
          await new Promise<void>((resolve) => {
            finish = resolve
          })
          return HttpResponse.error()
        }
        return HttpResponse.json(
          { id: 'saved', status: 'QUEUED', createdAt: new Date().toISOString() },
          { status: 202 },
        )
      }),
    )
    const { wrapper, router } = await render()
    await wrapper.findAll('select')[0]!.setValue('ds-orders-a')
    await flushPromises()
    await wrapper.get('[aria-label="数据库 / 命名空间"]').setValue('orders')
    await wrapper.get('textarea').setValue('select 1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')
    expect(calls).toHaveLength(1)
    finish!()
    await flushPromises()
    expect(wrapper.text()).toContain('尚未确认是否提交成功')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(calls[1]).toEqual(calls[0])
    expect(router.currentRoute.value.path).toBe('/sql-optimizer/tasks/saved')
    await wrapper.get('textarea').setValue('select 2')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(calls[2]!.key).not.toBe(calls[1]!.key)
    wrapper.unmount()
  })
  it('keeps SQL and coordinates on quota rejection without automatic retry', async () => {
    let count = 0
    server.use(
      http.post(`${appEnv.sqlApiBase}/api/v1/sql-optimization/tasks`, () => {
        count += 1
        return HttpResponse.json(
          { code: 'OPTIMIZATION_QUOTA_EXCEEDED', message: '配额已满', requestId: 'r' },
          { status: 429 },
        )
      }),
    )
    const { wrapper } = await render()
    await wrapper.findAll('select')[0]!.setValue('ds-orders-a')
    await flushPromises()
    await wrapper.get('[aria-label="数据库 / 命名空间"]').setValue('orders')
    await wrapper.get('textarea').setValue('select 1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('配额已满')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('select 1')
    expect(count).toBe(1)
    wrapper.unmount()
  })
})
