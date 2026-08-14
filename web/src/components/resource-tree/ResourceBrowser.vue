<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronRight, Menu, RefreshCw, Search } from 'lucide-vue-next'
import { getTableDetail, listDatabases, listTables } from '@/api/metadata'
import { quoteIdentifier, selectPreview } from '@/sql-editor/sql'
import { safeErrorMessage } from '@/api/api-error'
import type { DataSourceListItem, DatabaseItem, TableDetail, TableItem } from '@/types/contracts'

const props = defineProps<{
  sources: DataSourceListItem[]
  dataSourceId: string | null
  database: string | null
  reloadToken?: number
}>()
const emit = defineEmits<{
  'select-connection': [dataSourceId: string, database: string]
  insert: [sql: string, dataSourceId: string, database: string]
  notice: [message: string]
  suggestions: [values: string[]]
  refresh: []
}>()

type ObjectGroup = 'TABLE' | 'VIEW'
type ActiveNode =
  | { kind: 'source'; dataSourceId: string }
  | { kind: 'database'; dataSourceId: string; database: string }
  | { kind: 'group'; dataSourceId: string; database: string; group: ObjectGroup }
  | { kind: 'table'; dataSourceId: string; database: string; table: string }

const groups: { type: ObjectGroup; label: string }[] = [
  { type: 'TABLE', label: '表' },
  { type: 'VIEW', label: '视图' },
]

const databaseSearchBySource = ref<Record<string, string>>({})
const tableSearchBySource = ref<Record<string, string>>({})
const expandedSources = ref<Record<string, boolean>>({})
const expandedDbs = ref<Record<string, boolean>>({})
const expandedGroups = ref<Record<string, boolean>>({})
const databasesBySource = ref<Record<string, DatabaseItem[]>>({})
const loadingDatabases = ref<Record<string, boolean>>({})
const databaseError = ref<Record<string, string>>({})
const tablesByDb = ref<Record<string, TableItem[]>>({})
const loadingTables = ref<Record<string, boolean>>({})
const tableError = ref<Record<string, string>>({})
const details = ref<Record<string, TableDetail>>({})
const drawer = ref<TableDetail | null>(null)
const drawerSourceId = ref<string | null>(null)
const menu = ref<{
  x: number
  y: number
  dataSourceId: string
  database: string
  table: TableItem
} | null>(null)
const activeNode = ref<ActiveNode | null>(null)

function sourceEndpoint(source: DataSourceListItem): string {
  return `${source.host}:${source.port}`
}
function dbKey(dataSourceId: string, database: string): string {
  return `${dataSourceId}:${database}`
}
function tableKey(dataSourceId: string, database: string, table: string): string {
  return `${dataSourceId}:${database}.${table}`
}
function groupKey(dataSourceId: string, database: string, group: ObjectGroup): string {
  return `${dataSourceId}:${database}:${group}`
}
function databaseQuery(dataSourceId: string): string {
  return databaseSearchBySource.value[dataSourceId] || ''
}
function setDatabaseQuery(dataSourceId: string, event: Event): void {
  const value = (event.target as HTMLInputElement).value
  databaseSearchBySource.value = { ...databaseSearchBySource.value, [dataSourceId]: value }
}
function tableQuery(dataSourceId: string): string {
  return tableSearchBySource.value[dataSourceId] || ''
}
function setTableQuery(dataSourceId: string, event: Event): void {
  const value = (event.target as HTMLInputElement).value
  tableSearchBySource.value = { ...tableSearchBySource.value, [dataSourceId]: value }
}
function databasesOf(dataSourceId: string): DatabaseItem[] {
  const query = databaseQuery(dataSourceId).trim().toLowerCase()
  const items = databasesBySource.value[dataSourceId] || []
  if (!query) return items
  return items.filter((item) => item.name.toLowerCase().includes(query))
}
function tablesOf(dataSourceId: string, database: string, type: ObjectGroup): TableItem[] {
  const q = tableQuery(dataSourceId).trim().toLowerCase()
  return (tablesByDb.value[dbKey(dataSourceId, database)] || []).filter(
    (item) => item.type === type && (!q || item.name.toLowerCase().includes(q)),
  )
}
function isGroupOpen(dataSourceId: string, database: string, group: ObjectGroup): boolean {
  if (tableQuery(dataSourceId).trim()) return true
  return Boolean(expandedGroups.value[groupKey(dataSourceId, database, group)])
}
function isActive(node: ActiveNode): boolean {
  const current = activeNode.value
  if (!current || current.kind !== node.kind) return false
  if (current.kind === 'source' && node.kind === 'source') {
    return current.dataSourceId === node.dataSourceId
  }
  if (current.kind === 'database' && node.kind === 'database') {
    return current.dataSourceId === node.dataSourceId && current.database === node.database
  }
  if (current.kind === 'group' && node.kind === 'group') {
    return (
      current.dataSourceId === node.dataSourceId &&
      current.database === node.database &&
      current.group === node.group
    )
  }
  return (
    current.kind === 'table' &&
    node.kind === 'table' &&
    current.dataSourceId === node.dataSourceId &&
    current.database === node.database &&
    current.table === node.table
  )
}
function statusFill(status: DataSourceListItem['lastTestStatus']): string {
  if (status === 'SUCCESS') return '#22c55e'
  if (status === 'FAILED') return '#ef4444'
  return '#94a3b8'
}
function isChildOfDatabase(dataSourceId: string, database: string): boolean {
  const current = activeNode.value
  if (!current || current.kind === 'source') return false
  return current.dataSourceId === dataSourceId && current.database === database
}
function publishSuggestions(): void {
  const names = [
    ...props.sources.map((item) => item.name),
    ...Object.values(databasesBySource.value).flatMap((items) => items.map((item) => item.name)),
    ...Object.values(tablesByDb.value).flatMap((items) => items.map((item) => item.name)),
    ...Object.values(details.value).flatMap((item) => item.columns.map((column) => column.name)),
  ]
  emit('suggestions', Array.from(new Set(names)))
}
async function loadDatabases(dataSourceId: string, force = false): Promise<void> {
  if (!force && databasesBySource.value[dataSourceId]) return
  loadingDatabases.value = { ...loadingDatabases.value, [dataSourceId]: true }
  databaseError.value = { ...databaseError.value, [dataSourceId]: '' }
  try {
    const page = await listDatabases(dataSourceId)
    databasesBySource.value = { ...databasesBySource.value, [dataSourceId]: page.items }
    publishSuggestions()
  } catch (e) {
    databaseError.value = {
      ...databaseError.value,
      [dataSourceId]: safeErrorMessage(e, '数据库加载失败'),
    }
  } finally {
    loadingDatabases.value = { ...loadingDatabases.value, [dataSourceId]: false }
  }
}
async function loadTables(dataSourceId: string, database: string, force = false): Promise<void> {
  const key = dbKey(dataSourceId, database)
  if (!force && tablesByDb.value[key]) return
  loadingTables.value = { ...loadingTables.value, [key]: true }
  tableError.value = { ...tableError.value, [key]: '' }
  try {
    const page = await listTables(dataSourceId, database)
    tablesByDb.value = { ...tablesByDb.value, [key]: page.items }
    publishSuggestions()
  } catch (e) {
    tableError.value = { ...tableError.value, [key]: safeErrorMessage(e, '表加载失败') }
  } finally {
    loadingTables.value = { ...loadingTables.value, [key]: false }
  }
}
async function ensureDetail(
  dataSourceId: string,
  database: string,
  table: string,
): Promise<TableDetail | null> {
  const key = tableKey(dataSourceId, database, table)
  if (details.value[key]) return details.value[key]
  try {
    const detail = await getTableDetail(dataSourceId, database, table)
    details.value = { ...details.value, [key]: detail }
    publishSuggestions()
    return detail
  } catch (e) {
    emit('notice', safeErrorMessage(e, '表结构加载失败'))
    return null
  }
}
async function expandSource(dataSourceId: string): Promise<void> {
  expandedSources.value = { ...expandedSources.value, [dataSourceId]: true }
  await loadDatabases(dataSourceId)
}
async function expandDatabase(dataSourceId: string, database: string): Promise<void> {
  const key = dbKey(dataSourceId, database)
  expandedDbs.value = { ...expandedDbs.value, [key]: true }
  if (expandedGroups.value[groupKey(dataSourceId, database, 'TABLE')] === undefined) {
    expandedGroups.value = {
      ...expandedGroups.value,
      [groupKey(dataSourceId, database, 'TABLE')]: true,
    }
  }
  await loadTables(dataSourceId, database)
}
function toggleSource(dataSourceId: string): void {
  if (expandedSources.value[dataSourceId]) {
    expandedSources.value = { ...expandedSources.value, [dataSourceId]: false }
    return
  }
  void expandSource(dataSourceId)
}
function selectSource(dataSourceId: string): void {
  activeNode.value = { kind: 'source', dataSourceId }
  void expandSource(dataSourceId)
}
function toggleDatabase(dataSourceId: string, database: string): void {
  const key = dbKey(dataSourceId, database)
  if (expandedDbs.value[key]) {
    expandedDbs.value = { ...expandedDbs.value, [key]: false }
    return
  }
  void expandDatabase(dataSourceId, database)
}
function selectDatabase(dataSourceId: string, database: string): void {
  activeNode.value = { kind: 'database', dataSourceId, database }
  void expandDatabase(dataSourceId, database)
  emit('select-connection', dataSourceId, database)
}
function toggleGroup(dataSourceId: string, database: string, group: ObjectGroup): void {
  const key = groupKey(dataSourceId, database, group)
  expandedGroups.value = { ...expandedGroups.value, [key]: !expandedGroups.value[key] }
}
function selectGroup(dataSourceId: string, database: string, group: ObjectGroup): void {
  activeNode.value = { kind: 'group', dataSourceId, database, group }
  if (!isGroupOpen(dataSourceId, database, group)) toggleGroup(dataSourceId, database, group)
  emit('select-connection', dataSourceId, database)
}
async function openStructure(dataSourceId: string, database: string, table: string): Promise<void> {
  activeNode.value = { kind: 'table', dataSourceId, database, table }
  emit('select-connection', dataSourceId, database)
  drawer.value = await ensureDetail(dataSourceId, database, table)
  drawerSourceId.value = dataSourceId
  menu.value = null
}
async function copyName(name: string): Promise<void> {
  await navigator.clipboard.writeText(name)
  emit('notice', '名称已复制')
  menu.value = null
}
function generateSelect(dataSourceId: string, database: string, table: string): void {
  emit('insert', selectPreview(database, table), dataSourceId, database)
  menu.value = null
}
function openMenu(
  event: MouseEvent,
  dataSourceId: string,
  database: string,
  table: TableItem,
): void {
  event.preventDefault()
  menu.value = { x: event.clientX, y: event.clientY, dataSourceId, database, table }
}
function openRowMenu(
  event: MouseEvent,
  dataSourceId: string,
  database: string,
  table: TableItem,
): void {
  event.preventDefault()
  event.stopPropagation()
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  menu.value = { x: rect.left, y: rect.bottom, dataSourceId, database, table }
}
function closeMenu(): void {
  menu.value = null
}
watch(
  () => props.reloadToken,
  async () => {
    if (!props.reloadToken) return
    const openSources = Object.entries(expandedSources.value)
      .filter(([, open]) => open)
      .map(([id]) => id)
    await Promise.all(openSources.map((id) => loadDatabases(id, true)))
    const openDbs = Object.entries(expandedDbs.value)
      .filter(([, open]) => open)
      .map(([key]) => key)
    await Promise.all(
      openDbs.map((key) => {
        const sep = key.indexOf(':')
        return loadTables(key.slice(0, sep), key.slice(sep + 1), true)
      }),
    )
    if (drawer.value && drawerSourceId.value) {
      const key = tableKey(drawerSourceId.value, drawer.value.database, drawer.value.table)
      const next = { ...details.value }
      delete next[key]
      details.value = next
      drawer.value = await ensureDetail(
        drawerSourceId.value,
        drawer.value.database,
        drawer.value.table,
      )
    }
  },
)
watch(
  () => [props.dataSourceId, props.database] as const,
  async ([sourceId, database]) => {
    if (!sourceId || !props.sources.some((item) => item.id === sourceId)) return
    await expandSource(sourceId)
    if (database) {
      if (!isChildOfDatabase(sourceId, database)) {
        activeNode.value = { kind: 'database', dataSourceId: sourceId, database }
      }
      await expandDatabase(sourceId, database)
    } else if (!activeNode.value || activeNode.value.dataSourceId !== sourceId) {
      activeNode.value = { kind: 'source', dataSourceId: sourceId }
    }
    publishSuggestions()
  },
  { immediate: true },
)
watch(
  () => props.sources,
  () => publishSuggestions(),
  { immediate: true },
)
onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-panel">
    <div class="flex items-center justify-between border-b border-line px-3 py-2.5">
      <strong class="text-sm">数据库导航</strong>
      <button class="icon-btn" aria-label="刷新数据库" title="刷新" @click="emit('refresh')">
        <RefreshCw :size="14" />
      </button>
    </div>
    <div class="min-h-0 flex-1 overflow-auto py-1 text-[12px] leading-none">
      <div v-if="!sources.length" class="px-3 py-6 text-center text-xs text-muted">
        <p>还没有可见的数据源</p>
        <RouterLink class="mt-2 inline-block text-brand" to="/data-sources"
          >去数据源管理</RouterLink
        >
      </div>
      <div v-for="source in sources" :key="source.id">
        <div
          class="tree-row"
          :class="{ 'tree-row-active': isActive({ kind: 'source', dataSourceId: source.id }) }"
          :data-testid="`navigator-source-${source.id}`"
        >
          <button
            class="tree-chevron"
            :aria-label="
              (expandedSources[source.id] ? '折叠 ' : '展开 ') +
              source.name +
              ' ' +
              sourceEndpoint(source)
            "
            :aria-expanded="Boolean(expandedSources[source.id])"
            @click="toggleSource(source.id)"
          >
            <ChevronRight :size="12" :class="expandedSources[source.id] ? 'rotate-90' : ''" />
          </button>
          <button
            class="tree-label"
            :title="`${source.name} · ${sourceEndpoint(source)}`"
            :aria-label="`${source.name} ${sourceEndpoint(source)}`"
            @click="selectSource(source.id)"
          >
            <svg class="tree-icon" viewBox="0 0 16 16" aria-hidden="true">
              <ellipse cx="8" cy="4.2" rx="5.6" ry="2.1" fill="#0f766e" />
              <path
                fill="#0f766e"
                d="M2.4 4.2v7.4c0 1.16 2.5 2.1 5.6 2.1s5.6-.94 5.6-2.1V4.2c0 1.16-2.5 2.1-5.6 2.1S2.4 5.36 2.4 4.2Z"
              />
              <ellipse cx="8" cy="4.2" rx="5.6" ry="2.1" fill="#14b8a6" />
              <circle cx="11.4" cy="11.2" r="2.05" :fill="statusFill(source.lastTestStatus)" />
            </svg>
            <span class="truncate">{{ source.name }}</span>
            <span class="truncate text-[10px] text-muted">{{ sourceEndpoint(source) }}</span>
          </button>
        </div>
        <div v-if="expandedSources[source.id]">
          <div
            class="tree-filter tree-row-depth-1"
            role="search"
            :aria-label="'筛选 ' + source.name + ' 的数据库和表'"
          >
            <Search class="shrink-0" :size="12" aria-hidden="true" />
            <input
              class="tree-filter-input"
              placeholder="筛选库名"
              aria-label="筛选数据库"
              title="筛选数据库"
              :data-testid="`navigator-db-filter-${source.id}`"
              :value="databaseQuery(source.id)"
              @input="setDatabaseQuery(source.id, $event)"
            />
            <input
              class="tree-filter-input"
              placeholder="筛选表名"
              aria-label="筛选表"
              title="筛选表 / 视图"
              :data-testid="`navigator-table-filter-${source.id}`"
              :value="tableQuery(source.id)"
              @input="setTableQuery(source.id, $event)"
            />
          </div>
          <p v-if="loadingDatabases[source.id]" class="px-3 py-1.5 text-xs text-muted">
            正在加载数据库…
          </p>
          <p v-else-if="databaseError[source.id]" class="px-3 py-1.5 text-xs text-danger">
            {{ databaseError[source.id] }}
          </p>
          <p v-else-if="!databasesOf(source.id).length" class="tree-empty tree-row-depth-1">
            没有匹配的数据库
          </p>
          <div v-for="item in databasesOf(source.id)" :key="item.name">
            <div
              class="tree-row tree-row-depth-1"
              :class="{
                'tree-row-active': isActive({
                  kind: 'database',
                  dataSourceId: source.id,
                  database: item.name,
                }),
              }"
              :data-testid="`navigator-database-${source.id}-${item.name}`"
            >
              <button
                class="tree-chevron"
                :aria-label="
                  (expandedDbs[dbKey(source.id, item.name)] ? '折叠 ' : '展开 ') + item.name
                "
                :aria-expanded="Boolean(expandedDbs[dbKey(source.id, item.name)])"
                @click="toggleDatabase(source.id, item.name)"
              >
                <ChevronRight
                  :size="12"
                  :class="expandedDbs[dbKey(source.id, item.name)] ? 'rotate-90' : ''"
                />
              </button>
              <button class="tree-label" @click="selectDatabase(source.id, item.name)">
                <svg class="tree-icon" viewBox="0 0 16 16" aria-hidden="true">
                  <ellipse cx="8" cy="4.2" rx="5.6" ry="2.1" fill="#3b82f6" />
                  <path
                    fill="#3b82f6"
                    d="M2.4 4.2v7.4c0 1.16 2.5 2.1 5.6 2.1s5.6-.94 5.6-2.1V4.2c0 1.16-2.5 2.1-5.6 2.1S2.4 5.36 2.4 4.2Z"
                  />
                  <ellipse cx="8" cy="4.2" rx="5.6" ry="2.1" fill="#60a5fa" />
                  <circle cx="11.4" cy="11.2" r="2.05" fill="#22c55e" />
                </svg>
                <span class="truncate">{{ item.name }}</span>
              </button>
            </div>
            <div v-if="expandedDbs[dbKey(source.id, item.name)]">
              <p
                v-if="loadingTables[dbKey(source.id, item.name)]"
                class="px-3 py-1.5 text-xs text-muted"
              >
                加载表…
              </p>
              <p
                v-else-if="tableError[dbKey(source.id, item.name)]"
                class="px-3 py-1.5 text-xs text-danger"
              >
                {{ tableError[dbKey(source.id, item.name)] }}
              </p>
              <template v-else>
                <div v-for="group in groups" :key="group.type">
                  <div
                    class="tree-row tree-row-depth-2 group"
                    :class="{
                      'tree-row-active': isActive({
                        kind: 'group',
                        dataSourceId: source.id,
                        database: item.name,
                        group: group.type,
                      }),
                    }"
                  >
                    <button
                      class="tree-chevron"
                      :aria-label="
                        (isGroupOpen(source.id, item.name, group.type) ? '折叠 ' : '展开 ') +
                        group.label
                      "
                      :aria-expanded="isGroupOpen(source.id, item.name, group.type)"
                      @click="toggleGroup(source.id, item.name, group.type)"
                    >
                      <ChevronRight
                        :size="12"
                        :class="isGroupOpen(source.id, item.name, group.type) ? 'rotate-90' : ''"
                      />
                    </button>
                    <button
                      class="tree-label"
                      @click="selectGroup(source.id, item.name, group.type)"
                    >
                      <svg class="tree-icon" viewBox="0 0 16 16" aria-hidden="true">
                        <path
                          fill="#f59e0b"
                          d="M1.2 4.15c0-.64.52-1.15 1.15-1.15h3.05l.95 1.2h7.3c.63 0 1.15.51 1.15 1.15v6.65c0 .64-.52 1.15-1.15 1.15H2.35c-.63 0-1.15-.51-1.15-1.15V4.15Z"
                        />
                        <path
                          v-if="group.type === 'TABLE'"
                          fill="#fff"
                          d="M6.1 7.15h5.6v4.2H6.1zm1.15 0v4.2h.7V7.15zm0 1.2h4.45v.7H7.25z"
                        />
                        <path
                          v-else
                          fill="#fff"
                          fill-rule="evenodd"
                          d="M8.9 8.05c-1.35 0-2.5.7-3.15 1.75.65 1.05 1.8 1.75 3.15 1.75s2.5-.7 3.15-1.75c-.65-1.05-1.8-1.75-3.15-1.75Zm0 2.85a1.1 1.1 0 1 1 0-2.2 1.1 1.1 0 0 1 0 2.2Z"
                        />
                      </svg>
                      <span class="truncate">{{ group.label }}</span>
                    </button>
                  </div>
                  <template v-if="isGroupOpen(source.id, item.name, group.type)">
                    <p
                      v-if="!tablesOf(source.id, item.name, group.type).length"
                      class="tree-empty tree-row-depth-3"
                    >
                      无{{ group.label }}
                    </p>
                    <div
                      v-for="table in tablesOf(source.id, item.name, group.type)"
                      :key="table.name"
                      class="tree-row tree-row-depth-3 group"
                      :class="{
                        'tree-row-active': isActive({
                          kind: 'table',
                          dataSourceId: source.id,
                          database: item.name,
                          table: table.name,
                        }),
                      }"
                      @contextmenu="openMenu($event, source.id, item.name, table)"
                    >
                      <span class="tree-chevron" aria-hidden="true" />
                      <button
                        class="tree-label"
                        :title="table.comment || table.name"
                        @click="openStructure(source.id, item.name, table.name)"
                        @dblclick.stop="
                          emit('insert', quoteIdentifier(table.name), source.id, item.name)
                        "
                      >
                        <svg
                          v-if="group.type === 'TABLE'"
                          class="tree-icon"
                          viewBox="0 0 16 16"
                          aria-hidden="true"
                        >
                          <rect
                            x="1.4"
                            y="2.4"
                            width="13.2"
                            height="11.2"
                            rx="1.2"
                            fill="#3b82f6"
                          />
                          <path
                            fill="#dbeafe"
                            d="M1.4 5.15h13.2v1.05H1.4zM6.35 2.4h1.05v11.2H6.35z"
                          />
                        </svg>
                        <svg v-else class="tree-icon" viewBox="0 0 16 16" aria-hidden="true">
                          <rect
                            x="1.4"
                            y="2.4"
                            width="13.2"
                            height="11.2"
                            rx="1.2"
                            fill="#6366f1"
                          />
                          <path
                            fill="#eef2ff"
                            fill-rule="evenodd"
                            d="M8 6.05c-1.7 0-3.15.9-3.95 2.25C4.85 9.65 6.3 10.55 8 10.55s3.15-.9 3.95-2.25C11.15 6.95 9.7 6.05 8 6.05Zm0 3.5a1.25 1.25 0 1 1 0-2.5 1.25 1.25 0 0 1 0 2.5Z"
                          />
                        </svg>
                        <span class="truncate">{{ table.name }}</span>
                      </button>
                      <button
                        class="icon-btn mr-0.5 shrink-0"
                        :class="
                          isActive({
                            kind: 'table',
                            dataSourceId: source.id,
                            database: item.name,
                            table: table.name,
                          })
                            ? 'opacity-100'
                            : 'opacity-0 group-hover:opacity-100'
                        "
                        title="更多操作"
                        :aria-label="'打开 ' + table.name + ' 菜单'"
                        @click="openRowMenu($event, source.id, item.name, table)"
                      >
                        <Menu :size="12" />
                      </button>
                    </div>
                  </template>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
    <aside
      v-if="drawer"
      class="flex max-h-[46%] min-h-[180px] flex-col border-t border-line bg-subtle"
    >
      <div class="flex items-center gap-2 border-b border-line px-3 py-2">
        <svg class="tree-icon" viewBox="0 0 16 16" aria-hidden="true">
          <rect x="1.4" y="2.4" width="13.2" height="11.2" rx="1.2" fill="#3b82f6" />
          <path fill="#dbeafe" d="M1.4 5.15h13.2v1.05H1.4zM6.35 2.4h1.05v11.2H6.35z" />
        </svg>
        <div class="min-w-0 flex-1">
          <strong class="block truncate text-sm">{{ drawer.table }}</strong>
          <span class="text-[11px] text-muted">{{ drawer.database }}</span>
        </div>
        <button class="icon-btn" aria-label="关闭结构" @click="drawer = null">×</button>
      </div>
      <div class="min-h-0 flex-1 overflow-auto px-3 py-2 text-xs leading-normal">
        <p class="mb-1 font-semibold text-muted">字段</p>
        <div
          v-for="column in drawer.columns"
          :key="column.name"
          class="grid grid-cols-[1fr_auto] gap-x-2 border-b border-line/70 py-1.5"
        >
          <span>
            <b>{{ column.name }}</b>
            <span v-if="column.primaryKey" class="ml-1 text-amber-500">PK</span>
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
        class="fixed z-50 min-w-44 overflow-hidden rounded-lg border border-line bg-panel py-1 text-sm shadow-lg"
        :style="{ left: `${menu.x}px`, top: `${menu.y}px` }"
        @click.stop
      >
        <button class="menu-item" @click="copyName(menu.table.name)">复制名称</button>
        <button
          class="menu-item"
          @click="generateSelect(menu.dataSourceId, menu.database, menu.table.name)"
        >
          生成 SELECT 前 100 行
        </button>
        <button
          class="menu-item"
          @click="openStructure(menu.dataSourceId, menu.database, menu.table.name)"
        >
          查看结构
        </button>
      </div>
    </Teleport>
  </section>
</template>
