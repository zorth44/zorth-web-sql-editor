<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ChevronRight, Copy, Eye, RefreshCw, Search } from 'lucide-vue-next'
import { getTableDetail, listDatabases, listTables } from '@/api/metadata'
import { quoteIdentifier, selectPreview } from '@/sql-editor/sql'
import { safeErrorMessage } from '@/api/api-error'
import type { DatabaseItem, TableDetail, TableItem } from '@/types/contracts'
const props = defineProps<{ dataSourceId: string | null; database: string | null }>()
const emit = defineEmits<{
  'select-database': [value: string]
  insert: [sql: string]
  notice: [message: string]
  suggestions: [values: string[]]
}>()
const databases = ref<DatabaseItem[]>([]),
  tables = ref<TableItem[]>([])
const databaseSearch = ref(''),
  tableSearch = ref(''),
  loading = ref(false),
  error = ref('')
const detail = ref<TableDetail | null>(null)
const visibleDatabases = computed(() =>
  databases.value.filter((item) =>
    item.name.toLowerCase().includes(databaseSearch.value.toLowerCase()),
  ),
)
const visibleTables = computed(() =>
  tables.value.filter((item) => item.name.toLowerCase().includes(tableSearch.value.toLowerCase())),
)
async function loadDatabases(): Promise<void> {
  if (!props.dataSourceId) {
    databases.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    databases.value = (await listDatabases(props.dataSourceId)).items
    emit(
      'suggestions',
      databases.value.map((item) => item.name),
    )
  } catch (e) {
    error.value = safeErrorMessage(e, '数据库加载失败')
  } finally {
    loading.value = false
  }
}
async function loadTables(): Promise<void> {
  if (!props.dataSourceId || !props.database) {
    tables.value = []
    return
  }
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    tables.value = (await listTables(props.dataSourceId, props.database)).items
    emit('suggestions', [props.database, ...tables.value.map((item) => item.name)])
  } catch (e) {
    error.value = safeErrorMessage(e, '表加载失败')
  } finally {
    loading.value = false
  }
}
async function showDetail(table: string): Promise<void> {
  if (!props.dataSourceId || !props.database) return
  try {
    detail.value = await getTableDetail(props.dataSourceId, props.database, table)
    emit('suggestions', [props.database, table, ...detail.value.columns.map((item) => item.name)])
  } catch (e) {
    emit('notice', safeErrorMessage(e, '表结构加载失败'))
  }
}
async function copyName(name: string): Promise<void> {
  await navigator.clipboard.writeText(name)
  emit('notice', '名称已复制')
}
watch(
  () => props.dataSourceId,
  async () => {
    databases.value = []
    tables.value = []
    detail.value = null
    await loadDatabases()
  },
  { immediate: true },
)
watch(() => props.database, loadTables, { immediate: true })
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-white">
    <div class="border-b border-line p-3">
      <div class="flex items-center justify-between">
        <strong class="text-sm">数据库资源</strong
        ><button class="icon-btn" aria-label="刷新数据库" @click="loadDatabases">
          <RefreshCw :size="15" />
        </button>
      </div>
      <label class="relative mt-2 block"
        ><Search class="absolute left-2 top-2 text-muted" :size="15" /><input
          v-model="databaseSearch"
          class="field py-1.5 pl-8 text-xs"
          placeholder="搜索数据库"
      /></label>
    </div>
    <div class="min-h-0 flex-1 overflow-auto p-2 text-sm">
      <p v-if="!dataSourceId" class="p-3 text-xs text-muted">请先选择数据源</p>
      <p v-else-if="loading" class="p-3 text-xs text-muted">正在加载…</p>
      <p v-if="error" class="p-3 text-xs text-danger">{{ error }}</p>
      <button
        v-for="item in visibleDatabases"
        :key="item.name"
        class="flex w-full items-center gap-1 rounded px-2 py-1.5 text-left hover:bg-slate-100"
        :class="{ 'bg-teal-50 text-brand': database === item.name }"
        @click="emit('select-database', item.name)"
      >
        <ChevronRight :size="14" />{{ item.name }}
      </button>
      <template v-if="database"
        ><div class="my-2 border-t border-line pt-2">
          <label class="relative block"
            ><Search class="absolute left-2 top-2 text-muted" :size="15" /><input
              v-model="tableSearch"
              class="field py-1.5 pl-8 text-xs"
              placeholder="过滤表 / 视图"
          /></label>
        </div>
        <div v-for="group in ['TABLE', 'VIEW']" :key="group" class="mb-3">
          <p class="px-2 py-1 text-[11px] font-semibold uppercase tracking-wider text-muted">
            {{ group === 'TABLE' ? '表' : '视图' }}
          </p>
          <div
            v-for="table in visibleTables.filter((item) => item.type === group)"
            :key="table.name"
            class="group flex items-center rounded hover:bg-slate-100"
          >
            <button
              class="min-w-0 flex-1 truncate px-2 py-1.5 text-left"
              :title="table.comment || table.name"
              @dblclick="emit('insert', quoteIdentifier(table.name))"
            >
              {{ table.name }}</button
            ><button
              class="icon-btn opacity-0 group-hover:opacity-100"
              title="复制名称"
              @click="copyName(table.name)"
            >
              <Copy :size="13" /></button
            ><button
              class="icon-btn opacity-0 group-hover:opacity-100"
              title="生成 SELECT"
              @click="emit('insert', selectPreview(database!, table.name))"
            >
              SQL</button
            ><button
              class="icon-btn opacity-0 group-hover:opacity-100"
              title="查看结构"
              @click="showDetail(table.name)"
            >
              <Eye :size="13" />
            </button>
          </div>
        </div>
      </template>
    </div>
    <div
      v-if="detail"
      class="max-h-[45%] overflow-auto border-t border-line bg-slate-50 p-3 text-xs"
    >
      <div class="flex items-center justify-between">
        <strong>{{ detail.table }}</strong
        ><button aria-label="关闭结构" @click="detail = null">×</button>
      </div>
      <p class="mt-2 font-semibold text-muted">字段</p>
      <div
        v-for="column in detail.columns"
        :key="column.name"
        class="mt-1 grid grid-cols-[1fr_auto] gap-2"
      >
        <span
          ><b>{{ column.name }}</b> <span v-if="column.primaryKey">🔑</span
          ><small class="ml-1 text-muted">{{ column.typeName }}</small></span
        ><span>{{ column.nullable ? 'NULL' : 'NOT NULL' }}</span>
      </div>
      <p class="mt-3 font-semibold text-muted">索引</p>
      <p v-for="index in detail.indexes" :key="index.name" class="mt-1">
        {{ index.name }} · {{ index.unique ? 'UNIQUE' : 'INDEX' }} ({{ index.columns.join(', ') }})
      </p>
    </div>
  </section>
</template>
