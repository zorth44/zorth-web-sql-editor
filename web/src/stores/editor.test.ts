import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useEditorStore } from '@/stores/editor'
import type { SqlExecutionResult } from '@/types/contracts'
beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
})
function result(executionId: string): SqlExecutionResult {
  return {
    kind: 'RESULT_SET',
    executionId,
    columns: [],
    rows: [],
    rowCount: 0,
    truncated: false,
    durationMs: 1,
  }
}
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
  it('advances a script statement by statement and keeps every result', () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders', 'select 1;select 2')
    store.beginScript(tab.id, ['select 1', 'select 2'])
    expect(tab.statements.map((item) => item.status)).toEqual(['PENDING', 'PENDING'])
    for (const index of [0, 1]) {
      store.startStatement(tab.id, index, crypto.randomUUID())
      expect(tab.runningIndex).toBe(index)
      store.finishStatement(tab.id, index, result(`e-${index}`))
    }
    store.endScript(tab.id)
    expect(tab.running).toBe(false)
    expect(tab.statements.map((item) => item.status)).toEqual(['SUCCESS', 'SUCCESS'])
    expect(tab.statements.map((item) => item.result?.executionId)).toEqual(['e-0', 'e-1'])
    store.viewResult(tab.id, 0)
    expect(tab.result?.executionId).toBe('e-0')
    expect(tab.resultIndex).toBe(0)
  })
  it('marks statements after a failure as skipped', () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    store.beginScript(tab.id, ['select 1', 'boom', 'select 3'])
    store.startStatement(tab.id, 0, crypto.randomUUID())
    store.finishStatement(tab.id, 0, result('ok'))
    store.startStatement(tab.id, 1, crypto.randomUUID())
    store.finishStatement(tab.id, 1, undefined, 'SQL 执行失败')
    store.endScript(tab.id)
    expect(tab.statements.map((item) => item.status)).toEqual(['SUCCESS', 'FAILED', 'SKIPPED'])
    expect(tab.statements[0]?.result?.executionId).toBe('ok')
    expect(tab.error).toBe('SQL 执行失败')
  })
  it('flags cancellation so a running script loop stops', () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    store.beginScript(tab.id, ['select 1', 'select 2'])
    const controller = store.startStatement(tab.id, 0, crypto.randomUUID())
    expect(store.isCancelled(tab.id)).toBe(false)
    store.abort(tab.id)
    expect(controller.signal.aborted).toBe(true)
    expect(store.isCancelled(tab.id)).toBe(true)
    store.finishStatement(tab.id, 0, undefined, '执行已取消')
    store.endScript(tab.id)
    expect(tab.statements.map((item) => item.status)).toEqual(['FAILED', 'SKIPPED'])
    expect(store.isCancelled(tab.id)).toBe(false)
  })
  it('rejects a second script in a tab that is already running', () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    store.beginScript(tab.id, ['select 1'])
    expect(() => store.beginScript(tab.id, ['select 2'])).toThrow('当前页签正在执行')
  })
  it('marks unsaved SQL dirty and clean saved tabs clean', () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders', 'select 1')
    expect(store.isDirtyTab(tab)).toBe(true)
    store.markSaved(tab.id, {
      id: 'script-1',
      name: '月报',
      version: 1,
      statement: 'select 1',
      dataSourceId: 'ds-1',
      database: 'orders',
    })
    expect(tab.title).toBe('月报')
    expect(store.isDirtyTab(tab)).toBe(false)
    store.updateSql(tab.id, 'select 2')
    expect(store.isDirtyTab(tab)).toBe(true)
    const stored = sessionStorage.getItem('zorth.sql-editor.drafts.v1') || ''
    expect(stored).toContain('script-1')
    expect(stored).not.toContain('RESULT_SET')
    store.clearAll()
    expect(sessionStorage.getItem('zorth.sql-editor.drafts.v1')).toBeNull()
  })
  it('keeps local rename of an unsaved tab and applies saved rename', () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders', 'select 1', 'Query 1')
    store.renameLocal(tab.id, '草稿')
    expect(tab.title).toBe('草稿')
    store.markSaved(tab.id, {
      id: 'script-2',
      name: '草稿',
      version: 1,
      statement: 'select 1',
      dataSourceId: 'ds-1',
      database: 'orders',
    })
    store.applyRename('script-2', '月报 v2', 2)
    expect(tab.title).toBe('月报 v2')
    expect(tab.savedVersion).toBe(2)
    expect(store.isDirtyTab(tab)).toBe(false)
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
