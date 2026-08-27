import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import HistoryPanel from '@/components/history/HistoryPanel.vue'
import { executeSql } from '@/api/executions'
import { saveToken } from '@/auth/token-storage'
import { queryClient } from '@/query/client'

async function seedHistory(count: number): Promise<void> {
  for (let i = 0; i < count; i += 1) {
    await executeSql({
      executionId: crypto.randomUUID(),
      dataSourceId: 'ds-orders-a',
      database: 'orders',
      statement: `select ${i}`,
    })
  }
}

async function renderPanel(dataSourceId: string | null = 'ds-orders-a') {
  const wrapper = mount(HistoryPanel, {
    props: { dataSourceId },
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

describe('history panel', () => {
  beforeEach(() => {
    queryClient.clear()
    saveToken('mock-token', false)
  })

  it('pages history instead of growing an unbounded list', async () => {
    await seedHistory(31)
    const wrapper = await renderPanel()
    expect(wrapper.find('[aria-label="select 30 SUCCESS"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="select 1 SUCCESS"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="select 0 SUCCESS"]').exists()).toBe(false)
    expect(wrapper.findAll('button[aria-label]')).toHaveLength(30)
    expect(wrapper.get('[data-testid="history-pager"]').text()).toContain('第 1 页')
    expect(wrapper.findAll('button').filter((button) => button.text() === '加载更多')).toHaveLength(
      0,
    )
    const next = wrapper.findAll('button').find((button) => button.text() === '下一页')
    await next!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('第 2 页')
    expect(wrapper.find('[aria-label="select 0 SUCCESS"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="select 30 SUCCESS"]').exists()).toBe(false)
    expect(wrapper.findAll('button[aria-label]')).toHaveLength(1)
    const previous = wrapper.findAll('button').find((button) => button.text() === '上一页')
    await previous!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('第 1 页')
    expect(wrapper.find('[aria-label="select 30 SUCCESS"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('opens a listed history item', async () => {
    await executeSql({
      executionId: crypto.randomUUID(),
      dataSourceId: 'ds-orders-a',
      database: 'orders',
      statement: 'select * from order_item',
    })
    const wrapper = await renderPanel()
    await wrapper.get('[aria-label="select * from order_item SUCCESS"]').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('open')?.[0]?.[0]).toMatchObject({
      statement: 'select * from order_item',
      connectionAvailable: true,
    })
    wrapper.unmount()
  })

  it('hides the pager when history fits on one page', async () => {
    await executeSql({
      executionId: crypto.randomUUID(),
      dataSourceId: 'ds-orders-a',
      database: 'orders',
      statement: 'select 1',
    })
    const wrapper = await renderPanel()
    expect(wrapper.text()).toContain('select 1')
    expect(wrapper.find('[data-testid="history-pager"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
