<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Splitpanes, Pane } from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import { Database, Download, History, Play, Plus, Square, X } from 'lucide-vue-next'
import { listDataSources } from '@/api/data-sources'
import { cancelExecution, executeSql, exportExecution } from '@/api/executions'
import { getHistory } from '@/api/history'
import { safeErrorMessage } from '@/api/api-error'
import ResourceBrowser from '@/components/resource-tree/ResourceBrowser.vue'
import HistoryPanel from '@/components/history/HistoryPanel.vue'
import ResultGrid from '@/components/result-grid/ResultGrid.vue'
import SqlMonacoEditor from '@/components/editor/SqlMonacoEditor.vue'
import { likelyNeedsDatabase } from '@/sql-editor/sql'
import { useEditorStore } from '@/stores/editor'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import { queryClient, queryKeys } from '@/query/client'
import type { DataSourceListItem, HistoryDetail } from '@/types/contracts'

const route = useRoute(),
  router = useRouter(),
  editor = useEditorStore(),
  auth = useAuthStore(),
  notifications = useNotificationsStore()
const sources = ref<DataSourceListItem[]>([]),
  selectedSource = ref<string | null>(null),
  selectedDatabase = ref<string | null>(null),
  side = ref<'database' | 'history'>('database'),
  resourceNonce = ref(0),
  exporting = ref(false),
  metadataSuggestions = ref<string[]>([])
let exportAbort: AbortController | undefined
const canExecute = computed(() => auth.session?.capabilities.includes('SQL_EXECUTE') ?? false),
  canExport = computed(() => auth.session?.capabilities.includes('SQL_EXPORT') ?? false),
  canHistory = computed(() => auth.session?.capabilities.includes('HISTORY_READ') ?? false)
const active = computed(() => editor.active)
const suggestions = computed(() =>
  Array.from(new Set([...metadataSuggestions.value, ...sources.value.map((item) => item.name)])),
)
async function syncUrl() {
  const query: { dataSourceId?: string; database?: string } = {}
  if (selectedSource.value) query.dataSourceId = selectedSource.value
  if (selectedDatabase.value) query.database = selectedDatabase.value
  await router.replace({ path: '/sql-editor', query })
}
function newBoundTab() {
  editor.createTab(selectedSource.value, selectedDatabase.value)
}
async function selectSource(value: string) {
  if (value === selectedSource.value) return
  selectedSource.value = value
  selectedDatabase.value = null
  resourceNonce.value++
  newBoundTab()
  await syncUrl()
}
async function selectDatabase(value: string) {
  if (value === selectedDatabase.value) return
  selectedDatabase.value = value
  newBoundTab()
  await syncUrl()
}
function insertSql(sql: string) {
  const tab = editor.ensureTab(selectedSource.value, selectedDatabase.value)
  const separator = tab.sql.trim() ? '\n' : ''
  editor.updateSql(tab.id, `${tab.sql}${separator}${sql}`)
}
async function run(statement: string) {
  const tab = active.value
  if (!tab || !canExecute.value) return
  const sql = statement.trim()
  if (!sql) {
    notice('请输入要执行的 SQL')
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
async function download() {
  const tab = active.value
  if (!tab || tab.result?.kind !== 'RESULT_SET' || !canExport.value) return
  if (!window.confirm('导出会重新执行当前 SQL，数据可能与屏幕结果不同。继续吗？')) return
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
function closeTab(id: string) {
  const tab = editor.tabs.find((item) => item.id === id)
  if (!tab) return
  if (tab.sql.trim() && !window.confirm('关闭后将丢弃此页签 SQL，确定继续吗？')) return
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
  }
  editor.createTab(
    detail.connectionAvailable ? detail.dataSourceId : null,
    detail.connectionAvailable ? detail.database : null,
    detail.statement,
    `History ${detail.id.slice(0, 6)}`,
  )
  void syncUrl()
}
function notice(message: string) {
  notifications.push('info', message)
}
onMounted(async () => {
  try {
    sources.value = (await listDataSources({ keyword: '', pageSize: 100 })).items
    const requested = typeof route.query.dataSourceId === 'string' ? route.query.dataSourceId : null
    selectedSource.value = sources.value.some((item) => item.id === requested)
      ? requested
      : sources.value[0]?.id || null
    selectedDatabase.value = typeof route.query.database === 'string' ? route.query.database : null
    editor.ensureTab(selectedSource.value, selectedDatabase.value)
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
  <div class="sql-workspace flex bg-white">
    <aside
      class="flex w-12 shrink-0 flex-col items-center border-r border-line bg-slate-950 py-2 text-slate-300"
    >
      <button
        class="activity-btn"
        :class="{ 'bg-slate-700 text-white': side === 'database' }"
        title="数据库"
        @click="side = 'database'"
      >
        <Database :size="19" /></button
      ><button
        v-if="canHistory"
        class="activity-btn"
        :class="{ 'bg-slate-700 text-white': side === 'history' }"
        title="执行历史"
        @click="side = 'history'"
      >
        <History :size="19" />
      </button>
    </aside>
    <Splitpanes class="default-theme min-w-0 flex-1"
      ><Pane :size="22" min-size="16" max-size="38"
        ><ResourceBrowser
          v-if="side === 'database'"
          :key="resourceNonce"
          :data-source-id="selectedSource"
          :database="selectedDatabase"
          @select-database="selectDatabase"
          @insert="insertSql"
          @notice="notice"
          @suggestions="metadataSuggestions = $event" /><HistoryPanel
          v-else
          :data-source-id="selectedSource"
          @open="openHistory"
          @notice="notice" /></Pane
      ><Pane
        ><section class="flex h-full min-w-0 flex-col">
          <div class="flex h-12 shrink-0 items-center gap-2 border-b border-line px-3">
            <select
              :value="selectedSource || ''"
              class="field w-52 py-1.5 text-xs"
              aria-label="数据源"
              @change="selectSource(($event.target as HTMLSelectElement).value)"
            >
              <option value="">选择数据源</option>
              <option v-for="source in sources" :key="source.id" :value="source.id">
                {{ source.name }} · {{ source.host }}
              </option></select
            ><span class="max-w-44 truncate rounded bg-slate-100 px-3 py-2 text-xs">{{
              selectedDatabase || '未选择数据库'
            }}</span>
            <div class="ml-auto flex gap-2">
              <button
                v-if="!active?.running"
                class="btn-primary min-h-8 px-3 py-1.5"
                :disabled="!canExecute"
                @click="run(active?.sql || '')"
              >
                <Play :size="14" />运行</button
              ><button v-else class="btn min-h-8 px-3 py-1.5 text-danger" @click="stop">
                <Square :size="14" />停止</button
              ><button
                v-if="active?.result?.kind === 'RESULT_SET'"
                class="btn min-h-8 px-3 py-1.5"
                :disabled="exporting || !canExport"
                @click="download"
              >
                <Download :size="14" />{{ exporting ? '导出中' : '导出' }}
              </button>
            </div>
          </div>
          <div class="flex h-10 shrink-0 items-end border-b border-line bg-slate-50 px-2">
            <button
              v-for="tab in editor.tabs"
              :key="tab.id"
              class="flex h-9 items-center gap-2 border-x border-t px-3 text-xs"
              :class="
                tab.id === editor.activeId
                  ? 'border-line bg-white'
                  : 'border-transparent text-muted'
              "
              @click="editor.setActive(tab.id)"
            >
              <span class="max-w-28 truncate">{{ tab.title }}</span
              ><span v-if="tab.running" class="h-2 w-2 animate-pulse rounded-full bg-brand" /><X
                :size="12"
                @click.stop="closeTab(tab.id)"
              /></button
            ><button
              class="grid h-8 w-8 place-items-center text-muted"
              title="新建页签"
              @click="newBoundTab"
            >
              <Plus :size="16" />
            </button>
          </div>
          <Splitpanes horizontal class="default-theme min-h-0 flex-1"
            ><Pane :size="58" min-size="25"
              ><SqlMonacoEditor
                v-if="active"
                :key="active.id"
                :model-value="active.sql"
                :suggestions="suggestions"
                @update:model-value="editor.updateSql(active!.id, $event)"
                @run="run"
                @run-all="run"
                @notice="notice"
              />
              <div v-else class="grid h-full place-items-center text-muted">
                新建 SQL 页签开始编辑
              </div></Pane
            ><Pane :size="42" min-size="20"
              ><ResultGrid :result="active?.result || null" :error="active?.error || null" /></Pane
          ></Splitpanes></section></Pane
    ></Splitpanes>
  </div>
</template>
