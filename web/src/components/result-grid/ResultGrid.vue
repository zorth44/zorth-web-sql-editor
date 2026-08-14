<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowDown,
  ArrowUp,
  ChevronRight,
  Copy,
  Download,
  Filter,
  Menu,
  PanelRight,
  Pin,
  Table2,
  X,
} from 'lucide-vue-next'
import type { SqlCellValue, SqlColumn, SqlExecutionResult } from '@/types/contracts'
import { cellMatches, compareCells, displayCell, previewCell } from './cell-value'
import { columnTypeGlyph, columnTypeKind, defaultColumnWidth } from './column-type'
import { clampRowLimit, DEFAULT_ROW_LIMIT, MAX_ROW_LIMIT, MIN_ROW_LIMIT } from './limits'

const INDEX_WIDTH = 44
const ROW_HEIGHT = 28
const HEADER_HEIGHT = 30

type ColumnFilter = { mode: 'contains' | 'equals'; value: string } | { mode: 'null' | 'not-null' }
type SortState = { col: number; dir: 'asc' | 'desc' }
type MenuState = {
  x: number
  y: number
  row: number | null
  col: number
  submenu: 'sort' | 'filter' | null
}

const props = defineProps<{
  result: SqlExecutionResult | null
  error: string | null
  running?: boolean
  canExport?: boolean
  exporting?: boolean
  rowLimit?: number
}>()
const emit = defineEmits<{
  export: []
  'cancel-export': []
  'update:rowLimit': [value: number]
}>()

const filter = ref('')
const columnFilters = ref<Record<number, ColumnFilter>>({})
const sort = ref<SortState | null>(null)
const pinned = ref<number[]>([])
const widths = ref<Record<number, number>>({})
const scrollEl = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewport = ref(280)
const selected = ref<{ row: number; col: number } | null>(null)
const detail = ref<{ title: string; text: string } | null>(null)
const menu = ref<MenuState | null>(null)
const filterDraft = ref('')
const limitDraft = ref(String(props.rowLimit ?? DEFAULT_ROW_LIMIT))
const copied = ref('')
let copyTimer = 0
let observer: ResizeObserver | undefined

const columns = computed(() =>
  props.result?.kind === 'RESULT_SET' ? props.result.columns : ([] as SqlColumn[]),
)
const rows = computed(() => (props.result?.kind === 'RESULT_SET' ? props.result.rows : []))
const visualColumns = computed(() => {
  const pinnedSet = new Set(pinned.value)
  const pinnedCols = pinned.value.flatMap((index) => {
    const column = columns.value[index]
    return column ? [{ index, column, pinned: true as const }] : []
  })
  const rest = columns.value.flatMap((column, index) =>
    pinnedSet.has(index) ? [] : [{ index, column, pinned: false as const }],
  )
  return [...pinnedCols, ...rest]
})
const indexedRows = computed(() => rows.value.map((row, index) => ({ row, index })))
const filtered = computed(() => {
  const q = filter.value.trim().toLowerCase()
  return indexedRows.value.filter(({ row }) => {
    if (q && !row.some((cell) => displayCell(cell).toLowerCase().includes(q))) return false
    return Object.entries(columnFilters.value).every(([key, rule]) => {
      const cell = row[Number(key)]
      if (rule.mode === 'contains' || rule.mode === 'equals') {
        return cellMatches(cell, rule.value, rule.mode)
      }
      return rule.mode === 'null' ? cell === null : cell !== null
    })
  })
})
const ordered = computed(() => {
  if (!sort.value) return filtered.value
  const { col, dir } = sort.value
  const copy = [...filtered.value]
  copy.sort((a, b) => {
    const delta = compareCells(a.row[col], b.row[col])
    return dir === 'asc' ? delta : -delta
  })
  return copy
})
const start = computed(() => Math.max(0, Math.floor(scrollTop.value / ROW_HEIGHT) - 8))
const end = computed(() =>
  Math.min(ordered.value.length, start.value + Math.ceil(viewport.value / ROW_HEIGHT) + 16),
)
const visible = computed(() => ordered.value.slice(start.value, end.value))
const tableMinWidth = computed(
  () => INDEX_WIDTH + visualColumns.value.reduce((sum, item) => sum + widthOf(item.index), 0),
)
const summary = computed(() => {
  const result = props.result
  if (!result) return ''
  if (result.kind === 'RESULT_SET') {
    const shown = ordered.value.length
    const count = result.rowCount.toLocaleString()
    const visibleCount = shown === result.rowCount ? count : `${shown.toLocaleString()} / ${count}`
    const trunc = result.truncated ? ' · 已截断' : ''
    return `${visibleCount} 行${trunc} · ${result.durationMs} ms`
  }
  if (result.affectedRows !== null) {
    return `影响 ${result.affectedRows.toLocaleString()} 行 · ${result.durationMs} ms`
  }
  return `${result.durationMs} ms`
})

function widthOf(index: number): number {
  return widths.value[index] ?? defaultColumnWidth(columns.value[index]?.label || '')
}
function pinnedLeft(colIndex: number): number {
  let left = INDEX_WIDTH
  for (const item of visualColumns.value) {
    if (item.index === colIndex) return left
    if (item.pinned) left += widthOf(item.index)
  }
  return left
}
function resetView(): void {
  selected.value = null
  filter.value = ''
  columnFilters.value = {}
  sort.value = null
  pinned.value = []
  widths.value = {}
  detail.value = null
  menu.value = null
  scrollTop.value = 0
  if (scrollEl.value) scrollEl.value.scrollTop = 0
}
function closeMenu(): void {
  menu.value = null
}
function onDocumentClick(event: MouseEvent): void {
  const target = event.target
  if (
    target instanceof Element &&
    (target.closest('.result-ctx') || target.closest('.result-cell-menu'))
  ) {
    return
  }
  closeMenu()
}
function commitLimit(): void {
  const next = clampRowLimit(Number(limitDraft.value))
  limitDraft.value = String(next)
  if (next !== (props.rowLimit ?? DEFAULT_ROW_LIMIT)) emit('update:rowLimit', next)
}
async function copy(text: string, label = '已复制'): Promise<void> {
  await navigator.clipboard.writeText(text)
  copied.value = label
  window.clearTimeout(copyTimer)
  copyTimer = window.setTimeout(() => (copied.value = ''), 1600)
}
function cellAt(row: number, col: number): SqlCellValue {
  return rows.value[row]?.[col]
}
async function copyCell(): Promise<void> {
  if (!selected.value) return
  await copy(displayCell(cellAt(selected.value.row, selected.value.col)), '单元格已复制')
}
async function copyRow(): Promise<void> {
  if (selected.value == null) return
  await copy((rows.value[selected.value.row] || []).map(displayCell).join('\t'), '整行已复制')
}
async function copyResult(): Promise<void> {
  if (props.result?.kind !== 'RESULT_SET') return
  const lines = [
    props.result.columns.map((column) => column.label).join('\t'),
    ...props.result.rows.map((row) => row.map(displayCell).join('\t')),
  ]
  await copy(lines.join('\n'), '结果已复制')
}
function openValue(row: number, col: number): void {
  const column = columns.value[col]
  detail.value = {
    title: column?.label || '',
    text: displayCell(cellAt(row, col)),
  }
  closeMenu()
}
function cycleSort(col: number): void {
  closeMenu()
  if (sort.value?.col === col && sort.value.dir === 'asc') sort.value = { col, dir: 'desc' }
  else if (sort.value?.col === col && sort.value.dir === 'desc') sort.value = null
  else sort.value = { col, dir: 'asc' }
}
function setSort(col: number, dir: 'asc' | 'desc'): void {
  sort.value = { col, dir }
  closeMenu()
}
function clearSort(): void {
  sort.value = null
  closeMenu()
}
function pinColumn(col: number): void {
  if (!pinned.value.includes(col)) pinned.value = [...pinned.value, col]
  closeMenu()
}
function unpinAll(): void {
  pinned.value = []
  closeMenu()
}
function filterText(col: number): string {
  const rule = columnFilters.value[col]
  return rule && (rule.mode === 'contains' || rule.mode === 'equals') ? rule.value : ''
}
function applyFilter(col: number, rule: ColumnFilter | null): void {
  const next = { ...columnFilters.value }
  if (!rule || ((rule.mode === 'contains' || rule.mode === 'equals') && !rule.value.trim())) {
    delete next[col]
  } else {
    next[col] = rule
  }
  columnFilters.value = next
  closeMenu()
}
function openMenuValue(): void {
  if (menu.value?.row == null) return
  openValue(menu.value.row, menu.value.col)
}
function openMenu(event: MouseEvent, row: number | null, col: number): void {
  event.preventDefault()
  event.stopPropagation()
  if (row != null) selected.value = { row, col }
  filterDraft.value = filterText(col)
  menu.value = {
    x: Math.min(event.clientX, window.innerWidth - 248),
    y: Math.min(event.clientY, window.innerHeight - 280),
    row,
    col,
    submenu: null,
  }
}
function startResize(index: number, event: MouseEvent): void {
  event.preventDefault()
  event.stopPropagation()
  const origin = event.clientX
  const startWidth = widthOf(index)
  const move = (ev: MouseEvent) => {
    widths.value = { ...widths.value, [index]: Math.max(72, startWidth + ev.clientX - origin) }
  }
  const up = () => {
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', up)
  }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}
function selectCell(row: number, col: number): void {
  selected.value = { row, col }
  closeMenu()
}
function onScroll(): void {
  scrollTop.value = scrollEl.value?.scrollTop || 0
  viewport.value = scrollEl.value?.clientHeight || viewport.value
}
function ensureVisible(filteredIndex: number): void {
  const el = scrollEl.value
  if (!el) return
  const top = filteredIndex * ROW_HEIGHT
  if (top < el.scrollTop) el.scrollTop = top
  else if (top + ROW_HEIGHT > el.scrollTop + el.clientHeight - HEADER_HEIGHT) {
    el.scrollTop = top - el.clientHeight + HEADER_HEIGHT + ROW_HEIGHT
  }
}
function moveSelection(dRow: number, dCol: number): void {
  if (!columns.value.length || !ordered.value.length) return
  const pos = Math.max(
    0,
    ordered.value.findIndex((item) => item.index === selected.value?.row),
  )
  const nextPos = Math.min(ordered.value.length - 1, Math.max(0, pos + dRow))
  const col = Math.min(columns.value.length - 1, Math.max(0, (selected.value?.col ?? 0) + dCol))
  const next = ordered.value[nextPos]
  if (!next) return
  selected.value = { row: next.index, col }
  ensureVisible(nextPos)
}
function onGridKeydown(event: KeyboardEvent): void {
  const target = event.target
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) return
  if (event.key === 'Escape') {
    closeMenu()
    detail.value = null
    return
  }
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'c') {
    event.preventDefault()
    void copyCell()
    return
  }
  if (event.key === 'Enter' && selected.value) {
    event.preventDefault()
    openValue(selected.value.row, selected.value.col)
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveSelection(1, 0)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveSelection(-1, 0)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    moveSelection(0, 1)
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    moveSelection(0, -1)
  }
}

watch(
  () => [props.result, props.error],
  () => resetView(),
)
watch(
  () => props.rowLimit,
  (value) => {
    limitDraft.value = String(value ?? DEFAULT_ROW_LIMIT)
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
onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => {
  observer?.disconnect()
  window.clearTimeout(copyTimer)
  document.removeEventListener('click', onDocumentClick)
})
</script>
<template>
  <section class="result-pane" data-testid="result-pane" @keydown="onGridKeydown">
    <div v-if="running" class="grid flex-1 place-items-center gap-2 text-sm text-muted">
      <span class="h-5 w-5 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      正在执行 SQL
    </div>
    <div
      v-else-if="error"
      class="m-3 rounded-lg bg-danger-soft p-4 text-sm text-danger"
      role="alert"
    >
      <p class="font-medium">执行失败</p>
      <pre class="mt-2 whitespace-pre-wrap font-mono text-xs">{{ error }}</pre>
    </div>
    <div v-else-if="!result" class="grid flex-1 place-items-center text-sm text-muted">
      运行当前语句后在这里查看结果
      <span class="mt-1 text-xs">⌘/Ctrl + Enter</span>
    </div>
    <div
      v-else-if="result.kind !== 'RESULT_SET'"
      class="m-3 rounded-lg border border-line bg-subtle p-4"
    >
      <p class="font-medium">{{ result.message || '执行成功' }}</p>
      <p class="mt-1 text-sm text-muted">{{ summary }}</p>
    </div>
    <div v-else class="result-body">
      <div
        ref="scrollEl"
        class="result-scroll"
        role="grid"
        aria-label="查询结果"
        tabindex="0"
        @scroll.passive="onScroll"
      >
        <div class="result-table" :style="{ minWidth: `${tableMinWidth}px` }">
          <div class="result-head" role="row" :style="{ height: `${HEADER_HEIGHT}px` }">
            <div class="result-index result-index-head" role="columnheader">#</div>
            <div
              v-for="item in visualColumns"
              :key="`${item.index}:${item.column.name}`"
              class="result-header"
              :class="{ 'result-col-pinned': item.pinned }"
              role="columnheader"
              :style="{
                width: `${widthOf(item.index)}px`,
                left: item.pinned ? `${pinnedLeft(item.index)}px` : undefined,
              }"
              :title="`${item.column.jdbcType} / ${item.column.typeName}`"
              :data-testid="`result-header-${item.index}`"
              @click="cycleSort(item.index)"
              @contextmenu="openMenu($event, null, item.index)"
            >
              <span
                class="result-type"
                :data-kind="columnTypeKind(item.column.jdbcType)"
                :title="item.column.jdbcType"
              >
                {{ columnTypeGlyph(columnTypeKind(item.column.jdbcType)) }}
              </span>
              <span class="result-header-label">{{ item.column.label }}</span>
              <ArrowUp
                v-if="sort?.col === item.index && sort.dir === 'asc'"
                class="result-header-icon"
                :size="11"
              />
              <ArrowDown
                v-else-if="sort?.col === item.index && sort.dir === 'desc'"
                class="result-header-icon"
                :size="11"
              />
              <Filter v-if="columnFilters[item.index]" class="result-header-icon" :size="11" />
              <button
                class="result-resize"
                type="button"
                aria-label="调整列宽"
                @click.stop
                @mousedown="startResize(item.index, $event)"
              />
            </div>
          </div>
          <div class="result-rows" :style="{ height: `${ordered.length * ROW_HEIGHT}px` }">
            <div
              :style="{ transform: `translateY(${start * ROW_HEIGHT}px)` }"
              class="result-window"
            >
              <div
                v-for="item in visible"
                :key="item.index"
                class="result-row"
                :class="{ 'result-row-selected': selected?.row === item.index }"
                role="row"
                :style="{ height: `${ROW_HEIGHT}px` }"
              >
                <div class="result-index">{{ item.index + 1 }}</div>
                <div
                  v-for="col in visualColumns"
                  :key="col.index"
                  class="result-cell"
                  :class="{
                    'result-cell-active':
                      selected?.row === item.index && selected?.col === col.index,
                    'result-cell-null': item.row[col.index] === null,
                    'result-col-pinned': col.pinned,
                  }"
                  role="gridcell"
                  :tabindex="selected?.row === item.index && selected?.col === col.index ? 0 : -1"
                  :style="{
                    width: `${widthOf(col.index)}px`,
                    left: col.pinned ? `${pinnedLeft(col.index)}px` : undefined,
                  }"
                  :title="
                    item.row[col.index] === '' ? '空字符串' : displayCell(item.row[col.index])
                  "
                  :data-testid="`result-cell-${item.index}-${col.index}`"
                  @click="selectCell(item.index, col.index)"
                  @dblclick="openValue(item.index, col.index)"
                  @contextmenu="openMenu($event, item.index, col.index)"
                >
                  <span v-if="item.row[col.index] === ''">&nbsp;</span>
                  <span v-else>{{ previewCell(item.row[col.index]) }}</span>
                  <button
                    v-if="selected?.row === item.index && selected?.col === col.index"
                    class="result-cell-menu"
                    type="button"
                    title="单元格操作"
                    aria-label="单元格操作"
                    data-testid="result-cell-menu"
                    @click.stop="openMenu($event, item.index, col.index)"
                  >
                    <Menu :size="12" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <aside v-if="detail" class="result-value-panel" data-testid="result-value-panel">
        <div class="result-value-head">
          <strong class="truncate">{{ detail.title }}</strong>
          <button class="icon-btn" title="复制" aria-label="复制值" @click="copy(detail.text)">
            <Copy :size="13" />
          </button>
          <button class="icon-btn" title="关闭" aria-label="关闭值面板" @click="detail = null">
            <X :size="13" />
          </button>
        </div>
        <pre class="result-value-body">{{ detail.text }}</pre>
      </aside>
    </div>
    <footer class="result-footer" data-testid="result-footer">
      <input
        v-model="limitDraft"
        class="result-limit"
        data-testid="result-limit"
        title="查询返回行数上限，下次运行生效"
        aria-label="查询返回行数上限"
        :min="MIN_ROW_LIMIT"
        :max="MAX_ROW_LIMIT"
        inputmode="numeric"
        @keydown.enter="commitLimit"
        @blur="commitLimit"
      />
      <span v-if="running" class="text-brand">正在执行…</span>
      <span v-else-if="error" class="text-danger">执行失败</span>
      <span v-else-if="result || error" class="truncate">{{ summary }}</span>
      <span v-if="copied" class="text-success">{{ copied }}</span>
      <span class="ml-auto" />
      <template v-if="result?.kind === 'RESULT_SET'">
        <input
          v-model="filter"
          class="result-quick-filter"
          placeholder="过滤已返回结果"
          aria-label="过滤已返回结果"
        />
        <button class="icon-btn" title="复制结果" aria-label="复制结果" @click="copyResult">
          <Table2 :size="13" />
        </button>
        <button
          v-if="!exporting"
          class="result-export"
          :disabled="!canExport"
          data-testid="result-export"
          @click="emit('export')"
        >
          <Download :size="13" />导出
        </button>
        <button
          v-else
          class="result-export result-export-cancel"
          data-testid="result-export-cancel"
          @click="emit('cancel-export')"
        >
          取消导出
        </button>
      </template>
    </footer>
    <Teleport to="body">
      <div
        v-if="menu"
        class="result-ctx"
        data-testid="result-context-menu"
        :style="{ left: `${menu.x}px`, top: `${menu.y}px` }"
        @click.stop
      >
        <button v-if="menu.row != null" class="result-ctx-item" @click="openMenuValue">
          <PanelRight :size="14" />在值面板中显示
        </button>
        <button v-if="menu.row != null" class="result-ctx-item" @click="copyCell">
          <Copy :size="14" />复制单元格
        </button>
        <button v-if="menu.row != null" class="result-ctx-item" @click="copyRow">
          <Copy :size="14" />复制整行
        </button>
        <div class="result-ctx-item result-ctx-parent" @mouseenter="menu.submenu = 'sort'">
          <ArrowUp :size="14" />排序
          <ChevronRight :size="12" class="ml-auto text-muted" />
          <div v-if="menu.submenu === 'sort'" class="result-ctx-sub">
            <button class="result-ctx-item" @click="setSort(menu.col, 'asc')">升序</button>
            <button class="result-ctx-item" @click="setSort(menu.col, 'desc')">降序</button>
            <button class="result-ctx-item" @click="clearSort">取消排序</button>
          </div>
        </div>
        <div class="result-ctx-item result-ctx-parent" @mouseenter="menu.submenu = 'filter'">
          <Filter :size="14" />筛选
          <ChevronRight :size="12" class="ml-auto text-muted" />
          <div v-if="menu.submenu === 'filter'" class="result-ctx-sub">
            <input
              v-model="filterDraft"
              class="result-ctx-input"
              placeholder="包含…"
              @keydown.enter="applyFilter(menu.col, { mode: 'contains', value: filterDraft })"
            />
            <button
              class="result-ctx-item"
              @click="applyFilter(menu.col, { mode: 'contains', value: filterDraft })"
            >
              包含
            </button>
            <button
              class="result-ctx-item"
              @click="applyFilter(menu.col, { mode: 'equals', value: filterDraft })"
            >
              等于
            </button>
            <button class="result-ctx-item" @click="applyFilter(menu.col, { mode: 'null' })">
              为空
            </button>
            <button class="result-ctx-item" @click="applyFilter(menu.col, { mode: 'not-null' })">
              不为空
            </button>
            <button class="result-ctx-item" @click="applyFilter(menu.col, null)">清除筛选</button>
          </div>
        </div>
        <button class="result-ctx-item" @click="pinColumn(menu.col)">
          <Pin :size="14" />固定列
        </button>
        <button class="result-ctx-item" :disabled="!pinned.length" @click="unpinAll">
          取消所有固定
        </button>
      </div>
    </Teleport>
  </section>
</template>
