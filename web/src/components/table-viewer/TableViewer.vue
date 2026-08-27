<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Copy, RefreshCw } from 'lucide-vue-next'
import { getTableDetail } from '@/api/metadata'
import { safeErrorMessage } from '@/api/api-error'
import ResultGrid from '@/components/result-grid/ResultGrid.vue'
import LoadingState from '@/components/LoadingState.vue'
import ErrorState from '@/components/ErrorState.vue'
import type { DatabaseObjectType, SqlExecutionResult, TableDetail } from '@/types/contracts'
import type { TableDataSort } from '@/sql-editor/table-data-filter'
import type { TableViewerPane } from '@/stores/editor'

type PropertiesSection = 'info' | 'columns' | 'keys' | 'indexes' | 'ddl'

const props = defineProps<{
  dataSourceId: string
  database: string
  table: string
  tableType: DatabaseObjectType | null
  tableComment: string | null
  pane: TableViewerPane
  result: SqlExecutionResult | null
  error: string | null
  running: boolean
  canExport: boolean
  exporting: boolean
  rowLimit: number
  reloadToken?: number
  filterDrafts?: Record<string, string>
  filterErrors?: Record<string, string>
  sortState?: TableDataSort | null
}>()
const emit = defineEmits<{
  'update:pane': [pane: TableViewerPane]
  refresh: []
  export: []
  'cancel-export': []
  'update:rowLimit': [value: number]
  'update:filterDrafts': [Record<string, string>]
  'apply-filters': []
  'update:sortState': [TableDataSort | null]
}>()

const sections: { id: PropertiesSection; label: string }[] = [
  { id: 'info', label: '信息' },
  { id: 'columns', label: '字段' },
  { id: 'keys', label: '主键' },
  { id: 'indexes', label: '索引' },
  { id: 'ddl', label: 'DDL' },
]
const section = ref<PropertiesSection>('info')
const detail = ref<TableDetail | null>(null)
const detailError = ref('')
const detailLoading = ref(false)
const copied = ref(false)
let copyTimer = 0

async function loadDetail(force = false): Promise<void> {
  if (!force && detail.value && !detailError.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await getTableDetail(props.dataSourceId, props.database, props.table)
  } catch (e) {
    detail.value = null
    detailError.value = safeErrorMessage(e, '表结构加载失败')
  } finally {
    detailLoading.value = false
  }
}
function refresh(): void {
  if (props.pane === 'properties') void loadDetail(true)
  else emit('refresh')
}
async function copyDdl(): Promise<void> {
  if (!detail.value?.ddl) return
  await navigator.clipboard.writeText(detail.value.ddl)
  copied.value = true
  window.clearTimeout(copyTimer)
  copyTimer = window.setTimeout(() => {
    copied.value = false
  }, 1200)
}

watch(
  () => props.pane,
  (pane) => {
    if (pane === 'properties') void loadDetail()
  },
)
watch(
  () => [props.dataSourceId, props.database, props.table, props.reloadToken] as const,
  () => {
    detail.value = null
    detailError.value = ''
    if (props.pane === 'properties') void loadDetail(true)
  },
)
onMounted(() => {
  if (props.pane === 'properties') void loadDetail()
})
onBeforeUnmount(() => window.clearTimeout(copyTimer))
</script>
<template>
  <section class="table-viewer" data-testid="table-viewer">
    <div class="object-tab-bar" role="tablist" aria-label="表对象页签">
      <button
        class="object-tab"
        :class="{ 'object-tab-active': pane === 'properties' }"
        role="tab"
        data-testid="table-viewer-tab-properties"
        :aria-selected="pane === 'properties'"
        @click="emit('update:pane', 'properties')"
      >
        Properties
      </button>
      <button
        class="object-tab"
        :class="{ 'object-tab-active': pane === 'data' }"
        role="tab"
        data-testid="table-viewer-tab-data"
        :aria-selected="pane === 'data'"
        @click="emit('update:pane', 'data')"
      >
        Data
      </button>
      <button class="ml-auto icon-btn" title="刷新" aria-label="刷新表对象" @click="refresh">
        <RefreshCw :size="14" />
      </button>
    </div>
    <div v-if="pane === 'data'" class="min-h-0 flex-1">
      <ResultGrid
        :result="result"
        :error="error"
        :running="running"
        :can-export="canExport"
        :exporting="exporting"
        :row-limit="rowLimit"
        header-filters
        :filter-drafts="filterDrafts ?? {}"
        :filter-errors="filterErrors ?? {}"
        :sort-state="sortState ?? null"
        @update:row-limit="emit('update:rowLimit', $event)"
        @export="emit('export')"
        @cancel-export="emit('cancel-export')"
        @update:filter-drafts="emit('update:filterDrafts', $event)"
        @apply-filters="emit('apply-filters')"
        @update:sort-state="emit('update:sortState', $event)"
      >
        <template #status>
          <span class="truncate">
            {{ database }}.{{ table }}
            <span class="result-footer-sep">|</span>
            {{ tableType === 'VIEW' ? '视图' : '表' }}
          </span>
        </template>
      </ResultGrid>
    </div>
    <div v-else class="table-properties" data-testid="table-properties">
      <nav class="properties-nav" aria-label="表属性">
        <button
          v-for="item in sections"
          :key="item.id"
          class="properties-nav-item"
          :class="{ 'properties-nav-item-active': section === item.id }"
          :data-testid="`table-properties-nav-${item.id}`"
          @click="section = item.id"
        >
          {{ item.label }}
        </button>
      </nav>
      <div class="min-h-0 flex-1 overflow-auto text-sm" :class="section === 'ddl' ? '' : 'p-4'">
        <LoadingState v-if="detailLoading" label="正在加载表结构…" />
        <ErrorState
          v-else-if="detailError"
          :message="detailError"
          retryable
          @retry="loadDetail(true)"
        />
        <template v-else-if="detail">
          <table v-if="section === 'info'" class="properties-table">
            <thead>
              <tr>
                <th>属性</th>
                <th>值</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>名称</td>
                <td>{{ detail.table }}</td>
              </tr>
              <tr>
                <td>数据库</td>
                <td>{{ detail.database }}</td>
              </tr>
              <tr>
                <td>类型</td>
                <td>{{ tableType === 'VIEW' ? '视图' : '表' }}</td>
              </tr>
              <tr>
                <td>注释</td>
                <td>{{ tableComment || '' }}</td>
              </tr>
              <tr>
                <td>字段数</td>
                <td>{{ detail.columns.length }}</td>
              </tr>
            </tbody>
          </table>
          <table v-else-if="section === 'columns'" class="properties-table">
            <thead>
              <tr>
                <th>#</th>
                <th>字段</th>
                <th>类型</th>
                <th>可空</th>
                <th>默认值</th>
                <th>额外</th>
                <th>注释</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="column in detail.columns" :key="column.name">
                <td>{{ column.ordinal }}</td>
                <td>
                  <b>{{ column.name }}</b>
                  <span v-if="column.primaryKey" class="ml-1 text-amber-500">PK</span>
                </td>
                <td>{{ column.typeName }}</td>
                <td>{{ column.nullable ? 'YES' : 'NO' }}</td>
                <td>{{ column.defaultValue ?? '' }}</td>
                <td>{{ column.extra || '' }}</td>
                <td>{{ column.comment || '' }}</td>
              </tr>
            </tbody>
          </table>
          <table v-else-if="section === 'keys'" class="properties-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>类型</th>
                <th>字段</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="detail.primaryKey">
                <td>{{ detail.primaryKey.name || 'PRIMARY' }}</td>
                <td>PRIMARY KEY</td>
                <td>{{ detail.primaryKey.columns.join(', ') }}</td>
              </tr>
              <tr v-else>
                <td colspan="3" class="text-muted">无主键</td>
              </tr>
            </tbody>
          </table>
          <table v-else-if="section === 'indexes'" class="properties-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>类型</th>
                <th>唯一</th>
                <th>字段</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!detail.indexes.length">
                <td colspan="4" class="text-muted">无索引</td>
              </tr>
              <tr v-for="index in detail.indexes" :key="index.name">
                <td>{{ index.name }}</td>
                <td>{{ index.type }}</td>
                <td>{{ index.unique ? 'YES' : 'NO' }}</td>
                <td>{{ index.columns.join(', ') }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="properties-ddl-pane" data-testid="table-properties-ddl">
            <div class="properties-ddl-toolbar">
              <button
                class="btn min-h-8 px-2.5 py-1 text-xs"
                :disabled="!detail.ddl"
                @click="copyDdl"
              >
                <Copy :size="13" />{{ copied ? '已复制' : '复制 DDL' }}
              </button>
            </div>
            <pre class="properties-ddl">{{ detail.ddl || '无法读取该表的 DDL' }}</pre>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>
