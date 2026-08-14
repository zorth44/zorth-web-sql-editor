<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ChevronRight,
  Columns3,
  Copy,
  Database,
  Eye,
  KeyRound,
  RefreshCw,
  Search,
  Table2,
} from 'lucide-vue-next'
import { getTableDetail, listTables } from '@/api/metadata'
import { quoteIdentifier, selectPreview } from '@/sql-editor/sql'
import { safeErrorMessage } from '@/api/api-error'
import type { DatabaseItem, TableDetail, TableItem } from '@/types/contracts'

const props = defineProps<{
  dataSourceId: string | null
  database: string | null
  databases: DatabaseItem[]
  loadingDatabases?: boolean
  reloadToken?: number
}>()
const emit = defineEmits<{
  'select-database': [value: string]
  insert: [sql: string, database: string]
  notice: [message: string]
  suggestions: [values: string[]]
  refresh: []
}>()

const databaseSearch = ref('')
const tableSearch = ref('')
const expandedDbs = ref<Record<string, boolean>>({})
const expandedTables = ref<Record<string, boolean>>({})
const tablesByDb = ref<Record<string, TableItem[]>>({})
const loadingTables = ref<Record<string, boolean>>({})
const tableError = ref<Record<string, string>>({})
const details = ref<Record<string, TableDetail>>({})
const drawer = ref<TableDetail | null>(null)
const menu = ref<{ x: number; y: number; database: string; table: TableItem } | null>(null)

const visibleDatabases = computed(() =>
  props.databases.filter((item) =>
    item.name.toLowerCase().includes(databaseSearch.value.trim().toLowerCase()),
  ),
)

function tableKey(database: string, table: string): string {
  return `${database}.${table}`
}
function tablesOf(database: string, type: TableItem['type']): TableItem[] {
  const q = tableSearch.value.trim().toLowerCase()
  return (tablesByDb.value[database] || []).filter(
    (item) => item.type === type && (!q || item.name.toLowerCase().includes(q)),
  )
}
function publishSuggestions(): void {
  const names = [
    ...props.databases.map((item) => item.name),
    ...Object.values(tablesByDb.value).flatMap((items) => items.map((item) => item.name)),
    ...Object.values(details.value).flatMap((item) => item.columns.map((column) => column.name)),
  ]
  emit('suggestions', Array.from(new Set(names)))
}
async function loadTables(database: string, force = false): Promise<void> {
  if (!props.dataSourceId) return
  if (!force && tablesByDb.value[database]) return
  loadingTables.value = { ...loadingTables.value, [database]: true }
  tableError.value = { ...tableError.value, [database]: '' }
  try {
    const page = await listTables(props.dataSourceId, database)
    tablesByDb.value = { ...tablesByDb.value, [database]: page.items }
    publishSuggestions()
  } catch (e) {
    tableError.value = { ...tableError.value, [database]: safeErrorMessage(e, '表加载失败') }
  } finally {
    loadingTables.value = { ...loadingTables.value, [database]: false }
  }
}
async function ensureDetail(database: string, table: string): Promise<TableDetail | null> {
  if (!props.dataSourceId) return null
  const key = tableKey(database, table)
  if (details.value[key]) return details.value[key]
  try {
    const detail = await getTableDetail(props.dataSourceId, database, table)
    details.value = { ...details.value, [key]: detail }
    publishSuggestions()
    return detail
  } catch (e) {
    emit('notice', safeErrorMessage(e, '表结构加载失败'))
    return null
  }
}
async function expandDatabase(name: string): Promise<void> {
  expandedDbs.value = { ...expandedDbs.value, [name]: true }
  await loadTables(name)
}
function toggleDatabase(name: string): void {
  if (expandedDbs.value[name]) {
    expandedDbs.value = { ...expandedDbs.value, [name]: false }
    return
  }
  void expandDatabase(name)
}
function selectDatabase(name: string): void {
  void expandDatabase(name)
  emit('select-database', name)
}
async function toggleTable(database: string, table: string): Promise<void> {
  const key = tableKey(database, table)
  if (expandedTables.value[key]) {
    expandedTables.value = { ...expandedTables.value, [key]: false }
    return
  }
  expandedTables.value = { ...expandedTables.value, [key]: true }
  await ensureDetail(database, table)
}
async function openStructure(database: string, table: string): Promise<void> {
  drawer.value = await ensureDetail(database, table)
  menu.value = null
}
async function copyName(name: string): Promise<void> {
  await navigator.clipboard.writeText(name)
  emit('notice', '名称已复制')
  menu.value = null
}
function generateSelect(database: string, table: string): void {
  emit('insert', selectPreview(database, table), database)
  menu.value = null
}
function openMenu(event: MouseEvent, database: string, table: TableItem): void {
  event.preventDefault()
  menu.value = { x: event.clientX, y: event.clientY, database, table }
}
function closeMenu(): void {
  menu.value = null
}
watch(
  () => props.dataSourceId,
  () => {
    expandedDbs.value = {}
    expandedTables.value = {}
    tablesByDb.value = {}
    details.value = {}
    drawer.value = null
    menu.value = null
  },
)
watch(
  () => props.reloadToken,
  async () => {
    if (!props.reloadToken) return
    tablesByDb.value = {}
    details.value = {}
    expandedTables.value = {}
    const open = Object.entries(expandedDbs.value)
      .filter(([, openDb]) => openDb)
      .map(([name]) => name)
    await Promise.all(open.map((name) => loadTables(name, true)))
    if (drawer.value) {
      drawer.value = await ensureDetail(drawer.value.database, drawer.value.table)
    }
  },
)
watch(
  () => [props.database, props.databases] as const,
  async ([database]) => {
    if (database && props.databases.some((item) => item.name === database)) {
      await expandDatabase(database)
    }
    publishSuggestions()
  },
  { immediate: true },
)
onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-white">
    <div class="border-b border-line px-3 py-2.5">
      <div class="flex items-center justify-between">
        <strong class="text-sm">数据库</strong>
        <button class="icon-btn" aria-label="刷新数据库" title="刷新" @click="emit('refresh')">
          <RefreshCw :size="14" />
        </button>
      </div>
      <label class="relative mt-2 block">
        <Search class="absolute left-2.5 top-2 text-muted" :size="14" />
        <input
          v-model="databaseSearch"
          class="field py-1.5 pl-8 text-xs"
          placeholder="搜索数据库"
        />
      </label>
    </div>
    <div class="min-h-0 flex-1 overflow-auto py-1 text-[13px]">
      <p v-if="!dataSourceId" class="px-3 py-6 text-center text-xs text-muted">请先选择数据源</p>
      <p v-else-if="loadingDatabases" class="px-3 py-6 text-center text-xs text-muted">
        正在加载数据库…
      </p>
      <p v-else-if="!visibleDatabases.length" class="px-3 py-6 text-center text-xs text-muted">
        没有匹配的数据库
      </p>
      <div v-for="item in visibleDatabases" :key="item.name">
        <div class="tree-row" :class="{ 'tree-row-active': database === item.name }">
          <button
            class="tree-chevron"
            :aria-label="(expandedDbs[item.name] ? '折叠 ' : '展开 ') + item.name"
            :aria-expanded="Boolean(expandedDbs[item.name])"
            @click="toggleDatabase(item.name)"
          >
            <ChevronRight :size="14" :class="expandedDbs[item.name] ? 'rotate-90' : ''" />
          </button>
          <button class="tree-label" @click="selectDatabase(item.name)">
            <Database :size="14" class="shrink-0 text-brand" />
            <span class="truncate">{{ item.name }}</span>
          </button>
        </div>
        <div v-if="expandedDbs[item.name]" class="ml-3 border-l border-line/80">
          <p v-if="loadingTables[item.name]" class="px-3 py-1.5 text-xs text-muted">加载表…</p>
          <p v-else-if="tableError[item.name]" class="px-3 py-1.5 text-xs text-danger">
            {{ tableError[item.name] }}
          </p>
          <template v-else>
            <label v-if="tablesByDb[item.name]?.length" class="relative mx-2 my-1.5 block">
              <Search class="absolute left-2 top-1.5 text-muted" :size="12" />
              <input
                v-model="tableSearch"
                class="field py-1 pl-7 text-[11px]"
                placeholder="过滤表 / 视图"
              />
            </label>
            <div v-for="group in ['TABLE', 'VIEW'] as const" :key="group">
              <p class="tree-group">{{ group === 'TABLE' ? '表' : '视图' }}</p>
              <p v-if="!tablesOf(item.name, group).length" class="px-3 py-1 text-[11px] text-muted">
                无{{ group === 'TABLE' ? '表' : '视图' }}
              </p>
              <div v-for="table in tablesOf(item.name, group)" :key="table.name">
                <div class="tree-row group" @contextmenu="openMenu($event, item.name, table)">
                  <button
                    class="tree-chevron"
                    :aria-label="
                      (expandedTables[tableKey(item.name, table.name)] ? '折叠 ' : '展开 ') +
                      table.name
                    "
                    @click="toggleTable(item.name, table.name)"
                  >
                    <ChevronRight
                      :size="14"
                      :class="expandedTables[tableKey(item.name, table.name)] ? 'rotate-90' : ''"
                    />
                  </button>
                  <button
                    class="tree-label"
                    :title="table.comment || table.name"
                    @click="openStructure(item.name, table.name)"
                    @dblclick.stop="emit('insert', quoteIdentifier(table.name), item.name)"
                  >
                    <Table2 v-if="group === 'TABLE'" :size="13" class="shrink-0 text-sky-600" />
                    <Eye v-else :size="13" class="shrink-0 text-violet-600" />
                    <span class="truncate">{{ table.name }}</span>
                  </button>
                  <div class="mr-1 flex shrink-0 opacity-0 group-hover:opacity-100">
                    <button class="icon-btn" title="复制名称" @click="copyName(table.name)">
                      <Copy :size="12" />
                    </button>
                    <button
                      class="icon-btn"
                      title="生成 SELECT"
                      @click="generateSelect(item.name, table.name)"
                    >
                      SQL
                    </button>
                    <button
                      class="icon-btn"
                      title="查看结构"
                      @click="openStructure(item.name, table.name)"
                    >
                      <Eye :size="12" />
                    </button>
                  </div>
                </div>
                <div
                  v-if="expandedTables[tableKey(item.name, table.name)]"
                  class="ml-5 border-l border-line/80 py-0.5"
                >
                  <button
                    v-for="column in details[tableKey(item.name, table.name)]?.columns || []"
                    :key="column.name"
                    class="tree-row pl-1"
                    :title="column.comment || column.typeName"
                    @dblclick="emit('insert', quoteIdentifier(column.name), item.name)"
                  >
                    <span class="grid w-4 place-items-center">
                      <KeyRound v-if="column.primaryKey" :size="11" class="text-amber-500" />
                      <Columns3 v-else :size="11" class="text-slate-400" />
                    </span>
                    <span class="min-w-0 flex-1 truncate text-left">{{ column.name }}</span>
                    <span class="max-w-[72px] truncate pr-2 text-[10px] text-muted">{{
                      column.typeName
                    }}</span>
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
    <aside
      v-if="drawer"
      class="flex max-h-[46%] min-h-[180px] flex-col border-t border-line bg-slate-50"
    >
      <div class="flex items-center gap-2 border-b border-line px-3 py-2">
        <Table2 :size="14" class="text-sky-600" />
        <div class="min-w-0 flex-1">
          <strong class="block truncate text-sm">{{ drawer.table }}</strong>
          <span class="text-[11px] text-muted">{{ drawer.database }}</span>
        </div>
        <button class="icon-btn" aria-label="关闭结构" @click="drawer = null">×</button>
      </div>
      <div class="min-h-0 flex-1 overflow-auto px-3 py-2 text-xs">
        <p class="mb-1 font-semibold text-muted">字段</p>
        <div
          v-for="column in drawer.columns"
          :key="column.name"
          class="grid grid-cols-[1fr_auto] gap-x-2 border-b border-line/70 py-1.5"
        >
          <span>
            <b>{{ column.name }}</b>
            <KeyRound v-if="column.primaryKey" :size="11" class="ml-1 inline text-amber-500" />
            <span class="ml-1 text-muted">{{ column.typeName }}</span>
          </span>
          <span class="text-muted">{{ column.nullable ? 'NULL' : 'NOT NULL' }}</span>
          <span v-if="column.comment" class="col-span-2 text-[11px] text-muted">{{
            column.comment
          }}</span>
        </div>
        <p class="mb-1 mt-3 font-semibold text-muted">主键</p>
        <p class="text-muted">
          {{ drawer.primaryKey ? drawer.primaryKey.columns.join(', ') : '无' }}
        </p>
        <p class="mb-1 mt-3 font-semibold text-muted">索引</p>
        <p v-for="index in drawer.indexes" :key="index.name" class="py-0.5">
          {{ index.name }} · {{ index.unique ? 'UNIQUE' : index.type }} ({{
            index.columns.join(', ')
          }})
        </p>
      </div>
    </aside>
    <Teleport to="body">
      <div
        v-if="menu"
        class="fixed z-50 min-w-44 overflow-hidden rounded-lg border border-line bg-white py-1 text-sm shadow-lg"
        :style="{ left: `${menu.x}px`, top: `${menu.y}px` }"
        @click.stop
      >
        <button class="menu-item" @click="copyName(menu.table.name)">复制名称</button>
        <button class="menu-item" @click="generateSelect(menu.database, menu.table.name)">
          生成 SELECT 前 100 行
        </button>
        <button class="menu-item" @click="openStructure(menu.database, menu.table.name)">
          查看结构
        </button>
      </div>
    </Teleport>
  </section>
</template>
