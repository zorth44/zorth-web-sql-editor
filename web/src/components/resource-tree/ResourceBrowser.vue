<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronRight, Menu, RefreshCw, Search } from 'lucide-vue-next'
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

type ObjectGroup = 'TABLE' | 'VIEW'
type ActiveNode =
  | { kind: 'database'; database: string }
  | { kind: 'group'; database: string; group: ObjectGroup }
  | { kind: 'table'; database: string; table: string }

const groups: { type: ObjectGroup; label: string }[] = [
  { type: 'TABLE', label: '表' },
  { type: 'VIEW', label: '视图' },
]

const databaseSearch = ref('')
const tableSearch = ref('')
const expandedDbs = ref<Record<string, boolean>>({})
const expandedGroups = ref<Record<string, boolean>>({})
const tablesByDb = ref<Record<string, TableItem[]>>({})
const loadingTables = ref<Record<string, boolean>>({})
const tableError = ref<Record<string, string>>({})
const details = ref<Record<string, TableDetail>>({})
const drawer = ref<TableDetail | null>(null)
const menu = ref<{ x: number; y: number; database: string; table: TableItem } | null>(null)
const activeNode = ref<ActiveNode | null>(null)

const visibleDatabases = computed(() =>
  props.databases.filter((item) =>
    item.name.toLowerCase().includes(databaseSearch.value.trim().toLowerCase()),
  ),
)

function tableKey(database: string, table: string): string {
  return `${database}.${table}`
}
function groupKey(database: string, group: ObjectGroup): string {
  return `${database}:${group}`
}
function tablesOf(database: string, type: ObjectGroup): TableItem[] {
  const q = tableSearch.value.trim().toLowerCase()
  return (tablesByDb.value[database] || []).filter(
    (item) => item.type === type && (!q || item.name.toLowerCase().includes(q)),
  )
}
function isGroupOpen(database: string, group: ObjectGroup): boolean {
  if (tableSearch.value.trim()) return true
  return Boolean(expandedGroups.value[groupKey(database, group)])
}
function isActive(node: ActiveNode): boolean {
  const current = activeNode.value
  if (!current || current.kind !== node.kind) return false
  if (current.kind === 'database' && node.kind === 'database') {
    return current.database === node.database
  }
  if (current.kind === 'group' && node.kind === 'group') {
    return current.database === node.database && current.group === node.group
  }
  return (
    current.kind === 'table' &&
    node.kind === 'table' &&
    current.database === node.database &&
    current.table === node.table
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
  if (expandedGroups.value[groupKey(name, 'TABLE')] === undefined) {
    expandedGroups.value = { ...expandedGroups.value, [groupKey(name, 'TABLE')]: true }
  }
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
  activeNode.value = { kind: 'database', database: name }
  void expandDatabase(name)
  emit('select-database', name)
}
function toggleGroup(database: string, group: ObjectGroup): void {
  const key = groupKey(database, group)
  expandedGroups.value = { ...expandedGroups.value, [key]: !expandedGroups.value[key] }
}
function selectGroup(database: string, group: ObjectGroup): void {
  activeNode.value = { kind: 'group', database, group }
  if (!isGroupOpen(database, group)) toggleGroup(database, group)
  emit('select-database', database)
}
async function openStructure(database: string, table: string): Promise<void> {
  activeNode.value = { kind: 'table', database, table }
  emit('select-database', database)
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
function openRowMenu(event: MouseEvent, database: string, table: TableItem): void {
  event.preventDefault()
  event.stopPropagation()
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  menu.value = { x: rect.left, y: rect.bottom, database, table }
}
function closeMenu(): void {
  menu.value = null
}
watch(
  () => props.dataSourceId,
  () => {
    expandedDbs.value = {}
    expandedGroups.value = {}
    tablesByDb.value = {}
    details.value = {}
    drawer.value = null
    menu.value = null
    activeNode.value = null
  },
)
watch(
  () => props.reloadToken,
  async () => {
    if (!props.reloadToken) return
    tablesByDb.value = {}
    details.value = {}
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
      if (!activeNode.value) activeNode.value = { kind: 'database', database }
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
  <section class="flex h-full min-h-0 flex-col bg-panel">
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
    <div class="min-h-0 flex-1 overflow-auto py-1 text-[12px] leading-none">
      <p v-if="!dataSourceId" class="px-3 py-6 text-center text-xs text-muted">请先选择数据源</p>
      <p v-else-if="loadingDatabases" class="px-3 py-6 text-center text-xs text-muted">
        正在加载数据库…
      </p>
      <p v-else-if="!visibleDatabases.length" class="px-3 py-6 text-center text-xs text-muted">
        没有匹配的数据库
      </p>
      <div v-for="item in visibleDatabases" :key="item.name">
        <div
          class="tree-row"
          :class="{ 'tree-row-active': isActive({ kind: 'database', database: item.name }) }"
        >
          <button
            class="tree-chevron"
            :aria-label="(expandedDbs[item.name] ? '折叠 ' : '展开 ') + item.name"
            :aria-expanded="Boolean(expandedDbs[item.name])"
            @click="toggleDatabase(item.name)"
          >
            <ChevronRight :size="12" :class="expandedDbs[item.name] ? 'rotate-90' : ''" />
          </button>
          <button class="tree-label" @click="selectDatabase(item.name)">
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
        <div v-if="expandedDbs[item.name]">
          <p v-if="loadingTables[item.name]" class="px-3 py-1.5 text-xs text-muted">加载表…</p>
          <p v-else-if="tableError[item.name]" class="px-3 py-1.5 text-xs text-danger">
            {{ tableError[item.name] }}
          </p>
          <template v-else>
            <label v-if="tablesByDb[item.name]?.length" class="relative mx-2 my-1 block">
              <Search class="absolute left-2 top-1.5 text-muted" :size="12" />
              <input
                v-model="tableSearch"
                class="field py-1 pl-7 text-[11px]"
                placeholder="过滤表 / 视图"
              />
            </label>
            <div v-for="group in groups" :key="group.type">
              <div
                class="tree-row tree-row-depth-1 group"
                :class="{
                  'tree-row-active': isActive({
                    kind: 'group',
                    database: item.name,
                    group: group.type,
                  }),
                }"
              >
                <button
                  class="tree-chevron"
                  :aria-label="
                    (isGroupOpen(item.name, group.type) ? '折叠 ' : '展开 ') + group.label
                  "
                  :aria-expanded="isGroupOpen(item.name, group.type)"
                  @click="toggleGroup(item.name, group.type)"
                >
                  <ChevronRight
                    :size="12"
                    :class="isGroupOpen(item.name, group.type) ? 'rotate-90' : ''"
                  />
                </button>
                <button class="tree-label" @click="selectGroup(item.name, group.type)">
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
              <template v-if="isGroupOpen(item.name, group.type)">
                <p
                  v-if="!tablesOf(item.name, group.type).length"
                  class="tree-empty tree-row-depth-2"
                >
                  无{{ group.label }}
                </p>
                <div
                  v-for="table in tablesOf(item.name, group.type)"
                  :key="table.name"
                  class="tree-row tree-row-depth-2 group"
                  :class="{
                    'tree-row-active': isActive({
                      kind: 'table',
                      database: item.name,
                      table: table.name,
                    }),
                  }"
                  @contextmenu="openMenu($event, item.name, table)"
                >
                  <span class="tree-chevron" aria-hidden="true" />
                  <button
                    class="tree-label"
                    :title="table.comment || table.name"
                    @click="openStructure(item.name, table.name)"
                    @dblclick.stop="emit('insert', quoteIdentifier(table.name), item.name)"
                  >
                    <svg
                      v-if="group.type === 'TABLE'"
                      class="tree-icon"
                      viewBox="0 0 16 16"
                      aria-hidden="true"
                    >
                      <rect x="1.4" y="2.4" width="13.2" height="11.2" rx="1.2" fill="#3b82f6" />
                      <path fill="#dbeafe" d="M1.4 5.15h13.2v1.05H1.4zM6.35 2.4h1.05v11.2H6.35z" />
                    </svg>
                    <svg v-else class="tree-icon" viewBox="0 0 16 16" aria-hidden="true">
                      <rect x="1.4" y="2.4" width="13.2" height="11.2" rx="1.2" fill="#6366f1" />
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
                      isActive({ kind: 'table', database: item.name, table: table.name })
                        ? 'opacity-100'
                        : 'opacity-0 group-hover:opacity-100'
                    "
                    title="更多操作"
                    :aria-label="'打开 ' + table.name + ' 菜单'"
                    @click="openRowMenu($event, item.name, table)"
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
