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
import type { Capability, Session } from '@/types/contracts'

const resourceBrowserLifecycle = vi.hoisted(() => ({ mounted: 0, unmounted: 0 }))

vi.mock('@/components/editor/SqlMonacoEditor.vue', () => ({
  default: {
    name: 'SqlMonacoEditor',
    template: '<div data-testid="monaco-stub" />',
    methods: {
      getCopilotSql() {
        return ''
      },
      getRunnableScript() {
        return ''
      },
      getRunnableStatement() {
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
  const { defineComponent, onBeforeUnmount } = await import('vue')
  return {
    default: defineComponent({
      name: 'ResourceBrowser',
      setup() {
        resourceBrowserLifecycle.mounted += 1
        onBeforeUnmount(() => {
          resourceBrowserLifecycle.unmounted += 1
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
