<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Splitpanes, Pane } from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import {
  ChevronRight,
  Database,
  Download,
  History,
  Play,
  Plus,
  Square,
  WandSparkles,
  X,
} from 'lucide-vue-next'
import { listDataSources } from '@/api/data-sources'
import { cancelExecution, executeSql, exportExecution } from '@/api/executions'
import { getHistory } from '@/api/history'
import { listDatabases } from '@/api/metadata'
import { safeErrorMessage } from '@/api/api-error'
import ResourceBrowser from '@/components/resource-tree/ResourceBrowser.vue'
import HistoryPanel from '@/components/history/HistoryPanel.vue'
import ResultGrid from '@/components/result-grid/ResultGrid.vue'
import SqlMonacoEditor from '@/components/editor/SqlMonacoEditor.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { likelyNeedsDatabase } from '@/sql-editor/sql'
import { useEditorStore } from '@/stores/editor'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import { queryClient, queryKeys } from '@/query/client'
import type { DataSourceListItem, DatabaseItem, HistoryDetail } from '@/types/contracts'

const route = useRoute()
const router = useRouter()
const editor = useEditorStore()
const auth = useAuthStore()
const notifications = useNotificationsStore()
const monacoRef = ref<{
  getRunnableStatement: () => string
  formatSql: () => void
  insertAtCursor: (sql: string) => void
  focus: () => void
} | null>(null)

const sources = ref<DataSourceListItem[]>([])
const databases = ref<DatabaseItem[]>([])
const selectedSource = ref<string | null>(null)
const selectedDatabase = ref<string | null>(null)
const side = ref<'database' | 'history'>('database')
const sideCollapsed = ref(false)
const resourceNonce = ref(0)
const exporting = ref(false)
const exportOpen = ref(false)
const pendingCloseId = ref<string | null>(null)
const metadataSuggestions = ref<string[]>([])
let exportAbort: AbortController | undefined

const canExecute = computed(() => auth.session?.capabilities.includes('SQL_EXECUTE') ?? false)
const canExport = computed(() => auth.session?.capabilities.includes('SQL_EXPORT') ?? false)
const canHistory = computed(() => auth.session?.capabilities.includes('HISTORY_READ') ?? false)
const active = computed(() => editor.active)
const suggestions = computed(() =>
  Array.from(new Set([...metadataSuggestions.value, ...sources.value.map((item) => item.name)])),
)
const currentSource = computed(
  () => sources.value.find((item) => item.id === selectedSource.value) || null,
)
const runShortcut = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘↵' : 'Ctrl+Enter'
const formatShortcut = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌥⇧F' : 'Shift+Alt+F'

async function syncUrl() {
  const query: { dataSourceId?: string; database?: string } = {}
  if (selectedSource.value) query.dataSourceId = selectedSource.value
  if (selectedDatabase.value) query.database = selectedDatabase.value
  await router.replace({ path: '/sql-editor', query })
}
async function loadDatabases(): Promise<void> {
  if (!selectedSource.value) {
    databases.value = []
    return
  }
  try {
    databases.value = (await listDatabases(selectedSource.value)).items
  } catch (e) {
    databases.value = []
    notice(safeErrorMessage(e, '数据库加载失败'))
  }
}
function applyConnection(sourceId: string | null, database: string | null): void {
  selectedSource.value = sourceId
  selectedDatabase.value = database
  editor.activateConnection(sourceId, database)
  void syncUrl()
}
function newBoundTab() {
  editor.createTab(selectedSource.value, selectedDatabase.value)
  void nextTick(() => monacoRef.value?.focus())
}
async function selectSource(value: string) {
  const sourceId = value || null
  if (sourceId === selectedSource.value) return
  const source = sources.value.find((item) => item.id === sourceId)
  applyConnection(sourceId, source?.defaultDatabase || null)
}
async function selectDatabase(value: string) {
  const database = value || null
  if (database === selectedDatabase.value) return
  applyConnection(selectedSource.value, database)
}
function toggleSide(next: 'database' | 'history') {
  if (side.value === next && !sideCollapsed.value) {
    sideCollapsed.value = true
    return
  }
  side.value = next
  sideCollapsed.value = false
}
function selectConnection(sourceId: string, database: string) {
  applyConnection(sourceId, database)
}
async function insertSql(sql: string, dataSourceId: string, database: string) {
  applyConnection(dataSourceId, database)
  await nextTick()
  const tab = editor.active
  if (!tab) return
  if (monacoRef.value) monacoRef.value.insertAtCursor(sql)
  else editor.updateSql(tab.id, `${tab.sql.trim() ? `${tab.sql}\n` : ''}${sql}`)
}
function runCurrent() {
  void run(monacoRef.value?.getRunnableStatement() || active.value?.sql || '')
}
function formatSql() {
  monacoRef.value?.formatSql()
}
async function run(statement: string) {
  const tab = active.value
  if (!tab || !canExecute.value) return
  const sql = statement.trim()
  if (!sql) {
    notice('请输入要执行的 SQL，或将光标放在目标语句上')
    return
  }
  if (!tab.dataSourceId) {
    notice('请选择数据源')
    return
  }
  if (!tab.database && likelyNeedsDatabase(sql)) {
    notice('请选择数据库')
    return
  }
  const executionId = crypto.randomUUID()
  let controller: AbortController
  try {
    controller = editor.start(tab.id, executionId)
  } catch (e) {
    notice(e instanceof Error ? e.message : '无法执行')
    return
  }
  try {
    const result = await executeSql(
      {
        executionId,
        dataSourceId: tab.dataSourceId,
        database: tab.database,
        statement: sql,
        rowLimit: 1000,
      },
      controller.signal,
    )
    editor.finish(tab.id, result)
    if (result.kind === 'DDL') {
      resourceNonce.value++
      await queryClient.invalidateQueries({ queryKey: queryKeys.metadata(tab.dataSourceId) })
      await loadDatabases()
    }
    await queryClient.invalidateQueries({ queryKey: ['sql-history'] })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      editor.finish(tab.id, undefined, '执行已取消')
    } else editor.finish(tab.id, undefined, safeErrorMessage(e, 'SQL 执行失败'))
    await queryClient.invalidateQueries({ queryKey: ['sql-history'] })
  }
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
  if (tab.sql.trim()) {
    pendingCloseId.value = id
    return
  }
  closeTab(id)
}
function closeTab(id: string) {
  pendingCloseId.value = null
  editor.closeTab(id)
  if (!editor.tabs.length) newBoundTab()
}
function openHistory(detail: HistoryDetail) {
  if (detail.connectionAvailable) {
    selectedSource.value = detail.dataSourceId
    selectedDatabase.value = detail.database
  } else {
    selectedSource.value = null
    selectedDatabase.value = null
    notice('原连接已不可用，请重新选择数据源和数据库')
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
function notice(message: string) {
  notifications.push('info', message)
}
function tabLabel(tab: { database: string | null; dataSourceId: string | null }): string {
  if (tab.database) return tab.database
  const source = sources.value.find((item) => item.id === tab.dataSourceId)
  return source?.name || '未选择连接'
}

watch(selectedSource, () => {
  void loadDatabases()
})
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

onMounted(async () => {
  try {
    sources.value = (await listDataSources({ keyword: '', pageSize: 100 })).items
    const requested = typeof route.query.dataSourceId === 'string' ? route.query.dataSourceId : null
    const sourceId = sources.value.some((item) => item.id === requested)
      ? requested
      : sources.value[0]?.id || null
    const source = sources.value.find((item) => item.id === sourceId)
    const requestedDb = typeof route.query.database === 'string' ? route.query.database : null
    const database = requestedDb ?? source?.defaultDatabase ?? null
    selectedSource.value = sourceId
    selectedDatabase.value = database
    await loadDatabases()
    if (!editor.tabs.length) editor.createTab(sourceId, database)
    else if (requested || requestedDb) editor.activateConnection(sourceId, database)
    else {
      selectedSource.value = editor.active?.dataSourceId ?? sourceId
      selectedDatabase.value = editor.active?.database ?? database
    }
    if (typeof route.params.historyId === 'string')
      openHistory(await getHistory(route.params.historyId))
    await syncUrl()
  } catch (e) {
    notice(safeErrorMessage(e, '编辑器初始化失败'))
  }
})
onBeforeUnmount(() => {
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
    </aside>
    <Splitpanes :key="sideCollapsed ? 'collapsed' : 'open'" class="sql-split min-w-0 flex-1">
      <Pane v-if="!sideCollapsed" :size="22" min-size="16" max-size="38">
        <ResourceBrowser
          v-if="side === 'database'"
          :sources="sources"
          :data-source-id="selectedSource"
          :database="selectedDatabase"
          :reload-token="resourceNonce"
          @select-connection="selectConnection"
          @insert="insertSql"
          @notice="notice"
          @suggestions="metadataSuggestions = $event"
          @refresh="loadDatabases().then(() => resourceNonce++)"
        />
        <HistoryPanel
          v-else
          :data-source-id="selectedSource"
          @open="openHistory"
          @notice="notice"
        />
      </Pane>
      <Pane>
        <section class="flex h-full min-w-0 flex-col bg-panel">
          <div class="editor-toolbar">
            <select
              :value="selectedSource || ''"
              class="field h-8 w-52 py-0 text-xs"
              aria-label="数据源"
              @change="selectSource(($event.target as HTMLSelectElement).value)"
            >
              <option value="">选择数据源</option>
              <option v-for="source in sources" :key="source.id" :value="source.id">
                {{ source.name }} · {{ source.host }}
              </option>
            </select>
            <ChevronRight :size="14" class="text-muted" />
            <select
              :value="selectedDatabase || ''"
              class="field h-8 w-44 py-0 text-xs"
              aria-label="数据库"
              :disabled="!selectedSource"
              @change="selectDatabase(($event.target as HTMLSelectElement).value)"
            >
              <option value="">选择数据库</option>
              <option v-for="item in databases" :key="item.name" :value="item.name">
                {{ item.name }}
              </option>
            </select>
            <div class="ml-auto flex items-center gap-2">
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
                :title="`运行当前语句或选区（${runShortcut}）`"
                @click="runCurrent"
              >
                <Play :size="14" />运行
                <kbd class="shortcut shortcut-on-primary">{{ runShortcut }}</kbd>
              </button>
              <button v-else class="btn min-h-8 px-3 py-1 text-xs text-danger" @click="stop">
                <Square :size="14" />停止
              </button>
              <button
                v-if="active?.result?.kind === 'RESULT_SET' && !exporting"
                class="btn min-h-8 px-2.5 py-1 text-xs"
                :disabled="!canExport"
                @click="requestExport"
              >
                <Download :size="14" />导出
              </button>
              <button
                v-else-if="exporting"
                class="btn min-h-8 px-2.5 py-1 text-xs text-danger"
                @click="exportAbort?.abort()"
              >
                取消导出
              </button>
            </div>
          </div>
          <div class="tab-bar">
            <button
              v-for="tab in editor.tabs"
              :key="tab.id"
              class="editor-tab"
              :class="{ 'editor-tab-active': tab.id === editor.activeId }"
              @click="editor.setActive(tab.id)"
            >
              <span class="flex min-w-0 flex-col items-start leading-tight">
                <span class="max-w-32 truncate">{{ tab.title }}</span>
                <span class="max-w-32 truncate text-[10px] text-muted">{{ tabLabel(tab) }}</span>
              </span>
              <span v-if="tab.running" class="h-1.5 w-1.5 rounded-full bg-brand" />
              <X class="tab-close" :size="12" @click.stop="requestClose(tab.id)" />
            </button>
            <button class="tab-add" title="新建页签" aria-label="新建页签" @click="newBoundTab">
              <Plus :size="15" />
            </button>
          </div>
          <Splitpanes horizontal class="sql-split min-h-0 flex-1">
            <Pane :size="62" min-size="28">
              <SqlMonacoEditor
                v-if="active"
                :key="active.id"
                ref="monacoRef"
                :model-value="active.sql"
                :suggestions="suggestions"
                @update:model-value="editor.updateSql(active!.id, $event)"
                @run="run"
                @run-all="run"
                @notice="notice"
              />
              <div v-else class="grid h-full place-items-center text-sm text-muted">
                新建 SQL 页签开始编辑
              </div>
            </Pane>
            <Pane :size="38" min-size="18">
              <ResultGrid
                :result="active?.result || null"
                :error="active?.error || null"
                :running="Boolean(active?.running)"
              />
            </Pane>
          </Splitpanes>
          <div class="status-bar">
            <span>{{ currentSource?.name || '未选择数据源' }}</span>
            <span v-if="selectedDatabase">/ {{ selectedDatabase }}</span>
            <span class="mx-2 text-line">|</span>
            <span>{{ active?.title || '无页签' }}</span>
            <span class="ml-auto text-[11px] text-muted">MySQL · 每次运行一条语句</span>
          </div>
        </section>
      </Pane>
    </Splitpanes>
    <ConfirmDialog
      :open="Boolean(pendingCloseId)"
      title="关闭页签"
      confirm-label="关闭"
      @close="pendingCloseId = null"
      @confirm="pendingCloseId && closeTab(pendingCloseId)"
    >
      关闭后将丢弃此页签中的 SQL，确定继续吗？
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
