import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResourceBrowser from '@/components/resource-tree/ResourceBrowser.vue'
import { initialDataSources } from '@/mocks/fixtures'
import { saveToken } from '@/auth/token-storage'
import * as metadataApi from '@/api/metadata'
import type { DataSourceListItem, EngineDescriptor } from '@/types/contracts'
import { mysqlEngineDescriptor, postgresEngineDescriptor } from '@/mocks/engines'

const sources = initialDataSources as DataSourceListItem[]

async function renderBrowser(props: {
  sources?: DataSourceListItem[]
  engines?: EngineDescriptor[]
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
      ...(props.engines ? { engines: props.engines } : {}),
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

  it('filters databases and tables under an expanded source', async () => {
    const wrapper = await renderBrowser({ dataSourceId: 'ds-orders-a', database: 'orders' })
    expect(wrapper.find('input[placeholder="搜索数据源 / 数据库"]').exists()).toBe(false)
    expect(wrapper.find('input[placeholder="过滤表 / 视图"]').exists()).toBe(false)
    expect(
      wrapper.get('[data-testid="navigator-db-filter-ds-orders-a"]').attributes('placeholder'),
    ).toBe('筛选库名')
    expect(
      wrapper.get('[data-testid="navigator-table-filter-ds-orders-a"]').attributes('placeholder'),
    ).toBe('筛选表名')
    expect(wrapper.find('[data-testid="navigator-db-filter-ds-orders-b"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="navigator-source-ds-orders-b"]').exists()).toBe(true)

    await wrapper.get('[data-testid="navigator-db-filter-ds-orders-a"]').setValue('analytics')
    await flushPromises()
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-analytics"]').exists()).toBe(
      true,
    )
    expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
      false,
    )

    await wrapper.get('[data-testid="navigator-db-filter-ds-orders-a"]').setValue('')
    await wrapper.get('[data-testid="navigator-table-filter-ds-orders-a"]').setValue('order_item')
    await flushPromises()
    expect(wrapper.text()).toContain('order_item')
    expect(wrapper.text()).not.toContain('order_view')
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

  it('opens a table object from double-click without a structure drawer', async () => {
    const wrapper = await renderBrowser({ dataSourceId: 'ds-orders-a', database: 'orders' })
    const table = wrapper.get('[data-testid="navigator-table-ds-orders-a-orders-order_item"]')
    await table.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('select-connection')?.at(-1)).toEqual(['ds-orders-a', 'orders'])
    expect(wrapper.text()).not.toContain('auto_increment')
    await table.trigger('dblclick')
    expect(wrapper.emitted('open-table')?.at(-1)?.[0]).toMatchObject({
      dataSourceId: 'ds-orders-a',
      database: 'orders',
      pane: 'data',
      table: { name: 'order_item', type: 'TABLE' },
    })
    wrapper.unmount()
  })

  it('labels NAMESPACE filters from the engine catalog and skips unknown tree kinds', async () => {
    const engines = [
      {
        ...mysqlEngineDescriptor,
        resourceTree: [
          ...mysqlEngineDescriptor.resourceTree,
          { kind: 'PARTITION', label: '分区', parentKind: 'TABLE' },
        ],
      },
    ]
    const wrapper = await renderBrowser({
      engines,
      dataSourceId: 'ds-orders-a',
      database: 'orders',
    })
    expect(
      wrapper.get('[data-testid="navigator-db-filter-ds-orders-a"]').attributes('placeholder'),
    ).toBe('筛选数据库')
    expect(
      wrapper.get('[data-testid="navigator-table-filter-ds-orders-a"]').attributes('placeholder'),
    ).toBe('筛选表名')
    expect(wrapper.text()).toContain('表')
    expect(wrapper.text()).toContain('视图')
    expect(wrapper.text()).not.toContain('分区')
    wrapper.unmount()
  })

  it('labels PostgreSQL NAMESPACE filters as schemas', async () => {
    const pgSource = { ...sources[0], engine: 'POSTGRESQL' } as DataSourceListItem
    const wrapper = await renderBrowser({
      sources: [pgSource],
      engines: [postgresEngineDescriptor],
      dataSourceId: 'ds-orders-a',
      database: 'orders',
    })
    expect(
      wrapper.get('[data-testid="navigator-db-filter-ds-orders-a"]').attributes('placeholder'),
    ).toBe('筛选模式')
    wrapper.unmount()
  })

  it('renders a distinct engine icon for each data source type', async () => {
    const wrapper = await renderBrowser({
      sources: [
        { ...sources[0], engine: 'MYSQL' },
        { ...sources[1], engine: 'POSTGRESQL' },
        { ...sources[2], engine: 'GBASE_8A' },
      ],
    })
    expect(
      wrapper
        .get('[data-testid="navigator-source-ds-orders-a"] [data-engine-icon="MYSQL"]')
        .attributes('data-engine-variant'),
    ).toBe('tree')
    expect(
      wrapper
        .get('[data-testid="navigator-source-ds-orders-b"] [data-engine-icon="POSTGRESQL"]')
        .attributes('data-engine-variant'),
    ).toBe('tree')
    expect(
      wrapper
        .get('[data-testid="navigator-source-ds-in-use"] [data-engine-icon="GBASE_8A"]')
        .attributes('data-engine-variant'),
    ).toBe('tree')
    wrapper.unmount()
  })

  it('shows a spinner while databases are loading', async () => {
    let resolveDatabases!: (value: Awaited<ReturnType<typeof metadataApi.listDatabases>>) => void
    const spy = vi.spyOn(metadataApi, 'listDatabases').mockReturnValue(
      new Promise((resolve) => {
        resolveDatabases = resolve
      }),
    )
    try {
      const wrapper = await renderBrowser({})
      await wrapper.get('[aria-label="展开 订单测试库 mysql-a.internal:3306"]').trigger('click')
      await flushPromises()
      const spinner = wrapper.get('[data-testid="navigator-loading-ds-orders-a"]')
      expect(spinner.find('.tree-expand-spinner').exists()).toBe(true)
      expect(wrapper.text()).not.toContain('正在加载数据库')
      expect(wrapper.find('[data-testid="navigator-db-filter-ds-orders-a"]').exists()).toBe(false)
      resolveDatabases({ items: [{ name: 'orders', kind: 'NAMESPACE' }], nextPageToken: null })
      await flushPromises()
      expect(wrapper.find('[data-testid="navigator-loading-ds-orders-a"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="navigator-database-ds-orders-a-orders"]').exists()).toBe(
        true,
      )
      wrapper.unmount()
    } finally {
      spy.mockRestore()
    }
  })
})
