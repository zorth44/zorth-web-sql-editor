import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mockSession } from '@/mocks/fixtures'
import { queryClient } from '@/query/client'
import { useAuthStore } from '@/stores/auth'
import { useCopilotStore } from '@/stores/copilot'
import { useEditorStore } from '@/stores/editor'
import { saveToken } from '@/auth/token-storage'
import * as metadataApi from '@/api/metadata'
import type { Capability, Session } from '@/types/contracts'
import type { CompletionCatalog } from '@/sql-editor/completion-catalog'

const resourceBrowserLifecycle = vi.hoisted(() => ({
  mounted: 0,
  unmounted: 0,
  emitSuggestions: undefined as ((catalog: CompletionCatalog) => void) | undefined,
}))

vi.mock('@/components/editor/SqlMonacoEditor.vue', () => ({
  default: {
    name: 'SqlMonacoEditor',
    props: ['modelValue', 'language', 'catalog', 'identifierQuote', 'resolveColumns'],
    template: '<div data-testid="monaco-stub" />',
    methods: {
      getCopilotSql() {
        return ''
      },
      getRunnableScript() {
        return ''
      },
      formatSql() {},
      insertAtCursor() {},
      appendSql() {},
      replaceSql() {
        return false
      },
      focus() {},
    },
  },
}))

vi.mock('@/components/resource-tree/ResourceBrowser.vue', async () => {
  const { defineComponent, onBeforeUnmount, watch } = await import('vue')
  return {
    default: defineComponent({
      name: 'ResourceBrowser',
      props: {
        dataSourceId: { type: String, default: null },
        database: { type: String, default: null },
        completionGeneration: { type: Number, default: 0 },
        reloadToken: { type: Number, default: 0 },
        sources: { type: Array, default: () => [] },
        engines: { type: Array, default: () => [] },
      },
      emits: ['select-connection', 'insert', 'open-table', 'notice', 'suggestions', 'refresh'],
      setup(props, { emit }) {
        resourceBrowserLifecycle.mounted += 1
        resourceBrowserLifecycle.emitSuggestions = (catalog) => emit('suggestions', catalog)
        watch(
          () => [props.dataSourceId, props.database, props.completionGeneration] as const,
          ([dataSourceId, database, generation]) => {
            if (!dataSourceId || !database) return
            emit('suggestions', {
              dataSourceId,
              namespace: database,
              generation: generation ?? 0,
              namespaces: [database],
              tables: ['order_item'],
              columnsByTable: {},
            })
          },
          { immediate: true },
        )
        onBeforeUnmount(() => {
          resourceBrowserLifecycle.unmounted += 1
          resourceBrowserLifecycle.emitSuggestions = undefined
        })
      },
      template: '<div data-testid="resource-browser" />',
    }),
  }
})
vi.mock('@/components/copilot/CopilotPanel.vue', () => ({
  default: { name: 'CopilotPanel', template: '<div />' },
}))
vi.mock('@/components/table-viewer/TableViewer.vue', () => ({
  default: { name: 'TableViewer', template: '<div />' },
}))
vi.mock('@/components/history/HistoryPanel.vue', () => ({
  default: { name: 'HistoryPanel', template: '<div />' },
}))
vi.mock('@/components/scripts/ScriptPanel.vue', () => ({
  default: { name: 'ScriptPanel', template: '<div data-testid="script-panel" />' },
}))
vi.mock('@/components/result-grid/ScriptResultPanel.vue', () => ({
  default: { name: 'ScriptResultPanel', template: '<div />' },
}))
vi.mock('splitpanes', () => ({
  Splitpanes: { name: 'Splitpanes', template: '<div><slot /></div>' },
  Pane: { name: 'Pane', template: '<div><slot /></div>' },
}))
vi.mock('splitpanes/dist/splitpanes.css', () => ({}))

import SqlEditorPage from '@/pages/SqlEditorPage.vue'

async function renderPage(capabilities: Capability[] = mockSession.capabilities) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const session: Session = { ...mockSession, capabilities }
  useAuthStore().session = session
  saveToken('mock-token', false)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sql-editor', component: SqlEditorPage },
      { path: '/data-sources', component: { template: '<div />' } },
    ],
  })
  await router.push('/sql-editor')
  await router.isReady()
  const wrapper = mount(SqlEditorPage, {
    attachTo: document.body,
    global: {
      plugins: [pinia, [VueQueryPlugin, { queryClient }], router],
    },
  })
  await flushPromises()
  return wrapper
}

describe('sql editor scripts workspace', () => {
  beforeEach(() => {
    queryClient.clear()
    resourceBrowserLifecycle.mounted = 0
    resourceBrowserLifecycle.unmounted = 0
  })

  it('hides the scripts rail and save actions without SCRIPT_MANAGE', async () => {
    const wrapper = await renderPage(
      mockSession.capabilities.filter((item) => item !== 'SCRIPT_MANAGE'),
    )
    useEditorStore().createTab('ds-orders-a', 'orders', 'select 1')
    await flushPromises()
    expect(wrapper.find('[data-testid="scripts-rail"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="save-script"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="save-script-as"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('opens a name dialog on first save', async () => {
    const wrapper = await renderPage()
    useEditorStore().createTab('ds-orders-a', 'orders', 'select 1 from order_item')
    await flushPromises()
    await wrapper.get('[data-testid="save-script"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('保存脚本')
    expect(document.querySelector('[data-testid="save-script-name"]')).not.toBeNull()
    wrapper.unmount()
  })
})

describe('sql editor copilot layout', () => {
  beforeEach(() => {
    queryClient.clear()
    resourceBrowserLifecycle.mounted = 0
    resourceBrowserLifecycle.unmounted = 0
  })

  it('does not remount the resource tree when toggling Copilot', async () => {
    const wrapper = await renderPage()
    useEditorStore().createTab('ds-orders-a', 'orders', 'select 1')
    await flushPromises()
    const mounted = resourceBrowserLifecycle.mounted
    const unmounted = resourceBrowserLifecycle.unmounted
    expect(mounted).toBeGreaterThan(0)

    useCopilotStore().toggle()
    await flushPromises()
    expect(wrapper.find('[data-testid="resource-browser"]').exists()).toBe(true)
    expect(resourceBrowserLifecycle.mounted).toBe(mounted)
    expect(resourceBrowserLifecycle.unmounted).toBe(unmounted)

    useCopilotStore().toggle()
    await flushPromises()
    expect(resourceBrowserLifecycle.mounted).toBe(mounted)
    expect(resourceBrowserLifecycle.unmounted).toBe(unmounted)
    wrapper.unmount()
  })
})

describe('sql editor metadata completion catalog', () => {
  beforeEach(() => {
    queryClient.clear()
    resourceBrowserLifecycle.mounted = 0
    resourceBrowserLifecycle.unmounted = 0
    resourceBrowserLifecycle.emitSuggestions = undefined
  })

  it('keeps a loaded table directory after the resource browser unmounts', async () => {
    const wrapper = await renderPage()
    useEditorStore().createTab('ds-orders-a', 'orders', 'select 1')
    await flushPromises()
    const monaco = wrapper.getComponent({ name: 'SqlMonacoEditor' })
    expect(monaco.props('catalog')).toMatchObject({
      dataSourceId: 'ds-orders-a',
      namespace: 'orders',
      tables: ['order_item'],
    })
    await wrapper.get('[aria-label="执行历史"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="resource-browser"]').exists()).toBe(false)
    expect(wrapper.getComponent({ name: 'SqlMonacoEditor' }).props('catalog')).toMatchObject({
      namespace: 'orders',
      tables: ['order_item'],
    })
    wrapper.unmount()
  })

  it('scopes the catalog to the active SQL tab instead of the latest emit', async () => {
    const wrapper = await renderPage()
    const store = useEditorStore()
    store.createTab('ds-orders-a', 'orders', 'select 1')
    await flushPromises()
    const firstId = store.activeId
    store.createTab('ds-orders-a', 'analytics', 'select 2')
    await flushPromises()
    resourceBrowserLifecycle.emitSuggestions?.({
      dataSourceId: 'ds-orders-a',
      namespace: 'analytics',
      generation: wrapper.getComponent({ name: 'SqlMonacoEditor' }).props('catalog').generation,
      namespaces: ['analytics'],
      tables: ['events'],
      columnsByTable: {},
    })
    await flushPromises()
    store.setActive(firstId)
    await flushPromises()
    expect(wrapper.getComponent({ name: 'SqlMonacoEditor' }).props('catalog')).toMatchObject({
      namespace: 'orders',
      tables: ['order_item'],
    })
    wrapper.unmount()
  })

  it('does not call table-detail for an unknown qualifier', async () => {
    const spy = vi.spyOn(metadataApi, 'getTableDetail')
    const wrapper = await renderPage()
    useEditorStore().createTab('ds-orders-a', 'orders', 'select 1')
    await flushPromises()
    const resolveColumns = wrapper
      .getComponent({ name: 'SqlMonacoEditor' })
      .props('resolveColumns') as (table: string) => Promise<string[]>
    await expect(resolveColumns('nope')).resolves.toEqual([])
    expect(spy).not.toHaveBeenCalled()
    wrapper.unmount()
    spy.mockRestore()
  })

  it('seeds cached columns from a catalog snapshot', async () => {
    const spy = vi.spyOn(metadataApi, 'getTableDetail')
    const wrapper = await renderPage()
    useEditorStore().createTab('ds-orders-a', 'orders', 'select 1')
    await flushPromises()
    const monaco = wrapper.getComponent({ name: 'SqlMonacoEditor' })
    resourceBrowserLifecycle.emitSuggestions?.({
      dataSourceId: 'ds-orders-a',
      namespace: 'orders',
      generation: monaco.props('catalog').generation,
      namespaces: ['orders'],
      tables: ['order_item'],
      columnsByTable: { order_item: ['id', 'amount'] },
    })
    await flushPromises()
    expect(
      wrapper.getComponent({ name: 'SqlMonacoEditor' }).props('catalog').columnsByTable,
    ).toEqual({ order_item: ['id', 'amount'] })
    const resolveColumns = wrapper
      .getComponent({ name: 'SqlMonacoEditor' })
      .props('resolveColumns') as (table: string) => Promise<string[]>
    await expect(resolveColumns('order_item')).resolves.toEqual(['id', 'amount'])
    expect(spy).not.toHaveBeenCalled()
    wrapper.unmount()
    spy.mockRestore()
  })

  it('drops in-flight table-detail results after the tab connection changes', async () => {
    let resolveDetail!: (value: Awaited<ReturnType<typeof metadataApi.getTableDetail>>) => void
    const spy = vi.spyOn(metadataApi, 'getTableDetail').mockReturnValue(
      new Promise((resolve) => {
        resolveDetail = resolve
      }),
    )
    try {
      const wrapper = await renderPage()
      const store = useEditorStore()
      store.createTab('ds-orders-a', 'orders', 'select 1')
      await flushPromises()
      const resolveColumns = wrapper
        .getComponent({ name: 'SqlMonacoEditor' })
        .props('resolveColumns') as (table: string) => Promise<string[]>
      const pending = resolveColumns('order_item')
      store.createTab('ds-orders-b', 'analytics', 'select 2')
      await flushPromises()
      resolveDetail({
        database: 'orders',
        table: 'order_item',
        columns: [
          {
            name: 'stale',
            typeName: 'INT',
            jdbcType: 'INTEGER',
            length: null,
            precision: null,
            scale: null,
            nullable: true,
            defaultValue: null,
            extra: null,
            comment: null,
            ordinal: 1,
            primaryKey: false,
          },
        ],
        primaryKey: null,
        indexes: [],
        ddl: null,
      })
      await expect(pending).resolves.toEqual([])
      wrapper.unmount()
    } finally {
      spy.mockRestore()
    }
  })
})
