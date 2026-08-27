import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { DatabaseObjectType, SqlExecutionResult, TableItem } from '@/types/contracts'
import type { TableDataPredicate, TableDataSort } from '@/sql-editor/table-data-filter'

const STORAGE_KEY = 'zorth.sql-editor.drafts.v1'
const MAX_BYTES = 200_000
export type EditorTabKind = 'sql' | 'table'
export type TableViewerPane = 'data' | 'properties'
export type StatementStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED'

export interface StatementRun {
  position: number
  sql: string
  status: StatementStatus
  result: SqlExecutionResult | null
  error: string | null
}

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
  statements: StatementRun[]
  resultIndex: number
  runningIndex: number | null
  scriptId: string | null
  savedVersion: number | null
  savedName: string | null
  savedSql: string | null
  savedDataSourceId: string | null
  savedDatabase: string | null
  dataFilterDrafts: Record<string, string>
  dataFilterErrors: Record<string, string>
  dataAppliedPredicates: TableDataPredicate[]
  dataSort: TableDataSort | null
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
  scriptId?: string | null
  savedVersion?: number | null
  savedName?: string | null
  savedSql?: string | null
  savedDataSourceId?: string | null
  savedDatabase?: string | null
}
interface DraftBundle {
  activeId: string
  tabs: Draft[]
}

export function isIdleTab(
  tab: Pick<EditorTab, 'kind' | 'sql' | 'result' | 'error' | 'running' | 'scriptId'>,
): boolean {
  return (
    tab.kind === 'sql' &&
    !tab.scriptId &&
    !tab.sql.trim() &&
    !tab.result &&
    !tab.error &&
    !tab.running
  )
}

export function isDirtyTab(tab: EditorTab): boolean {
  if (tab.kind !== 'sql') return false
  if (!tab.scriptId) return Boolean(tab.sql.trim())
  return (
    tab.sql !== tab.savedSql ||
    tab.title !== tab.savedName ||
    tab.dataSourceId !== tab.savedDataSourceId ||
    tab.database !== tab.savedDatabase
  )
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

function emptyTableQuery(): Pick<
  EditorTab,
  'dataFilterDrafts' | 'dataFilterErrors' | 'dataAppliedPredicates' | 'dataSort'
> {
  return {
    dataFilterDrafts: {},
    dataFilterErrors: {},
    dataAppliedPredicates: [],
    dataSort: null,
  }
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
    statements: [],
    resultIndex: 0,
    runningIndex: null,
    scriptId: tableTab ? null : item.scriptId || null,
    savedVersion: tableTab ? null : item.savedVersion || null,
    savedName: tableTab ? null : item.savedName || null,
    savedSql: tableTab ? null : (item.savedSql ?? item.sql),
    savedDataSourceId: tableTab ? null : (item.savedDataSourceId ?? item.dataSourceId),
    savedDatabase: tableTab ? null : (item.savedDatabase ?? item.database),
    ...emptyTableQuery(),
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
  const cancelled = new Set<string>()
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
          scriptId,
          savedVersion,
          savedName,
          savedSql,
          savedDataSourceId,
          savedDatabase,
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
          scriptId: kind === 'table' ? null : scriptId,
          savedVersion: kind === 'table' ? null : savedVersion,
          savedName: kind === 'table' ? null : savedName,
          savedSql: kind === 'table' ? null : savedSql,
          savedDataSourceId: kind === 'table' ? null : savedDataSourceId,
          savedDatabase: kind === 'table' ? null : savedDatabase,
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
      statements: [],
      resultIndex: 0,
      runningIndex: null,
      scriptId: null,
      savedVersion: null,
      savedName: null,
      savedSql: null,
      savedDataSourceId: null,
      savedDatabase: null,
      ...emptyTableQuery(),
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
      statements: [],
      resultIndex: 0,
      runningIndex: null,
      scriptId: null,
      savedVersion: null,
      savedName: null,
      savedSql: null,
      savedDataSourceId: null,
      savedDatabase: null,
      ...emptyTableQuery(),
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
  function setTableDataFilterDrafts(id: string, drafts: Record<string, string>): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.kind !== 'table') return
    tab.dataFilterDrafts = drafts
  }
  function setTableDataFilterErrors(id: string, errors: Record<string, string>): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.kind !== 'table') return
    tab.dataFilterErrors = errors
  }
  function commitTableDataFilters(id: string, predicates: TableDataPredicate[]): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.kind !== 'table') return
    tab.dataAppliedPredicates = predicates
    tab.dataFilterErrors = {}
  }
  function setTableDataSort(id: string, sort: TableDataSort | null): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.kind !== 'table') return
    tab.dataSort = sort
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
    cancelled.delete(id)
    tabs.value.splice(index, 1)
    if (activeId.value === id) activeId.value = tabs.value[Math.max(0, index - 1)]?.id || ''
    persist()
  }
  function beginScript(id: string, sqls: string[]): void {
    if (runningCount.value >= 3) throw new Error('最多同时执行 3 条 SQL')
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.running) throw new Error('当前页签正在执行')
    cancelled.delete(id)
    tab.running = true
    tab.executionId = null
    tab.result = null
    tab.error = null
    tab.resultIndex = 0
    tab.runningIndex = null
    tab.statements = sqls.map((sql, index) => ({
      position: index + 1,
      sql,
      status: 'PENDING',
      result: null,
      error: null,
    }))
  }
  function startStatement(id: string, index: number, executionId: string): AbortController {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab) throw new Error('页签不存在')
    const statement = tab.statements[index]
    if (!statement) throw new Error('语句不存在')
    const controller = new AbortController()
    aborters.set(id, controller)
    tab.executionId = executionId
    tab.runningIndex = index
    statement.status = 'RUNNING'
    return controller
  }
  function finishStatement(
    id: string,
    index: number,
    result?: SqlExecutionResult,
    error?: string,
  ): void {
    const tab = tabs.value.find((item) => item.id === id)
    aborters.delete(id)
    if (!tab) return
    const statement = tab.statements[index]
    if (statement) {
      statement.status = error ? 'FAILED' : 'SUCCESS'
      statement.result = result || null
      statement.error = error || null
    }
    tab.runningIndex = null
    tab.resultIndex = index
    tab.result = result || null
    tab.error = error || null
  }
  function endScript(id: string): void {
    const tab = tabs.value.find((item) => item.id === id)
    aborters.delete(id)
    cancelled.delete(id)
    if (!tab) return
    tab.running = false
    tab.runningIndex = null
    for (const statement of tab.statements) {
      if (statement.status === 'PENDING' || statement.status === 'RUNNING') {
        statement.status = 'SKIPPED'
      }
    }
  }
  function start(id: string, executionId: string, sql = ''): AbortController {
    beginScript(id, [sql])
    return startStatement(id, 0, executionId)
  }
  function finish(id: string, result?: SqlExecutionResult, error?: string): void {
    const tab = tabs.value.find((item) => item.id === id)
    finishStatement(id, tab?.runningIndex ?? 0, result, error)
    endScript(id)
  }
  function viewResult(id: string, index: number): void {
    const tab = tabs.value.find((item) => item.id === id)
    const statement = tab?.statements[index]
    if (!tab || !statement) return
    tab.resultIndex = index
    tab.result = statement.result
    tab.error = statement.error
  }
  /** Stops the in-flight statement and tells a running script loop not to continue. */
  function abort(id: string): void {
    cancelled.add(id)
    aborters.get(id)?.abort()
  }
  function isCancelled(id: string): boolean {
    return cancelled.has(id)
  }
  function markSaved(
    id: string,
    script: {
      id: string
      name: string
      version: number
      statement: string
      dataSourceId: string | null
      database: string | null
    },
  ): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.kind !== 'sql') return
    tab.scriptId = script.id
    tab.title = script.name
    tab.sql = script.statement
    tab.dataSourceId = script.dataSourceId
    tab.database = script.database
    tab.savedVersion = script.version
    tab.savedName = script.name
    tab.savedSql = script.statement
    tab.savedDataSourceId = script.dataSourceId
    tab.savedDatabase = script.database
    persist()
  }
  function applyRename(scriptId: string, name: string, version: number): void {
    for (const tab of tabs.value) {
      if (tab.kind !== 'sql' || tab.scriptId !== scriptId) continue
      tab.title = name
      tab.savedName = name
      tab.savedVersion = version
    }
    persist()
  }
  function renameLocal(id: string, name: string): void {
    const tab = tabs.value.find((item) => item.id === id)
    if (!tab || tab.kind !== 'sql') return
    tab.title = name
    persist()
  }
  function unbindScript(scriptId: string): void {
    for (const tab of tabs.value) {
      if (tab.scriptId !== scriptId) continue
      tab.scriptId = null
      tab.savedVersion = null
      tab.savedName = null
      tab.savedSql = null
      tab.savedDataSourceId = null
      tab.savedDatabase = null
    }
    persist()
  }
  function findByScriptId(scriptId: string): EditorTab | undefined {
    return tabs.value.find((tab) => tab.kind === 'sql' && tab.scriptId === scriptId)
  }
  function clearAll(): void {
    for (const controller of aborters.values()) controller.abort()
    aborters.clear()
    cancelled.clear()
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
    setTableDataFilterDrafts,
    setTableDataFilterErrors,
    commitTableDataFilters,
    setTableDataSort,
    ensureTab,
    bindTab,
    activateConnection,
    setActive,
    updateSql,
    closeTab,
    beginScript,
    startStatement,
    finishStatement,
    endScript,
    start,
    finish,
    viewResult,
    abort,
    isCancelled,
    markSaved,
    applyRename,
    renameLocal,
    unbindScript,
    findByScriptId,
    isDirtyTab,
    clearAll,
  }
})
