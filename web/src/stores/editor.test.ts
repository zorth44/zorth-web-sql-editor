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
})
