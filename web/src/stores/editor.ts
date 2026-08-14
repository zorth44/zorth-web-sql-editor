import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { SqlExecutionResult } from '@/types/contracts'

const STORAGE_KEY = 'zorth.sql-editor.drafts.v1'
const MAX_BYTES = 200_000
export interface EditorTab {
  id: string
  title: string
  dataSourceId: string | null
  database: string | null
  sql: string
  running: boolean
  executionId: string | null
  result: SqlExecutionResult | null
  error: string | null
}
interface Draft {
  id: string
  title: string
  dataSourceId: string | null
  database: string | null
  sql: string
}

function restore(): Draft[] {
  try {
    const value = sessionStorage.getItem(STORAGE_KEY)
    if (!value || value.length > MAX_BYTES) return []
    const parsed: unknown = JSON.parse(value)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((item): item is Draft =>
      Boolean(
        item &&
          typeof item === 'object' &&
          typeof item.id === 'string' &&
          typeof item.title === 'string' &&
          typeof item.sql === 'string' &&
          item.sql.length <= MAX_BYTES,
      ),
    )
  } catch {
    return []
  }
}

export const useEditorStore = defineStore('editor', () => {
  const restored = restore()
  const tabs = ref<EditorTab[]>(
    restored.map((item) => ({
      ...item,
      running: false,
      executionId: null,
      result: null,
      error: null,
    })),
  )
  const activeId = ref(tabs.value[0]?.id || '')
  const aborters = new Map<string, AbortController>()
  const active = computed(() => tabs.value.find((tab) => tab.id === activeId.value) || null)
  const runningCount = computed(() => tabs.value.filter((tab) => tab.running).length)

  function persist(): void {
    const drafts = tabs.value.map(({ id, title, dataSourceId, database, sql }) => ({
      id,
      title,
      dataSourceId,
      database,
      sql,
    }))
    const value = JSON.stringify(drafts)
    if (value.length <= MAX_BYTES) sessionStorage.setItem(STORAGE_KEY, value)
  }
  function createTab(
    dataSourceId: string | null,
    database: string | null,
    sql = '',
    title?: string,
  ): EditorTab {
    const tab: EditorTab = {
      id: crypto.randomUUID(),
      title: title || `Query ${tabs.value.length + 1}`,
      dataSourceId,
      database,
      sql,
      running: false,
      executionId: null,
      result: null,
      error: null,
    }
    tabs.value.push(tab)
    activeId.value = tab.id
    persist()
    return tab
  }
  function ensureTab(dataSourceId: string | null, database: string | null): EditorTab {
    return active.value || createTab(dataSourceId, database)
  }
  function setActive(id: string): void {
    if (tabs.value.some((tab) => tab.id === id)) activeId.value = id
  }
  function updateSql(id: string, sql: string): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (tab) {
      tab.sql = sql
      persist()
    }
  }
  function closeTab(id: string): void {
    const index = tabs.value.findIndex((item) => item.id === id)
    if (index < 0) return
    aborters.get(id)?.abort()
    aborters.delete(id)
    tabs.value.splice(index, 1)
    if (activeId.value === id) activeId.value = tabs.value[Math.max(0, index - 1)]?.id || ''
    persist()
  }
  function start(id: string, executionId: string): AbortController {
    if (runningCount.value >= 3) throw new Error('最多同时执行 3 条 SQL')
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.running) throw new Error('当前页签正在执行')
    const controller = new AbortController()
    aborters.set(id, controller)
    tab.running = true
    tab.executionId = executionId
    tab.result = null
    tab.error = null
    return controller
  }
  function finish(id: string, result?: SqlExecutionResult, error?: string): void {
    const tab = tabs.value.find((item) => item.id === id)
    aborters.delete(id)
    if (!tab) return
    tab.running = false
    tab.result = result || null
    tab.error = error || null
  }
  function abort(id: string): void {
    aborters.get(id)?.abort()
  }
  function clearAll(): void {
    for (const controller of aborters.values()) controller.abort()
    aborters.clear()
    tabs.value = []
    activeId.value = ''
    sessionStorage.removeItem(STORAGE_KEY)
  }
  return {
    tabs,
    activeId,
    active,
    runningCount,
    createTab,
    ensureTab,
    setActive,
    updateSql,
    closeTab,
    start,
    finish,
    abort,
    clearAll,
  }
})
