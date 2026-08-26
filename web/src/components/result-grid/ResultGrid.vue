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
import {
  HEADER_HEIGHT,
  INDEX_WIDTH,
  ROW_HEIGHT,
  clampSelection,
  dragFocus,
  extendSelection,
  hitTest,
  inRect,
  rectOf,
  selectCell,
  selectColumns,
  selectRows,
  selectionFromDrag,
  selectionTsv,
  type DragMode,
  type GridPoint,
  type GridSelection,
  type HitTestLayout,
} from './selection'

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
  canFixWithAi?: boolean
  fixDisabled?: boolean
}>()
const emit = defineEmits<{
  export: []
  'cancel-export': []
  'update:rowLimit': [value: number]
  'fix-with-ai': []
}>()

const filter = ref('')
const columnFilters = ref<Record<number, ColumnFilter>>({})
const sort = ref<SortState | null>(null)
const pinned = ref<number[]>([])
const widths = ref<Record<number, number>>({})
const scrollEl = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewport = ref(280)
const selection = ref<GridSelection | null>(null)
const hover = ref<GridPoint | null>(null)
const detail = ref<{ title: string; text: string } | null>(null)
const menu = ref<MenuState | null>(null)
const filterDraft = ref('')
const limitDraft = ref(String(props.rowLimit ?? DEFAULT_ROW_LIMIT))
const copied = ref('')
let copyTimer = 0
let observer: ResizeObserver | undefined
let dragging = false
let dragMode: DragMode | null = null

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
const selectedRect = computed(() => (selection.value ? rectOf(selection.value) : null))
const visualColumnIndexes = computed(() => visualColumns.value.map((item) => item.index))
const displayedRows = computed(() => ordered.value.map((item) => item.row))
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
  selection.value = null
  hover.value = null
  dragging = false
  dragMode = null
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
function focusData(): { row: number; col: number } | null {
  const current = selection.value
  if (!current) return null
  const item = ordered.value[current.focus.row]
  const visual = visualColumns.value[current.focus.col]
  if (!item || !visual) return null
  return { row: item.index, col: visual.index }
}
async function copySelection(): Promise<void> {
  if (!selection.value) return
  const text = selectionTsv(selection.value, displayedRows.value, visualColumnIndexes.value)
  const rect = rectOf(selection.value)
  const count = (rect.rowEnd - rect.rowStart + 1) * (rect.colEnd - rect.colStart + 1)
  await copy(text, count === 1 ? '单元格已复制' : '选区已复制')
  closeMenu()
}
async function copyRow(): Promise<void> {
  const row = menu.value?.row ?? focusData()?.row
  if (row == null) return
  await copy((rows.value[row] || []).map(displayCell).join('\t'), '整行已复制')
  closeMenu()
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
  if (row != null) {
    const displayedRow = ordered.value.findIndex((item) => item.index === row)
    const visualCol = visualColumns.value.findIndex((item) => item.index === col)
    const rect = selectedRect.value
    if (displayedRow < 0 || visualCol < 0 || !rect || !inRect(rect, displayedRow, visualCol)) {
      if (displayedRow >= 0 && visualCol >= 0) selection.value = selectCell(displayedRow, visualCol)
    }
  }
  filterDraft.value = filterText(col)
  menu.value = {
    x: Math.min(event.clientX, window.innerWidth - 248),
    y: Math.min(event.clientY, window.innerHeight - 280),
    row,
    col,
    submenu: null,
  }
}
function currentLayout(): HitTestLayout {
  const el = scrollEl.value
  return {
    indexWidth: INDEX_WIDTH,
    headerHeight: HEADER_HEIGHT,
    rowHeight: ROW_HEIGHT,
    columnWidths: visualColumns.value.map((item) => widthOf(item.index)),
    pinnedCount: visualColumns.value.filter((item) => item.pinned).length,
    rowCount: ordered.value.length,
    colCount: visualColumns.value.length,
    scrollLeft: el?.scrollLeft || 0,
    scrollTop: el?.scrollTop || 0,
    viewportWidth: el?.clientWidth || 0,
    viewportHeight: el?.clientHeight || 0,
  }
}
function localPoint(event: PointerEvent): { x: number; y: number } | null {
  const el = scrollEl.value
  if (!el) return null
  const rect = el.getBoundingClientRect()
  return { x: event.clientX - rect.left, y: event.clientY - rect.top }
}
function ignoreGridPointer(event: PointerEvent): boolean {
  if (event.button !== 0) return true
  const target = event.target
  return (
    target instanceof Element &&
    Boolean(
      target.closest(
        '.result-resize, .result-cell-menu, .result-type, .result-sort-icon, input, textarea',
      ),
    )
  )
}
function applyHitSelection(
  event: PointerEvent,
  hitRow: number,
  hitCol: number,
  mode: DragMode,
): void {
  const rowCount = ordered.value.length
  const colCount = visualColumns.value.length
  if (!rowCount || !colCount) return
  const focus = { row: Math.max(0, hitRow), col: Math.max(0, hitCol) }
  if (event.shiftKey && selection.value) {
    selection.value = selectionFromDrag(mode, selection.value.anchor, focus, rowCount, colCount)
    return
  }
  if (mode === 'row') selection.value = selectRows(focus.row, focus.row, colCount)
  else if (mode === 'column') selection.value = selectColumns(focus.col, focus.col, rowCount)
  else selection.value = selectCell(focus.row, focus.col)
}
function onGridPointerDown(event: PointerEvent): void {
  if (ignoreGridPointer(event)) return
  const point = localPoint(event)
  if (!point) return
  const hit = hitTest(point.x, point.y, currentLayout())
  if (hit.region === 'outside' || hit.region === 'corner') return
  event.preventDefault()
  closeMenu()
  const target = event.currentTarget
  if (target instanceof HTMLElement && typeof target.setPointerCapture === 'function') {
    try {
      target.setPointerCapture(event.pointerId)
    } catch {
      /* jsdom */
    }
  }
  dragMode = hit.region === 'row-number' ? 'row' : hit.region === 'header' ? 'column' : 'cell'
  dragging = true
  applyHitSelection(event, hit.row, hit.col, dragMode)
}
function onGridPointerMove(event: PointerEvent): void {
  const point = localPoint(event)
  if (!point) return
  const layout = currentLayout()
  const hit = hitTest(point.x, point.y, layout)
  if (!dragging || !dragMode) {
    hover.value =
      hit.region === 'cell'
        ? { row: hit.row, col: hit.col }
        : hit.region === 'row-number'
          ? { row: hit.row, col: -1 }
          : null
    return
  }
  const rowCount = ordered.value.length
  const colCount = visualColumns.value.length
  const current = selection.value
  if (!current || !rowCount || !colCount) return
  const focus = dragFocus(dragMode, hit, current.focus, rowCount, colCount)
  selection.value = selectionFromDrag(dragMode, current.anchor, focus, rowCount, colCount)
}
function onGridPointerUp(): void {
  dragging = false
  dragMode = null
}
function onGridPointerLeave(): void {
  if (!dragging) hover.value = null
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
function moveSelection(dRow: number, dCol: number, extend = false): void {
  const rowCount = ordered.value.length
  const colCount = visualColumns.value.length
  if (!rowCount || !colCount) return
  const current = selection.value ?? selectCell(0, 0)
  const focus = {
    row: Math.min(rowCount - 1, Math.max(0, current.focus.row + dRow)),
    col: Math.min(colCount - 1, Math.max(0, current.focus.col + dCol)),
  }
  selection.value = extend ? extendSelection(current, focus) : selectCell(focus.row, focus.col)
  ensureVisible(focus.row)
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
    void copySelection()
    return
  }
  const focused = focusData()
  if (event.key === 'Enter' && focused) {
    event.preventDefault()
    openValue(focused.row, focused.col)
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveSelection(1, 0, event.shiftKey)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveSelection(-1, 0, event.shiftKey)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    moveSelection(0, 1, event.shiftKey)
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    moveSelection(0, -1, event.shiftKey)
  }
}

watch(
  () => [props.result, props.error],
  () => resetView(),
)
watch(
  () => [ordered.value.length, visualColumns.value.length],
  () => {
    if (!selection.value) return
    selection.value = clampSelection(
      selection.value,
      ordered.value.length,
      visualColumns.value.length,
    )
  },
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
      <div class="flex items-start justify-between gap-2">
        <p class="font-medium">执行失败</p>
        <button
          v-if="canFixWithAi"
          class="copilot-fix"
          type="button"
          :disabled="Boolean(fixDisabled)"
          data-testid="copilot-fix"
          @click="emit('fix-with-ai')"
        >
          用 AI 修复
        </button>
      </div>
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
        data-testid="result-scroll"
        role="grid"
        aria-label="查询结果"
        tabindex="0"
        @scroll.passive="onScroll"
        @pointerdown="onGridPointerDown"
        @pointermove="onGridPointerMove"
        @pointerup="onGridPointerUp"
        @pointercancel="onGridPointerUp"
        @lostpointercapture="onGridPointerUp"
        @pointerleave="onGridPointerLeave"
      >
        <div class="result-table" :style="{ minWidth: `${tableMinWidth}px` }">
          <div class="result-head" role="row" :style="{ height: `${HEADER_HEIGHT}px` }">
            <div class="result-index result-index-head" role="columnheader">#</div>
            <div
              v-for="(item, visualCol) in visualColumns"
              :key="`${item.index}:${item.column.name}`"
              class="result-header"
              :class="{
                'result-col-pinned': item.pinned,
                'result-header-selected':
                  selectedRect &&
                  visualCol >= selectedRect.colStart &&
                  visualCol <= selectedRect.colEnd,
              }"
              role="columnheader"
              :style="{
                width: `${widthOf(item.index)}px`,
                left: item.pinned ? `${pinnedLeft(item.index)}px` : undefined,
              }"
              :title="`${item.column.jdbcType} / ${item.column.typeName} · 单击选列，类型标记排序`"
              :data-testid="`result-header-${item.index}`"
              @contextmenu="openMenu($event, null, item.index)"
            >
              <span
                class="result-type"
                :data-kind="columnTypeKind(item.column.jdbcType)"
                :title="`排序 · ${item.column.jdbcType}`"
                :data-testid="`result-sort-glyph-${item.index}`"
                @pointerdown.stop
                @click.stop="cycleSort(item.index)"
              >
                {{ columnTypeGlyph(columnTypeKind(item.column.jdbcType)) }}
              </span>
              <span class="result-header-label">{{ item.column.label }}</span>
              <ArrowUp
                v-if="sort?.col === item.index && sort.dir === 'asc'"
                class="result-header-icon result-sort-icon"
                :size="11"
                data-testid="result-sort-icon"
                @pointerdown.stop
                @click.stop="cycleSort(item.index)"
              />
              <ArrowDown
                v-else-if="sort?.col === item.index && sort.dir === 'desc'"
                class="result-header-icon result-sort-icon"
                :size="11"
                data-testid="result-sort-icon"
                @pointerdown.stop
                @click.stop="cycleSort(item.index)"
              />
              <Filter v-if="columnFilters[item.index]" class="result-header-icon" :size="11" />
              <button
                class="result-resize"
                type="button"
                aria-label="调整列宽"
                @click.stop
                @pointerdown.stop
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
                v-for="(item, offset) in visible"
                :key="item.index"
                class="result-row"
                :class="{
                  'result-row-hover': hover?.row === start + offset,
                  'result-row-in-selection':
                    selectedRect &&
                    start + offset >= selectedRect.rowStart &&
                    start + offset <= selectedRect.rowEnd,
                }"
                role="row"
                :style="{ height: `${ROW_HEIGHT}px` }"
              >
                <div class="result-index" :data-testid="`result-row-number-${item.index}`">
                  {{ item.index + 1 }}
                </div>
                <div
                  v-for="(col, visualCol) in visualColumns"
                  :key="col.index"
                  class="result-cell"
                  :class="{
                    'result-cell-hover': hover?.row === start + offset && hover?.col === visualCol,
                    'result-cell-selected':
                      selectedRect && inRect(selectedRect, start + offset, visualCol),
                    'result-cell-active':
                      selection?.focus.row === start + offset && selection?.focus.col === visualCol,
                    'result-cell-null': item.row[col.index] === null,
                    'result-col-pinned': col.pinned,
                  }"
                  role="gridcell"
                  :tabindex="
                    selection?.focus.row === start + offset && selection?.focus.col === visualCol
                      ? 0
                      : -1
                  "
                  :style="{
                    width: `${widthOf(col.index)}px`,
                    left: col.pinned ? `${pinnedLeft(col.index)}px` : undefined,
                  }"
                  :title="
                    item.row[col.index] === '' ? '空字符串' : displayCell(item.row[col.index])
                  "
                  :data-testid="`result-cell-${item.index}-${col.index}`"
                  @dblclick="openValue(item.index, col.index)"
                  @contextmenu="openMenu($event, item.index, col.index)"
                >
                  <span v-if="item.row[col.index] === ''">&nbsp;</span>
                  <span v-else>{{ previewCell(item.row[col.index]) }}</span>
                  <button
                    v-if="
                      selection?.focus.row === start + offset && selection?.focus.col === visualCol
                    "
                    class="result-cell-menu"
                    type="button"
                    title="单元格操作"
                    aria-label="单元格操作"
                    data-testid="result-cell-menu"
                    @pointerdown.stop
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
      <span v-if="running" class="shrink-0 text-brand">正在执行…</span>
      <span v-else-if="error" class="shrink-0 text-danger">执行失败</span>
      <span v-else-if="result || error" class="shrink-0">{{ summary }}</span>
      <span v-if="copied" class="shrink-0 text-success">{{ copied }}</span>
      <div v-if="$slots.status" class="result-footer-meta" data-testid="result-footer-status">
        <slot name="status" />
      </div>
      <span class="result-footer-spacer" />
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
        <button v-if="selection" class="result-ctx-item" @click="copySelection">
          <Copy :size="14" />复制选区
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
