import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useEditorStore } from '@/stores/editor'
beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
})
describe('editor tabs', () => {
  it('persists drafts without results', () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders', 'select 1')
    const controller = store.start(tab.id, crypto.randomUUID())
    store.finish(tab.id, {
      executionId: 'x',
      kind: 'RESULT_SET',
      columns: [],
      rows: [],
      rowCount: 0,
      truncated: false,
      durationMs: 1,
    })
    const stored = sessionStorage.getItem('zorth.sql-editor.drafts.v1') || ''
    expect(stored).toContain('select 1')
    expect(stored).not.toContain('RESULT_SET')
    expect(controller.signal.aborted).toBe(false)
  })
  it('limits execution concurrency to three tabs', () => {
    const store = useEditorStore()
    for (let i = 0; i < 3; i++) {
      const tab = store.createTab('ds', 'db')
      store.start(tab.id, crypto.randomUUID())
    }
    const fourth = store.createTab('ds', 'db')
    expect(() => store.start(fourth.id, crypto.randomUUID())).toThrow('最多同时执行 3 条 SQL')
    store.clearAll()
    expect(sessionStorage.getItem('zorth.sql-editor.drafts.v1')).toBeNull()
  })
  it('rebinds an idle tab instead of silently mutating a used tab', () => {
    const store = useEditorStore()
    const idle = store.createTab('ds-1', 'orders')
    expect(store.activateConnection('ds-1', 'analytics').id).toBe(idle.id)
    expect(idle.database).toBe('analytics')
    store.updateSql(idle.id, 'select 1')
    const next = store.activateConnection('ds-1', 'orders')
    expect(next.id).not.toBe(idle.id)
    expect(idle.database).toBe('analytics')
    expect(next.database).toBe('orders')
  })
  it('leaves the workspace empty after the last tab is closed', () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    store.closeTab(tab.id)
    expect(store.tabs).toEqual([])
    expect(store.active).toBeNull()
  })
  it('opens one object tab per table and does not treat it as an idle SQL tab', () => {
    const store = useEditorStore()
    const sql = store.createTab('ds-1', 'orders', 'select 1')
    const table = {
      database: 'orders',
      name: 'order_item',
      type: 'TABLE' as const,
      comment: '订单明细',
    }
    const first = store.openTableTab('ds-1', 'orders', table)
    expect(first.kind).toBe('table')
    expect(first.title).toBe('order_item')
    expect(first.viewerPane).toBe('data')
    const second = store.openTableTab('ds-1', 'orders', table, 'properties')
    expect(second.id).toBe(first.id)
    expect(second.viewerPane).toBe('properties')
    expect(store.activateConnection('ds-1', 'analytics').id).not.toBe(first.id)
    expect(first.database).toBe('orders')
    expect(sql.database).toBe('orders')
    const stored = sessionStorage.getItem('zorth.sql-editor.drafts.v1') || ''
    expect(stored).toContain('"kind":"table"')
    expect(stored).toContain('order_item')
  })
})
