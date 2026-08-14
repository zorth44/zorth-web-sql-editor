import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'
import ResourceBrowser from '@/components/resource-tree/ResourceBrowser.vue'
import { initialDataSources } from '@/mocks/fixtures'
import { saveToken } from '@/auth/token-storage'
import type { DataSourceListItem } from '@/types/contracts'

const sources = initialDataSources as DataSourceListItem[]

async function renderBrowser(props: {
  sources?: DataSourceListItem[]
  dataSourceId?: string | null
  database?: string | null
  reloadToken?: number
}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/data-sources', component: { template: '<div>sources</div>' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(ResourceBrowser, {
    props: {
      sources: props.sources ?? sources,
      dataSourceId: props.dataSourceId ?? null,
      database: props.database ?? null,
      ...(props.reloadToken === undefined ? {} : { reloadToken: props.reloadToken }),
    },
    global: { plugins: [router] },
  })
  await flushPromises()
  return wrapper
}

describe('resource navigator', () => {
  beforeEach(() => saveToken('mock-token', false))

  it('renders data sources as roots and loads databases only after expand', async () => {
    const wrapper = await renderBrowser({})
    expect(wrapper.get('[data-testid="navigator-source-ds-orders-a"]').text()).toContain(
      '订单测试库',
    )
    expect(wrapper.get('[data-testid="navigator-source-ds-orders-a"]').text()).toContain(
      'mysql-a.internal:3306',
    )
    expect(wrapper.get('[data-testid="navigator-source-ds-orders-b"]').text()).toContain(
      'mysql-b.internal:3307',
    )
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
      false,
    )

    await wrapper.get('[aria-label="展开 订单测试库 mysql-a.internal:3306"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
      true,
    )
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-b-orders"]').exists()).toBe(
      false,
    )
    wrapper.unmount()
  })

  it('keeps multiple data sources expanded with isolated database keys', async () => {
    const wrapper = await renderBrowser({ dataSourceId: 'ds-orders-a', database: 'orders' })
    await wrapper.get('[aria-label="展开 订单测试库 mysql-b.internal:3307"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
      true,
    )
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-b-orders"]').exists()).toBe(
      true,
    )

    await wrapper
      .get('[data-testid="navigator-database-ds-orders-b-analytics"] .tree-label')
      .trigger('click')
    expect(wrapper.emitted('select-connection')?.at(-1)).toEqual(['ds-orders-b', 'analytics'])
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
      true,
    )
    wrapper.unmount()
  })

  it('does not bind the editor when only a data source node is clicked', async () => {
    const wrapper = await renderBrowser({})
    await wrapper.get('[aria-label="订单测试库 mysql-a.internal:3306"]').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('select-connection')).toBeUndefined()
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
      true,
    )
    wrapper.unmount()
  })

  it('filters sources by name or host and expanded databases', async () => {
    const wrapper = await renderBrowser({ dataSourceId: 'ds-orders-a' })
    await wrapper.get('input[placeholder="搜索数据源 / 数据库"]').setValue('mysql-b')
    await flushPromises()
    expect(wrapper.find('[data-testid="navigator-source-ds-orders-a"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="navigator-source-ds-orders-b"]').exists()).toBe(true)

    await wrapper.get('input[placeholder="搜索数据源 / 数据库"]').setValue('analytics')
    await flushPromises()
    expect(wrapper.find('[data-testid="navigator-source-ds-orders-a"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-analytics"]').exists()).toBe(
      true,
    )
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
      false,
    )
    wrapper.unmount()
  })

  it('points empty state to data-source management', async () => {
    const wrapper = await renderBrowser({ sources: [] })
    expect(wrapper.text()).toContain('还没有可见的数据源')
    expect(wrapper.get('a[href="/data-sources"]').text()).toBe('去数据源管理')
    wrapper.unmount()
  })

  it('expands a toolbar-selected source without collapsing others', async () => {
    const wrapper = await renderBrowser({ dataSourceId: 'ds-orders-a', database: 'orders' })
    await wrapper.get('[aria-label="展开 订单测试库 mysql-b.internal:3307"]').trigger('click')
    await flushPromises()
    await wrapper.setProps({ dataSourceId: 'ds-orders-b', database: 'orders_shadow' })
    await flushPromises()
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
      true,
    )
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-b-orders"]').exists()).toBe(
      true,
    )
    wrapper.unmount()
  })
})
