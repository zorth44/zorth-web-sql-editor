import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DataSourcesPage from '@/pages/DataSourcesPage.vue'
import { mockSession } from '@/mocks/fixtures'
import { queryClient, queryKeys } from '@/query/client'
import { useAuthStore } from '@/stores/auth'
import { saveToken } from '@/auth/token-storage'

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().session = mockSession
  saveToken('mock-token', false)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/data-sources', component: DataSourcesPage },
      { path: '/data-sources/new', component: { template: '<div />' } },
      { path: '/data-sources/:id/edit', component: { template: '<div />' } },
    ],
  })
  await router.push('/data-sources')
  await router.isReady()
  const wrapper = mount(DataSourcesPage, {
    attachTo: document.body,
    global: { plugins: [pinia, [VueQueryPlugin, { queryClient }], router] },
  })
  await flushPromises()
  return wrapper
}

describe('data-source list', () => {
  beforeEach(() => queryClient.clear())
  it('renders duplicate names as distinct ID-keyed rows', async () => {
    const wrapper = await renderPage()
    expect(wrapper.find('[data-testid="data-source-ds-orders-a"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="data-source-ds-orders-b"]').exists()).toBe(true)
    expect(wrapper.text().match(/订单测试库/g)).toHaveLength(2)
    expect(wrapper.text()).not.toContain('password')
    wrapper.unmount()
  })
  it('uses a cursor stack for next and previous navigation', async () => {
    const wrapper = await renderPage()
    await wrapper.find('select').setValue('2')
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const next = buttons.find((button) => button.text() === '下一页')!
    await next.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('第 2 页')
    expect(wrapper.text()).toContain('报表生产库')
    const previous = wrapper.findAll('button').find((button) => button.text() === '上一页')!
    await previous.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('第 1 页')
    expect(wrapper.text()).toContain('订单测试库')
    wrapper.unmount()
  })
  it('invalidates list and selected detail after a saved connection test', async () => {
    const wrapper = await renderPage()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    await wrapper.get('[aria-label="测试 订单测试库"]').trigger('click')
    await flushPromises()
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.dataSourceLists() })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.dataSourceDetail('ds-orders-a') })
    wrapper.unmount()
  })
})
