import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { DatabaseObjectType, SqlExecutionResult, TableItem } from '@/types/contracts'

const STORAGE_KEY = 'zorth.sql-editor.drafts.v1'
const MAX_BYTES = 200_000
export type EditorTabKind = 'sql' | 'table'
export type TableViewerPane = 'data' | 'properties'

export interface EditorTab {
  id: string
  kind: EditorTabKind
  title: string
  dataSourceId: string | null
  database: string | null
  sql: string
  table: string | null
  tableType: DatabaseObjectType | null
  tableComment: string | null
  viewerPane: TableViewerPane
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
  kind?: EditorTabKind
  table?: string | null
  tableType?: DatabaseObjectType | null
  tableComment?: string | null
  viewerPane?: TableViewerPane
}
interface DraftBundle {
  activeId: string
  tabs: Draft[]
}

export function isIdleTab(
  tab: Pick<EditorTab, 'kind' | 'sql' | 'result' | 'error' | 'running'>,
): boolean {
  return tab.kind === 'sql' && !tab.sql.trim() && !tab.result && !tab.error && !tab.running
}

function isDraft(item: unknown): item is Draft {
  if (!item || typeof item !== 'object') return false
  const draft = item as Draft
  if (
    typeof draft.id !== 'string' ||
    typeof draft.title !== 'string' ||
    typeof draft.sql !== 'string' ||
    draft.sql.length > MAX_BYTES
  ) {
    return false
  }
  if (draft.kind === 'table') return typeof draft.table === 'string' && draft.table.length > 0
  return draft.kind === undefined || draft.kind === 'sql'
}

function toTab(item: Draft): EditorTab {
  const tableTab = item.kind === 'table'
  return {
    id: item.id,
    kind: tableTab ? 'table' : 'sql',
    title: item.title,
    dataSourceId: item.dataSourceId,
    database: item.database,
    sql: tableTab ? '' : item.sql,
    table: tableTab ? item.table || null : null,
    tableType: tableTab ? item.tableType || 'TABLE' : null,
    tableComment: tableTab ? item.tableComment || null : null,
    viewerPane: tableTab && item.viewerPane === 'properties' ? 'properties' : 'data',
    running: false,
    executionId: null,
    result: null,
    error: null,
  }
}

function restore(): DraftBundle {
  try {
    const value = sessionStorage.getItem(STORAGE_KEY)
    if (!value || value.length > MAX_BYTES) return { activeId: '', tabs: [] }
    const parsed: unknown = JSON.parse(value)
    if (Array.isArray(parsed)) {
      const tabs = parsed.filter(isDraft)
      return { activeId: tabs[0]?.id || '', tabs }
    }
    if (parsed && typeof parsed === 'object' && Array.isArray((parsed as DraftBundle).tabs)) {
      const tabs = (parsed as DraftBundle).tabs.filter(isDraft)
      const activeId =
        typeof (parsed as DraftBundle).activeId === 'string' &&
        tabs.some((tab) => tab.id === (parsed as DraftBundle).activeId)
          ? (parsed as DraftBundle).activeId
          : tabs[0]?.id || ''
      return { activeId, tabs }
    }
    return { activeId: '', tabs: [] }
  } catch {
    return { activeId: '', tabs: [] }
  }
}

function nextQueryTitle(tabs: { title: string }[]): string {
  let max = 0
  for (const tab of tabs) {
    const match = /^Query (\d+)$/.exec(tab.title)
    if (match) max = Math.max(max, Number(match[1]))
  }
  return `Query ${max + 1}`
}

function sameTable(tab: EditorTab, dataSourceId: string, database: string, table: string): boolean {
  return (
    tab.kind === 'table' &&
    tab.dataSourceId === dataSourceId &&
    tab.database === database &&
    tab.table === table
  )
}

export const useEditorStore = defineStore('editor', () => {
  const restored = restore()
  const tabs = ref<EditorTab[]>(restored.tabs.map(toTab))
  const activeId = ref(restored.activeId || tabs.value[0]?.id || '')
  const aborters = new Map<string, AbortController>()
  const active = computed(() => tabs.value.find((tab) => tab.id === activeId.value) || null)
  const runningCount = computed(() => tabs.value.filter((tab) => tab.running).length)

  function persist(): void {
    const bundle: DraftBundle = {
      activeId: activeId.value,
      tabs: tabs.value.map(
        ({
          id,
          kind,
          title,
          dataSourceId,
          database,
          sql,
          table,
          tableType,
          tableComment,
          viewerPane,
        }) => ({
          id,
          kind,
          title,
          dataSourceId,
          database,
          sql: kind === 'table' ? '' : sql,
          table,
          tableType,
          tableComment,
          viewerPane,
        }),
      ),
    }
    const value = JSON.stringify(bundle)
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
      kind: 'sql',
      title: title || nextQueryTitle(tabs.value),
      dataSourceId,
      database,
      sql,
      table: null,
      tableType: null,
      tableComment: null,
      viewerPane: 'data',
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
  function openTableTab(
    dataSourceId: string,
    database: string,
    table: TableItem,
    pane: TableViewerPane = 'data',
  ): EditorTab {
    const existing = tabs.value.find((tab) => sameTable(tab, dataSourceId, database, table.name))
    if (existing) {
      existing.viewerPane = pane
      existing.tableType = table.type
      existing.tableComment = table.comment
      activeId.value = existing.id
      persist()
      return existing
    }
    const tab: EditorTab = {
      id: crypto.randomUUID(),
      kind: 'table',
      title: table.name,
      dataSourceId,
      database,
      sql: '',
      table: table.name,
      tableType: table.type,
      tableComment: table.comment,
      viewerPane: pane,
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
  function setViewerPane(id: string, pane: TableViewerPane): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.kind !== 'table') return
    tab.viewerPane = pane
    persist()
  }
  function ensureTab(dataSourceId: string | null, database: string | null): EditorTab {
    return active.value || createTab(dataSourceId, database)
  }
  function bindTab(id: string, dataSourceId: string | null, database: string | null): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.running || tab.kind === 'table') return
    tab.dataSourceId = dataSourceId
    tab.database = database
    persist()
  }
  function activateConnection(dataSourceId: string | null, database: string | null): EditorTab {
    const current = active.value
    if (
      current?.kind === 'sql' &&
      current.dataSourceId === dataSourceId &&
      current.database === database
    ) {
      return current
    }
    if (current && isIdleTab(current)) {
      bindTab(current.id, dataSourceId, database)
      return current
    }
    const matching = tabs.value.find(
      (tab) => tab.dataSourceId === dataSourceId && tab.database === database && isIdleTab(tab),
    )
    if (matching) {
      activeId.value = matching.id
      persist()
      return matching
    }
    return createTab(dataSourceId, database)
  }
  function setActive(id: string): void {
    if (tabs.value.some((tab) => tab.id === id)) {
      activeId.value = id
      persist()
    }
  }
  function updateSql(id: string, sql: string): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (tab && tab.kind === 'sql') {
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
    openTableTab,
    setViewerPane,
    ensureTab,
    bindTab,
    activateConnection,
    setActive,
    updateSql,
    closeTab,
    start,
    finish,
    abort,
    clearAll,
  }
})
