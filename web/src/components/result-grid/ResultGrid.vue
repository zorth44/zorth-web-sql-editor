<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Copy, Rows3, Table2 } from 'lucide-vue-next'
import type { BinaryValue, SqlCellValue, SqlExecutionResult } from '@/types/contracts'

const props = defineProps<{
  result: SqlExecutionResult | null
  error: string | null
  running?: boolean
}>()
const filter = ref('')
const scrollEl = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewport = ref(280)
const selected = ref<{ row: number; col: number } | null>(null)
const detail = ref<{ title: string; text: string } | null>(null)
const rowHeight = 32
const copied = ref('')
let copyTimer = 0
let observer: ResizeObserver | undefined

const rows = computed(() => (props.result?.kind === 'RESULT_SET' ? props.result.rows : []))
const filtered = computed(() => {
  const q = filter.value.trim().toLowerCase()
  if (!q) return rows.value.map((row, index) => ({ row, index }))
  return rows.value
    .map((row, index) => ({ row, index }))
    .filter(({ row }) => row.some((cell) => display(cell).toLowerCase().includes(q)))
})
const start = computed(() => Math.max(0, Math.floor(scrollTop.value / rowHeight) - 8))
const end = computed(() =>
  Math.min(filtered.value.length, start.value + Math.ceil(viewport.value / rowHeight) + 16),
)
const visible = computed(() => filtered.value.slice(start.value, end.value))
const summary = computed(() => {
  const result = props.result
  if (!result) return ''
  if (result.kind === 'RESULT_SET') {
    const count = result.rowCount.toLocaleString()
    return result.truncated
      ? `返回 ${count} 行（已达到上限），耗时 ${result.durationMs} ms`
      : `返回 ${count} 行，耗时 ${result.durationMs} ms`
  }
  if (result.affectedRows !== null) {
    return `执行成功，影响 ${result.affectedRows.toLocaleString()} 行，耗时 ${result.durationMs} ms`
  }
  return `执行成功，耗时 ${result.durationMs} ms`
})

function binary(value: unknown): value is BinaryValue {
  return Boolean(value && typeof value === 'object' && (value as BinaryValue).binary === true)
}
function display(value: SqlCellValue): string {
  if (value === null) return 'NULL'
  if (binary(value)) return `BINARY · ${value.size} bytes`
  return String(value)
}
function isLong(value: SqlCellValue): boolean {
  return typeof value === 'string' && value.length > 80
}
function preview(value: SqlCellValue): string {
  const text = display(value)
  return text.length > 80 ? `${text.slice(0, 80)}…` : text
}
async function copy(text: string, label = '已复制'): Promise<void> {
  await navigator.clipboard.writeText(text)
  copied.value = label
  window.clearTimeout(copyTimer)
  copyTimer = window.setTimeout(() => (copied.value = ''), 1600)
}
async function copyCell(): Promise<void> {
  if (!selected.value || props.result?.kind !== 'RESULT_SET') return
  await copy(display(props.result.rows[selected.value.row]?.[selected.value.col]), '单元格已复制')
}
async function copyRow(): Promise<void> {
  if (selected.value == null || props.result?.kind !== 'RESULT_SET') return
  await copy((props.result.rows[selected.value.row] || []).map(display).join('\t'), '整行已复制')
}
async function copyResult(): Promise<void> {
  if (props.result?.kind !== 'RESULT_SET') return
  const lines = [
    props.result.columns.map((column) => column.label).join('\t'),
    ...props.result.rows.map((row) => row.map(display).join('\t')),
  ]
  await copy(lines.join('\n'), '结果已复制')
}
function openCell(value: SqlCellValue, label: string): void {
  if (binary(value) || value === null || !isLong(value)) return
  detail.value = { title: label, text: String(value) }
}
function onScroll(): void {
  scrollTop.value = scrollEl.value?.scrollTop || 0
  viewport.value = scrollEl.value?.clientHeight || viewport.value
}

watch(
  () => [props.result, props.error],
  () => {
    selected.value = null
    filter.value = ''
    detail.value = null
    scrollTop.value = 0
    if (scrollEl.value) scrollEl.value.scrollTop = 0
  },
)
watch(scrollEl, async (el) => {
  observer?.disconnect()
  if (!el) return
  await nextTick()
  viewport.value = el.clientHeight || 280
  if (typeof ResizeObserver === 'undefined') return
  observer = new ResizeObserver(() => {
    viewport.value = el.clientHeight || 280
  })
  observer.observe(el)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  window.clearTimeout(copyTimer)
})
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-white">
    <div class="flex h-9 shrink-0 items-center gap-2 border-b border-line px-3">
      <strong class="text-sm">结果 / 消息</strong>
      <span v-if="running" class="text-xs text-brand">正在执行…</span>
      <span v-else-if="result || error" class="truncate text-xs text-muted">{{
        error ? '执行失败' : summary
      }}</span>
      <span v-if="copied" class="text-xs text-success">{{ copied }}</span>
      <template v-if="result?.kind === 'RESULT_SET'">
        <input
          v-model="filter"
          class="field ml-auto h-7 w-44 py-0 text-xs"
          placeholder="过滤已返回结果"
        />
        <button class="icon-btn" title="复制单元格" :disabled="!selected" @click="copyCell">
          <Copy :size="13" />
        </button>
        <button class="icon-btn" title="复制整行" :disabled="!selected" @click="copyRow">
          <Rows3 :size="13" />
        </button>
        <button class="icon-btn" title="复制结果" @click="copyResult">
          <Table2 :size="13" />
        </button>
      </template>
    </div>
    <div v-if="running" class="grid flex-1 place-items-center gap-2 text-sm text-muted">
      <span class="h-5 w-5 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      正在执行 SQL
    </div>
    <div v-else-if="error" class="m-3 rounded-lg bg-red-50 p-4 text-sm text-danger" role="alert">
      <p class="font-medium">执行失败</p>
      <pre class="mt-2 whitespace-pre-wrap font-mono text-xs">{{ error }}</pre>
    </div>
    <div v-else-if="!result" class="grid flex-1 place-items-center text-sm text-muted">
      运行当前语句后在这里查看结果
      <span class="mt-1 text-xs">⌘/Ctrl + Enter</span>
    </div>
    <div
      v-else-if="result.kind !== 'RESULT_SET'"
      class="m-3 rounded-lg border border-line bg-slate-50 p-4"
    >
      <p class="font-medium">{{ result.message || '执行成功' }}</p>
      <p class="mt-1 text-sm text-muted">{{ summary }}</p>
    </div>
    <div v-else ref="scrollEl" class="min-h-0 flex-1 overflow-auto" @scroll.passive="onScroll">
      <div
        class="sticky top-0 z-10 flex min-w-max bg-slate-50 text-xs font-semibold text-slate-600"
      >
        <div class="w-12 shrink-0 border-b border-r border-line px-2 py-2 text-right">#</div>
        <div
          v-for="column in result.columns"
          :key="column.label"
          class="w-48 shrink-0 border-b border-r border-line px-3 py-2"
          :title="`${column.jdbcType} / ${column.typeName}`"
        >
          {{ column.label }}
        </div>
      </div>
      <div :style="{ height: `${filtered.length * rowHeight}px` }" class="relative min-w-max">
        <div
          :style="{ transform: `translateY(${start * rowHeight}px)` }"
          class="absolute left-0 top-0"
        >
          <div
            v-for="item in visible"
            :key="item.index"
            class="flex h-8"
            :class="selected?.row === item.index ? 'bg-teal-50' : 'hover:bg-slate-50'"
          >
            <div
              class="w-12 shrink-0 border-b border-r border-line px-2 text-right text-[11px] text-muted"
            >
              {{ item.index + 1 }}
            </div>
            <button
              v-for="(cell, col) in item.row"
              :key="col"
              class="w-48 shrink-0 truncate border-b border-r border-line px-3 text-left font-mono text-xs"
              :class="{
                'italic text-muted': cell === null,
                'bg-teal-100': selected?.row === item.index && selected?.col === col,
              }"
              :title="cell === '' ? '空字符串' : display(cell)"
              @click="selected = { row: item.index, col }"
              @dblclick="
                isLong(cell) ? openCell(cell, result.columns[col]?.label || '') : copyCell()
              "
            >
              <span v-if="cell === ''">&nbsp;</span>
              <span v-else>{{ preview(cell) }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
    <div
      v-if="detail"
      class="fixed inset-0 z-40 grid place-items-center bg-slate-950/40 p-6"
      @click.self="detail = null"
    >
      <section
        class="max-h-[70vh] w-full max-w-2xl overflow-auto rounded-xl bg-white p-5 shadow-2xl"
      >
        <div class="mb-3 flex items-center justify-between">
          <strong>{{ detail.title }}</strong>
          <button class="icon-btn" aria-label="关闭" @click="detail = null">×</button>
        </div>
        <pre class="whitespace-pre-wrap break-all font-mono text-xs">{{ detail.text }}</pre>
      </section>
    </div>
  </section>
</template>
