<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Splitpanes, Pane } from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import {
  Database,
  FileCode,
  Files,
  History,
  Play,
  Plus,
  Save,
  Sparkles,
  Square,
  Table2,
  WandSparkles,
  X,
} from 'lucide-vue-next'
import { listDataSources } from '@/api/data-sources'
import { listEngines } from '@/api/engines'
import { cancelExecution, executeSql, exportExecution } from '@/api/executions'
import { getHistory } from '@/api/history'
import { createScript, getScript, updateScript } from '@/api/scripts'
import { ApiError, safeErrorMessage } from '@/api/api-error'
import { editorLanguageFor, engineById, identifierQuoteFor } from '@/data-sources/catalog'
import { useQuery } from '@tanstack/vue-query'
import ResourceBrowser from '@/components/resource-tree/ResourceBrowser.vue'
import HistoryPanel from '@/components/history/HistoryPanel.vue'
import ScriptPanel from '@/components/scripts/ScriptPanel.vue'
import ScriptResultPanel from '@/components/result-grid/ScriptResultPanel.vue'
import TableViewer from '@/components/table-viewer/TableViewer.vue'
import WelcomeStart from '@/components/welcome/WelcomeStart.vue'
import SqlMonacoEditor from '@/components/editor/SqlMonacoEditor.vue'
import CopilotPanel from '@/components/copilot/CopilotPanel.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { DEFAULT_ROW_LIMIT } from '@/components/result-grid/limits'
import { likelyNeedsDatabase, selectTableData } from '@/sql-editor/sql'
import { planScript, runScript as runScriptStatements } from '@/sql-editor/script-runner'
import { buildCopilotMessage, COPILOT_FIX_PROMPT } from '@/sql-editor/copilot-context'
import { appendSqlText, replaceSqlOnce } from '@/sql-editor/sql-insert'
import {
  SIDEBAR_DEFAULT_PX,
  SIDEBAR_MAX_PX,
  SIDEBAR_MIN_PX,
  fitSidebarWidth,
  pxToPanePercent,
} from '@/sql-editor/sidebar-width'
import { isDirtyTab, useEditorStore } from '@/stores/editor'
import { useCopilotStore } from '@/stores/copilot'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import { queryClient, queryKeys } from '@/query/client'
import type { DataSourceListItem, HistoryDetail, ScriptSummary, TableItem } from '@/types/contracts'

const route = useRoute()
const router = useRouter()
const editor = useEditorStore()
const copilot = useCopilotStore()
const auth = useAuthStore()
const notifications = useNotificationsStore()
const monacoRef = ref<{
  getRunnableStatement: () => string
  getRunnableScript: () => string
  getCopilotSql: () => string
  formatSql: () => void
  insertAtCursor: (sql: string) => void
  appendSql: (sql: string) => void
  replaceSql: (target: string, sql: string) => boolean
  focus: () => void
} | null>(null)
const hasSelection = ref(false)

const sources = ref<DataSourceListItem[]>([])
const enginesQuery = useQuery({
  queryKey: queryKeys.engines(),
  queryFn: listEngines,
  staleTime: 60_000,
})
const engines = computed(() => enginesQuery.data.value?.items || [])
const selectedSource = ref<string | null>(null)
const selectedDatabase = ref<string | null>(null)
const side = ref<'database' | 'history' | 'scripts'>('database')
const sideCollapsed = ref(false)
const splitHost = ref<HTMLElement | null>(null)
const splitWidth = ref(typeof window === 'undefined' ? 1200 : Math.max(window.innerWidth - 48, 1))
const sideWidthPx = ref(SIDEBAR_DEFAULT_PX)
const copilotWidthPx = ref(340)
const userSized = ref(false)
const sideFitKey = ref('init')
const resourceNonce = ref(0)
const sideSize = computed(() => pxToPanePercent(sideWidthPx.value, splitWidth.value))
const sideMinSize = computed(() => pxToPanePercent(SIDEBAR_MIN_PX, splitWidth.value))
const sideMaxSize = computed(() => pxToPanePercent(SIDEBAR_MAX_PX, splitWidth.value))
const copilotSize = computed(() => pxToPanePercent(copilotWidthPx.value, splitWidth.value))
const copilotMinSize = computed(() => pxToPanePercent(280, splitWidth.value))
const copilotMaxSize = computed(() => pxToPanePercent(480, splitWidth.value))
const exporting = ref(false)
const exportOpen = ref(false)
const rowLimit = ref(DEFAULT_ROW_LIMIT)
const pendingCloseId = ref<string | null>(null)
const saveOpen = ref(false)
const saveAsMode = ref(false)
const saveName = ref('')
const saveBusy = ref(false)
const renameOpen = ref(false)
const renameTarget = ref<{
  scriptId: string
  name: string
  version: number
  statement: string
  dataSourceId: string | null
  database: string | null
} | null>(null)
const renameValue = ref('')
const editingTitleId = ref<string | null>(null)
const titleDraft = ref('')
const scriptPanelRef = ref<{ reload: () => Promise<void> | void } | null>(null)
const metadataSuggestions = ref<string[]>([])
let exportAbort: AbortController | undefined

const canExecute = computed(() => auth.session?.capabilities.includes('SQL_EXECUTE') ?? false)
const canExport = computed(() => auth.session?.capabilities.includes('SQL_EXPORT') ?? false)
const canHistory = computed(() => auth.session?.capabilities.includes('HISTORY_READ') ?? false)
const canScripts = computed(() => auth.session?.capabilities.includes('SCRIPT_MANAGE') ?? false)
const active = computed(() => editor.active)
const editorLanguage = computed(() => {
  const engine = sources.value.find((item) => item.id === active.value?.dataSourceId)?.engine
  return editorLanguageFor(engineById(engines.value, engine))
})
const suggestions = computed(() =>
  Array.from(new Set([...metadataSuggestions.value, ...sources.value.map((item) => item.name)])),
)
const currentSource = computed(
  () => sources.value.find((item) => item.id === selectedSource.value) || null,
)
const isMac = /Mac|iPhone|iPad/.test(navigator.platform)
const runShortcut = isMac ? '⌘⇧↵' : 'Ctrl+Shift+Enter'
const statementShortcut = isMac ? '⌘↵' : 'Ctrl+Enter'
const formatShortcut = isMac ? '⌥⇧F' : 'Shift+Alt+F'
const copilotShortcut = isMac ? '⌘L' : 'Ctrl+L'
const saveShortcut = isMac ? '⌘S' : 'Ctrl+S'
const runLabel = computed(() => (hasSelection.value ? '运行选中' : '运行'))
const runTitle = computed(() =>
  hasSelection.value
    ? `运行选中的全部语句（${runShortcut}），运行当前语句用 ${statementShortcut}`
    : `运行编辑器全部语句（${runShortcut}），运行当前语句用 ${statementShortcut}`,
)
const copilotReady = computed(() => {
  const tab = active.value
  return Boolean(tab && tab.kind === 'sql' && tab.dataSourceId && tab.database)
})
const copilotDisabledReason = computed(() => {
  const tab = active.value
  if (!tab) return '打开 SQL 页签后使用 Copilot'
  if (tab.kind !== 'sql') return '请切换到 SQL 页签使用 Copilot'
  if (!tab.dataSourceId || !tab.database) return '请先在左侧选择数据源和数据库'
  return ''
})
const dialectLabel = computed(() => {
  const engine = engineById(engines.value, currentSource.value?.engine)
  return engine?.displayName || editorLanguage.value
})
const copilotMessages = computed(() => copilot.messages)
const copilotFixDisabled = computed(() => !copilotReady.value || copilot.inflight)

async function syncUrl() {
  const query: { dataSourceId?: string; database?: string } = {}
  if (selectedSource.value) query.dataSourceId = selectedSource.value
  if (selectedDatabase.value) query.database = selectedDatabase.value
  await router.replace({ path: '/sql-editor', query })
}
function applyConnection(sourceId: string | null, database: string | null): void {
  selectedSource.value = sourceId
  selectedDatabase.value = database
  editor.activateConnection(sourceId, database)
  void syncUrl()
}
async function openCopilotConversation(id: string): Promise<void> {
  await copilot.openConversation(id)
  if (copilot.conversationId !== id) return
  const sourceId = copilot.datasourceId
  const database = copilot.database
  if (!sourceId) return
  if (!sources.value.some((item) => item.id === sourceId)) {
    copilot.notice = '原数据源已不可用，请在左侧重新选择'
    return
  }
  applyConnection(sourceId, database)
  side.value = 'database'
  sideCollapsed.value = false
}
function newBoundTab() {
  editor.createTab(selectedSource.value, selectedDatabase.value)
  void nextTick(() => monacoRef.value?.focus())
}
function toggleSide(next: 'database' | 'history' | 'scripts') {
  if (side.value === next && !sideCollapsed.value) {
    sideCollapsed.value = true
    return
  }
  side.value = next
  syncSplitWidth()
  sideCollapsed.value = false
}
function selectConnection(sourceId: string, database: string) {
  selectedSource.value = sourceId
  selectedDatabase.value = database
  if (editor.active && editor.active.kind !== 'table') {
    editor.activateConnection(sourceId, database)
  }
  void syncUrl()
}
function openTable(payload: {
  dataSourceId: string
  database: string
  table: TableItem
  pane: 'data' | 'properties'
}) {
  selectedSource.value = payload.dataSourceId
  selectedDatabase.value = payload.database
  const tab = editor.openTableTab(
    payload.dataSourceId,
    payload.database,
    payload.table,
    payload.pane,
  )
  void syncUrl()
  if (tab.viewerPane === 'data') void loadTableData(tab.id)
}
async function insertSql(sql: string, dataSourceId: string, database: string) {
  applyConnection(dataSourceId, database)
  await nextTick()
  const tab = editor.active
  if (!tab) return
  if (monacoRef.value) monacoRef.value.insertAtCursor(sql)
  else editor.updateSql(tab.id, `${tab.sql.trim() ? `${tab.sql}\n` : ''}${sql}`)
}
function runScript() {
  void executeScriptText(monacoRef.value?.getRunnableScript() || active.value?.sql || '')
}
function formatSql() {
  monacoRef.value?.formatSql()
}
async function loadTableData(id: string, force = false) {
  const tab = editor.tabs.find((item) => item.id === id)
  if (!tab || tab.kind !== 'table' || tab.running) return
  if (!force && (tab.result || tab.error)) return
  if (!tab.dataSourceId || !tab.database || !tab.table) return
  if (!canExecute.value) {
    editor.finish(id, undefined, '没有 SQL 执行权限')
    return
  }
  await executeOnTab(
    id,
    selectTableData(
      tab.database,
      tab.table,
      identifierQuoteFor(
        engineById(
          engines.value,
          sources.value.find((item) => item.id === tab.dataSourceId)?.engine,
        ),
      ),
    ),
  )
}
async function executeOnTab(tabId: string, statement: string) {
  await executeStatements(tabId, [statement.trim()])
}
async function executeStatements(tabId: string, statements: string[]) {
  const tab = editor.tabs.find((item) => item.id === tabId)
  if (!tab || !tab.dataSourceId) return
  const dataSourceId = tab.dataSourceId
  const database = tab.database
  let outcome
  try {
    outcome = await runScriptStatements(editor, {
      tabId,
      statements,
      newExecutionId: () => crypto.randomUUID(),
      describeError: (error) =>
        error instanceof DOMException && error.name === 'AbortError'
          ? '执行已取消'
          : safeErrorMessage(error, 'SQL 执行失败'),
      execute: (statement, executionId, signal) =>
        executeSql(
          { executionId, dataSourceId, database, statement, rowLimit: rowLimit.value },
          signal,
        ),
    })
  } catch (e) {
    notice(e instanceof Error ? e.message : '无法执行')
    return
  }
  if (outcome.sawDdl) {
    resourceNonce.value++
    await queryClient.invalidateQueries({ queryKey: queryKeys.metadata(dataSourceId) })
  }
  await queryClient.invalidateQueries({ queryKey: ['sql-history'] })
}
async function run(statement: string) {
  const tab = active.value
  if (!tab || tab.kind !== 'sql' || !canExecute.value) return
  const sql = statement.trim()
  if (!sql) {
    notice('请输入要执行的 SQL，或将光标放在目标语句上')
    return
  }
  if (!tab.dataSourceId) {
    notice('请在左侧导航选择数据源')
    return
  }
  if (!tab.database && likelyNeedsDatabase(sql)) {
    notice('请在左侧导航选择数据库')
    return
  }
  await executeStatements(tab.id, [sql])
}
async function executeScriptText(text: string) {
  const tab = active.value
  if (!tab || tab.kind !== 'sql' || !canExecute.value) return
  if (!text.trim()) {
    notice('请输入要执行的 SQL')
    return
  }
  if (!tab.dataSourceId) {
    notice('请在左侧导航选择数据源')
    return
  }
  const plan = planScript(text, { hasDatabase: Boolean(tab.database) })
  if (!plan.ok) {
    notice(plan.message)
    return
  }
  if (plan.warning) notice(plan.warning)
  await executeStatements(tab.id, plan.statements)
}
async function stop() {
  const tab = active.value
  if (!tab?.executionId) return
  try {
    await cancelExecution(tab.executionId)
  } catch (e) {
    notice(safeErrorMessage(e, '取消请求失败'))
  } finally {
    editor.abort(tab.id)
  }
}
function requestExport() {
  if (active.value?.result?.kind === 'RESULT_SET' && canExport.value) exportOpen.value = true
}
async function download() {
  const tab = active.value
  if (!tab || tab.result?.kind !== 'RESULT_SET' || !canExport.value) return
  exportOpen.value = false
  exportAbort = new AbortController()
  exporting.value = true
  try {
    const file = await exportExecution(tab.result.executionId, 100000, exportAbort.signal)
    const url = URL.createObjectURL(file.blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename
    link.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    if (!(e instanceof DOMException && e.name === 'AbortError'))
      notice(safeErrorMessage(e, '导出失败'))
  } finally {
    exportAbort = undefined
    exporting.value = false
  }
}
function requestClose(id: string) {
  const tab = editor.tabs.find((item) => item.id === id)
  if (!tab) return
  if (tab.kind === 'sql' && isDirtyTab(tab)) {
    pendingCloseId.value = id
    return
  }
  closeTab(id)
}
function closeTab(id: string) {
  pendingCloseId.value = null
  editor.closeTab(id)
}
function openHistory(detail: HistoryDetail) {
  if (detail.connectionAvailable) {
    selectedSource.value = detail.dataSourceId
    selectedDatabase.value = detail.database
  } else {
    selectedSource.value = null
    selectedDatabase.value = null
    notice('原连接已不可用，请在左侧导航重新选择数据源和数据库')
  }
  editor.createTab(
    detail.connectionAvailable ? detail.dataSourceId : null,
    detail.connectionAvailable ? detail.database : null,
    detail.statement,
    `History ${detail.id.slice(0, 6)}`,
  )
  side.value = 'database'
  sideCollapsed.value = false
  void syncUrl()
}
async function openScript(id: string) {
  try {
    const detail = await getScript(id)
    const existing = editor.findByScriptId(detail.id)
    if (existing) {
      editor.setActive(existing.id)
    } else {
      const tab = editor.createTab(
        detail.connectionAvailable ? detail.dataSourceId : null,
        detail.connectionAvailable ? detail.database : null,
        detail.statement,
        detail.name,
      )
      editor.markSaved(tab.id, {
        id: detail.id,
        name: detail.name,
        version: detail.version,
        statement: detail.statement,
        dataSourceId: detail.connectionAvailable ? detail.dataSourceId : null,
        database: detail.connectionAvailable ? detail.database : null,
      })
    }
    if (detail.connectionAvailable) {
      selectedSource.value = detail.dataSourceId
      selectedDatabase.value = detail.database
    } else {
      selectedSource.value = null
      selectedDatabase.value = null
      notice('原连接已不可用，请在左侧导航重新选择数据源和数据库')
    }
    void syncUrl()
  } catch (e) {
    notice(safeErrorMessage(e, '打开脚本失败'))
  }
}
function writeBody(
  name: string,
  statement: string,
  dataSourceId: string | null,
  database: string | null,
  version?: number,
) {
  return {
    name,
    statement,
    dataSourceId: dataSourceId || null,
    database: database || null,
    ...(version != null ? { version } : {}),
  }
}
function handleSaveConflict(e: unknown): boolean {
  if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
    notice('脚本已被更新，请重新打开后再保存，或使用另存为')
    return true
  }
  return false
}
function requestSave(asNew = false) {
  const tab = active.value
  if (!tab || tab.kind !== 'sql' || !canScripts.value) return
  const sql = monacoRef.value?.getCopilotSql() || tab.sql
  if (!sql.trim()) {
    notice('请先编写 SQL 再保存')
    return
  }
  if (tab.scriptId && !asNew) {
    void persistTab(tab.id, tab.title, false)
    return
  }
  saveAsMode.value = asNew || Boolean(tab.scriptId)
  saveName.value = tab.title
  saveOpen.value = true
}
async function persistTab(tabId: string, name: string, asNew: boolean) {
  const tab = editor.tabs.find((item) => item.id === tabId)
  if (!tab || tab.kind !== 'sql') return
  const statement = monacoRef.value?.getCopilotSql() || tab.sql
  if (!statement.trim()) {
    notice('请先编写 SQL 再保存')
    return
  }
  saveBusy.value = true
  try {
    const detail =
      tab.scriptId && !asNew
        ? await updateScript(
            tab.scriptId,
            writeBody(
              name,
              statement,
              tab.dataSourceId,
              tab.database,
              tab.savedVersion ?? undefined,
            ),
          )
        : await createScript(writeBody(name, statement, tab.dataSourceId, tab.database))
    editor.markSaved(tab.id, {
      id: detail.id,
      name: detail.name,
      version: detail.version,
      statement: detail.statement,
      dataSourceId: detail.dataSourceId,
      database: detail.database,
    })
    saveOpen.value = false
    void scriptPanelRef.value?.reload()
    notice('脚本已保存')
  } catch (e) {
    if (!handleSaveConflict(e)) notice(safeErrorMessage(e, '保存脚本失败'))
  } finally {
    saveBusy.value = false
  }
}
async function confirmSave() {
  const name = saveName.value.trim()
  if (!name || !active.value) return
  await persistTab(active.value.id, name, saveAsMode.value)
}
function startRenameFromList(item: ScriptSummary) {
  renameTarget.value = {
    scriptId: item.id,
    name: item.name,
    version: item.version,
    statement: item.statementSummary,
    dataSourceId: item.dataSourceId,
    database: item.database,
  }
  renameValue.value = item.name
  renameOpen.value = true
}
function startTitleEdit(tabId: string) {
  const tab = editor.tabs.find((item) => item.id === tabId)
  if (!tab || tab.kind !== 'sql') return
  editingTitleId.value = tabId
  titleDraft.value = tab.title
}
async function commitTitleEdit() {
  const tabId = editingTitleId.value
  editingTitleId.value = null
  const tab = tabId ? editor.tabs.find((item) => item.id === tabId) : null
  const name = titleDraft.value.trim()
  if (!tab || tab.kind !== 'sql' || !name || name === tab.title) return
  if (!tab.scriptId) {
    editor.renameLocal(tab.id, name)
    return
  }
  try {
    const detail = await updateScript(
      tab.scriptId,
      writeBody(
        name,
        tab.savedSql || tab.sql,
        tab.savedDataSourceId,
        tab.savedDatabase,
        tab.savedVersion ?? undefined,
      ),
    )
    editor.applyRename(tab.scriptId, detail.name, detail.version)
    void scriptPanelRef.value?.reload()
  } catch (e) {
    if (!handleSaveConflict(e)) notice(safeErrorMessage(e, '重命名失败'))
  }
}
async function confirmRename() {
  const target = renameTarget.value
  const name = renameValue.value.trim()
  if (!target || !name) return
  try {
    const current = await getScript(target.scriptId)
    const detail = await updateScript(
      target.scriptId,
      writeBody(name, current.statement, current.dataSourceId, current.database, current.version),
    )
    editor.applyRename(target.scriptId, detail.name, detail.version)
    renameOpen.value = false
    renameTarget.value = null
    void scriptPanelRef.value?.reload()
  } catch (e) {
    if (!handleSaveConflict(e)) notice(safeErrorMessage(e, '重命名失败'))
  }
}
function onScriptDeleted(id: string) {
  editor.unbindScript(id)
}
function notice(message: string) {
  notifications.push('info', message)
}
function onCopilotShortcut(event: KeyboardEvent): void {
  if (!(event.metaKey || event.ctrlKey) || event.altKey || event.shiftKey) return
  if (event.key.toLowerCase() !== 'l') return
  event.preventDefault()
  copilot.toggle()
}
async function sendCopilot(
  userText: string,
  extra?: { replaceSql?: string; failedSql?: string; failedError?: string },
): Promise<void> {
  const tab = active.value
  if (!tab || tab.kind !== 'sql' || !tab.dataSourceId || !tab.database) return
  await copilot.send({
    userText,
    message: buildCopilotMessage({
      userText,
      dialect: dialectLabel.value,
      dataSourceName: currentSource.value?.name || '',
      database: tab.database,
      currentSql: monacoRef.value?.getCopilotSql() || tab.sql,
      ...(extra?.failedSql ? { failedSql: extra.failedSql } : {}),
      ...(extra?.failedError ? { failedError: extra.failedError } : {}),
    }),
    datasourceId: tab.dataSourceId,
    database: tab.database,
    ...(extra?.replaceSql ? { replaceSql: extra.replaceSql } : {}),
  })
}
function applyCopilotSql(sql: string, replaceSql?: string): void {
  const tab = active.value
  if (!tab || tab.kind !== 'sql') return
  if (replaceSql) {
    const replaced = monacoRef.value
      ? monacoRef.value.replaceSql(replaceSql, sql)
      : (() => {
          const result = replaceSqlOnce(tab.sql, replaceSql, sql)
          editor.updateSql(tab.id, result.text)
          return result.replaced
        })()
    if (!replaced) notice('未找到原失败语句，已追加到编辑器末尾')
  } else if (monacoRef.value) {
    monacoRef.value.appendSql(sql)
  } else {
    editor.updateSql(tab.id, appendSqlText(tab.sql, sql))
  }
  monacoRef.value?.focus()
}
async function insertAndRunCopilot(sql: string, replaceSql?: string): Promise<void> {
  applyCopilotSql(sql, replaceSql)
  await nextTick()
  await executeScriptText(sql)
}
async function fixWithAi(): Promise<void> {
  const tab = active.value
  if (!tab || tab.kind !== 'sql' || copilotFixDisabled.value) return
  copilot.show()
  const failed = tab.statements.find((item) => item.status === 'FAILED')
  const failedSql = failed?.sql || tab.sql
  const failedError = failed?.error || tab.error || ''
  await sendCopilot(COPILOT_FIX_PROMPT, {
    replaceSql: failedSql,
    failedSql,
    failedError,
  })
}
function syncSplitWidth(): void {
  const width = splitHost.value?.clientWidth
  if (width) splitWidth.value = width
}
function applyFittedSidebar(list: { name: string; host: string; port: number }[]): void {
  if (userSized.value) return
  syncSplitWidth()
  const next = fitSidebarWidth(list)
  if (next === sideWidthPx.value) return
  sideWidthPx.value = next
  sideFitKey.value = 'fit'
}
function onSideResized(event: { panes?: { size: number }[] }): void {
  if (sideCollapsed.value || (event.panes?.length ?? 0) < 2) return
  syncSplitWidth()
  const size = event.panes?.[0]?.size
  if (size == null || splitWidth.value <= 0) return
  userSized.value = true
  sideWidthPx.value = Math.round((size / 100) * splitWidth.value)
}
function tabConnectionLabel(tab: { database: string | null; dataSourceId: string | null }): string {
  const source = sources.value.find((item) => item.id === tab.dataSourceId)
  if (source?.name && tab.database) return `${source.name} / ${tab.database}`
  if (source?.name) return source.name
  if (tab.database) return tab.database
  return '未选择连接'
}

watch(
  () => copilot.open,
  (open) => {
    if (open) void copilot.loadList()
  },
)
watch(
  () => editor.activeId,
  () => {
    const tab = editor.active
    if (!tab) return
    if (tab.dataSourceId === selectedSource.value && tab.database === selectedDatabase.value) return
    selectedSource.value = tab.dataSourceId
    selectedDatabase.value = tab.database
    void syncUrl()
  },
)
watch(
  () => [editor.activeId, editor.active?.kind, editor.active?.viewerPane] as const,
  () => {
    const tab = editor.active
    if (tab?.kind === 'table' && tab.viewerPane === 'data') void loadTableData(tab.id)
  },
)

onMounted(async () => {
  syncSplitWidth()
  window.addEventListener('keydown', onCopilotShortcut, true)
  try {
    sources.value = (await listDataSources({ keyword: '', pageSize: 100 })).items
    applyFittedSidebar(sources.value)
    const requested = typeof route.query.dataSourceId === 'string' ? route.query.dataSourceId : null
    const requestedDb = typeof route.query.database === 'string' ? route.query.database : null
    const sourceId = sources.value.some((item) => item.id === requested) ? requested : null
    const source = sources.value.find((item) => item.id === sourceId)
    const database = sourceId ? (requestedDb ?? source?.defaultDatabase ?? null) : null
    if (editor.tabs.length) {
      if (requested || requestedDb) {
        selectedSource.value = sourceId
        selectedDatabase.value = database
        editor.activateConnection(sourceId, database)
      } else {
        selectedSource.value = editor.active?.dataSourceId ?? sourceId
        selectedDatabase.value = editor.active?.database ?? database
      }
    } else {
      selectedSource.value = sourceId
      selectedDatabase.value = database
    }
    if (typeof route.params.historyId === 'string')
      openHistory(await getHistory(route.params.historyId))
    await syncUrl()
  } catch (e) {
    notice(safeErrorMessage(e, '编辑器初始化失败'))
  }
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onCopilotShortcut, true)
  copilot.cancel()
  exportAbort?.abort()
  for (const tab of editor.tabs) if (tab.running) editor.abort(tab.id)
})
</script>
<template>
  <div class="sql-workspace flex bg-canvas">
    <aside class="activity-rail" aria-label="工作台面板">
      <button
        class="activity-btn"
        :class="{ 'activity-btn-active': side === 'database' && !sideCollapsed }"
        title="数据库"
        aria-label="数据库"
        @click="toggleSide('database')"
      >
        <Database :size="18" />
      </button>
      <button
        v-if="canHistory"
        class="activity-btn"
        :class="{ 'activity-btn-active': side === 'history' && !sideCollapsed }"
        title="执行历史"
        aria-label="执行历史"
        @click="toggleSide('history')"
      >
        <History :size="18" />
      </button>
      <button
        v-if="canScripts"
        class="activity-btn"
        :class="{ 'activity-btn-active': side === 'scripts' && !sideCollapsed }"
        title="脚本"
        aria-label="脚本"
        data-testid="scripts-rail"
        @click="toggleSide('scripts')"
      >
        <Files :size="18" />
      </button>
    </aside>
    <div ref="splitHost" class="min-w-0 flex-1">
      <Splitpanes
        :key="`${sideCollapsed ? 'collapsed' : 'open'}-${copilot.open ? 'copilot' : 'main'}-${sideFitKey}`"
        class="sql-split min-w-0 h-full"
        @resized="onSideResized"
      >
        <Pane
          v-if="!sideCollapsed"
          :size="sideSize"
          :min-size="sideMinSize"
          :max-size="sideMaxSize"
        >
          <ResourceBrowser
            v-if="side === 'database'"
            :sources="sources"
            :engines="engines"
            :data-source-id="selectedSource"
            :database="selectedDatabase"
            :reload-token="resourceNonce"
            @select-connection="selectConnection"
            @insert="insertSql"
            @open-table="openTable"
            @notice="notice"
            @suggestions="metadataSuggestions = $event"
            @refresh="resourceNonce++"
          />
          <HistoryPanel
            v-else-if="side === 'history'"
            :data-source-id="selectedSource"
            @open="openHistory"
            @notice="notice"
          />
          <ScriptPanel
            v-else
            ref="scriptPanelRef"
            @open="openScript"
            @rename="startRenameFromList"
            @deleted="onScriptDeleted"
            @notice="notice"
          />
        </Pane>
        <Pane>
          <section class="flex h-full min-w-0 flex-col bg-panel">
            <WelcomeStart v-if="!editor.tabs.length" @open-sql="newBoundTab" />
            <template v-else>
              <div class="tab-bar" role="tablist" aria-label="编辑器页签">
                <button
                  v-for="tab in editor.tabs"
                  :key="tab.id"
                  class="editor-tab"
                  :class="{ 'editor-tab-active': tab.id === editor.activeId }"
                  role="tab"
                  :aria-selected="tab.id === editor.activeId"
                  :aria-label="`${tab.title}，${tabConnectionLabel(tab)}`"
                  @click="editor.setActive(tab.id)"
                >
                  <Table2 v-if="tab.kind === 'table'" class="editor-tab-icon" :size="14" />
                  <FileCode v-else class="editor-tab-icon" :size="14" />
                  <span class="editor-tab-copy">
                    <input
                      v-if="editingTitleId === tab.id"
                      v-model="titleDraft"
                      class="editor-tab-title-input"
                      aria-label="页签名称"
                      @click.stop
                      @keydown.enter.prevent="commitTitleEdit"
                      @keydown.esc.prevent="editingTitleId = null"
                      @blur="commitTitleEdit"
                    />
                    <span
                      v-else
                      class="editor-tab-title"
                      :class="{ 'editor-tab-dirty': tab.kind === 'sql' && isDirtyTab(tab) }"
                      @dblclick.stop="startTitleEdit(tab.id)"
                      >{{ tab.title }}</span
                    >
                    <span class="editor-tab-connection">{{ tabConnectionLabel(tab) }}</span>
                  </span>
                  <span v-if="tab.running" class="editor-tab-running" title="正在执行" />
                  <X class="tab-close" :size="12" @click.stop="requestClose(tab.id)" />
                </button>
                <button class="tab-add" title="新建页签" aria-label="新建页签" @click="newBoundTab">
                  <Plus :size="15" />
                </button>
              </div>
              <div v-if="active?.kind !== 'table'" class="editor-toolbar">
                <button
                  class="btn min-h-8 px-2.5 py-1 text-xs"
                  title="格式化 SQL"
                  :disabled="!active"
                  @click="formatSql"
                >
                  <WandSparkles :size="14" />格式化
                  <kbd class="shortcut">{{ formatShortcut }}</kbd>
                </button>
                <button
                  v-if="!active?.running"
                  class="btn-primary min-h-8 px-3 py-1 text-xs"
                  :disabled="!canExecute"
                  :title="runTitle"
                  data-testid="run-button"
                  @click="runScript"
                >
                  <Play :size="14" />{{ runLabel }}
                  <kbd class="shortcut shortcut-on-primary">{{ runShortcut }}</kbd>
                </button>
                <button v-else class="btn min-h-8 px-3 py-1 text-xs text-danger" @click="stop">
                  <Square :size="14" />停止
                </button>
                <button
                  v-if="canScripts"
                  class="btn min-h-8 px-2.5 py-1 text-xs"
                  title="保存脚本"
                  data-testid="save-script"
                  :disabled="!active"
                  @click="requestSave(false)"
                >
                  <Save :size="14" />保存
                  <kbd class="shortcut">{{ saveShortcut }}</kbd>
                </button>
                <button
                  v-if="canScripts"
                  class="btn min-h-8 px-2.5 py-1 text-xs"
                  title="另存为新脚本"
                  data-testid="save-script-as"
                  :disabled="!active"
                  @click="requestSave(true)"
                >
                  另存为
                </button>
                <button
                  class="btn ml-auto min-h-8 px-2.5 py-1 text-xs"
                  :class="{ 'activity-btn-active': copilot.open }"
                  :title="`打开 Copilot（${copilotShortcut}）`"
                  data-testid="copilot-toggle"
                  @click="copilot.toggle()"
                >
                  <Sparkles :size="14" />Copilot
                  <kbd class="shortcut">{{ copilotShortcut }}</kbd>
                </button>
              </div>
              <TableViewer
                v-if="
                  active?.kind === 'table' && active.dataSourceId && active.database && active.table
                "
                :key="active.id"
                :data-source-id="active.dataSourceId"
                :database="active.database"
                :table="active.table"
                :table-type="active.tableType"
                :table-comment="active.tableComment"
                :pane="active.viewerPane"
                :result="active.result"
                :error="active.error"
                :running="active.running"
                :can-export="canExport"
                :exporting="exporting"
                :row-limit="rowLimit"
                :reload-token="resourceNonce"
                @update:pane="editor.setViewerPane(active.id, $event)"
                @refresh="loadTableData(active.id, true)"
                @export="requestExport"
                @cancel-export="exportAbort?.abort()"
                @update:row-limit="rowLimit = $event"
              />
              <Splitpanes v-else horizontal class="sql-split min-h-0 flex-1">
                <Pane :size="62" min-size="28">
                  <SqlMonacoEditor
                    v-if="active"
                    :key="active.id"
                    ref="monacoRef"
                    :model-value="active.sql"
                    :language="editorLanguage"
                    :suggestions="suggestions"
                    @update:model-value="editor.updateSql(active!.id, $event)"
                    @update:has-selection="hasSelection = $event"
                    @run="run"
                    @run-script="executeScriptText"
                    @save="requestSave(false)"
                    @notice="notice"
                  />
                </Pane>
                <Pane :size="38" min-size="18">
                  <ScriptResultPanel
                    :statements="active?.statements || []"
                    :result="active?.result || null"
                    :error="active?.error || null"
                    :result-index="active?.resultIndex ?? 0"
                    :running-index="active?.runningIndex ?? null"
                    :running="Boolean(active?.running)"
                    :can-export="canExport"
                    :exporting="exporting"
                    :row-limit="rowLimit"
                    :can-fix-with-ai="true"
                    :fix-disabled="copilotFixDisabled"
                    @select="active && editor.viewResult(active.id, $event)"
                    @update:row-limit="rowLimit = $event"
                    @export="requestExport"
                    @cancel-export="exportAbort?.abort()"
                    @fix-with-ai="fixWithAi"
                  >
                    <template #status>
                      <span class="truncate">
                        {{ currentSource?.name || '未选择数据源' }}
                        <template v-if="selectedDatabase"> / {{ selectedDatabase }}</template>
                        <span class="result-footer-sep">|</span>
                        {{ active?.title || '无页签' }}
                        <span class="result-footer-sep">|</span>
                        MySQL · 脚本按语句顺序执行
                      </span>
                    </template>
                  </ScriptResultPanel>
                </Pane>
              </Splitpanes>
            </template>
          </section>
        </Pane>
        <Pane
          v-if="copilot.open"
          :size="copilotSize"
          :min-size="copilotMinSize"
          :max-size="copilotMaxSize"
        >
          <CopilotPanel
            :available="copilotReady"
            :disabled-reason="copilotDisabledReason"
            :data-source-name="currentSource?.name || ''"
            :data-source-id="active?.dataSourceId || ''"
            :database="selectedDatabase || ''"
            :dialect="dialectLabel"
            :sources="sources"
            :messages="copilotMessages"
            :conversations="copilot.conversations"
            :conversation-id="copilot.conversationId"
            :conversation-datasource-id="copilot.datasourceId"
            :conversation-database="copilot.database"
            :notice="copilot.notice"
            :inflight="copilot.inflight"
            :can-insert-and-run="canExecute"
            @send="sendCopilot"
            @cancel="copilot.cancel()"
            @close="copilot.hide()"
            @insert="applyCopilotSql"
            @insert-and-run="insertAndRunCopilot"
            @new-conversation="copilot.startNew()"
            @open-conversation="openCopilotConversation"
            @delete-conversation="copilot.removeConversation"
          />
        </Pane>
      </Splitpanes>
    </div>
    <ConfirmDialog
      :open="Boolean(pendingCloseId)"
      title="关闭页签"
      confirm-label="关闭"
      @close="pendingCloseId = null"
      @confirm="pendingCloseId && closeTab(pendingCloseId)"
    >
      关闭后将丢弃未保存的修改，确定继续吗？
    </ConfirmDialog>
    <ConfirmDialog
      :open="saveOpen"
      title="保存脚本"
      tone="primary"
      confirm-label="保存"
      :busy="saveBusy"
      :confirm-disabled="!saveName.trim()"
      @close="saveOpen = false"
      @confirm="confirmSave"
    >
      <label class="block text-sm">
        名称
        <input v-model="saveName" class="field mt-1 w-full" data-testid="save-script-name" />
      </label>
    </ConfirmDialog>
    <ConfirmDialog
      :open="renameOpen"
      title="重命名脚本"
      tone="primary"
      confirm-label="重命名"
      :confirm-disabled="!renameValue.trim()"
      @close="renameOpen = false"
      @confirm="confirmRename"
    >
      <label class="block text-sm">
        名称
        <input v-model="renameValue" class="field mt-1 w-full" data-testid="rename-script-name" />
      </label>
    </ConfirmDialog>
    <ConfirmDialog
      :open="exportOpen"
      title="导出 CSV"
      tone="primary"
      confirm-label="继续导出"
      @close="exportOpen = false"
      @confirm="download"
    >
      导出会重新执行当前 SQL，数据可能与屏幕结果不同。
    </ConfirmDialog>
  </div>
</template>
