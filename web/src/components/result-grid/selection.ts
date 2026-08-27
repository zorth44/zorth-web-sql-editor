import type { SqlCellValue } from '@/types/contracts'
import { displayCell } from './cell-value'

export const INDEX_WIDTH = 44
export const ROW_HEIGHT = 28
export const HEADER_HEIGHT = 30
export const FILTER_ROW_HEIGHT = 28

export type GridPoint = { row: number; col: number }
export type GridSelection = { anchor: GridPoint; focus: GridPoint }
export type GridRect = { rowStart: number; rowEnd: number; colStart: number; colEnd: number }
export type DragMode = 'cell' | 'row' | 'column'
export type HitRegion = 'cell' | 'row-number' | 'header' | 'filter' | 'corner' | 'outside'

export interface GridHit {
  region: HitRegion
  row: number
  col: number
}

export interface HitTestLayout {
  indexWidth: number
  headerHeight: number
  filterRowHeight?: number
  rowHeight: number
  columnWidths: number[]
  pinnedCount: number
  rowCount: number
  colCount: number
  scrollLeft: number
  scrollTop: number
  viewportWidth: number
  viewportHeight: number
}

export function rectOf(selection: GridSelection): GridRect {
  return {
    rowStart: Math.min(selection.anchor.row, selection.focus.row),
    rowEnd: Math.max(selection.anchor.row, selection.focus.row),
    colStart: Math.min(selection.anchor.col, selection.focus.col),
    colEnd: Math.max(selection.anchor.col, selection.focus.col),
  }
}

export function inRect(rect: GridRect, row: number, col: number): boolean {
  return row >= rect.rowStart && row <= rect.rowEnd && col >= rect.colStart && col <= rect.colEnd
}

export function selectCell(row: number, col: number): GridSelection {
  return { anchor: { row, col }, focus: { row, col } }
}

export function extendSelection(selection: GridSelection, focus: GridPoint): GridSelection {
  return { anchor: selection.anchor, focus }
}

export function selectRows(fromRow: number, toRow: number, colCount: number): GridSelection {
  return {
    anchor: { row: fromRow, col: 0 },
    focus: { row: toRow, col: Math.max(0, colCount - 1) },
  }
}

export function selectColumns(fromCol: number, toCol: number, rowCount: number): GridSelection {
  return {
    anchor: { row: 0, col: fromCol },
    focus: { row: Math.max(0, rowCount - 1), col: toCol },
  }
}

export function selectionFromDrag(
  mode: DragMode,
  anchor: GridPoint,
  focus: GridPoint,
  rowCount: number,
  colCount: number,
): GridSelection {
  if (mode === 'row') return selectRows(anchor.row, focus.row, colCount)
  if (mode === 'column') return selectColumns(anchor.col, focus.col, rowCount)
  return { anchor, focus }
}

export function clampSelection(
  selection: GridSelection,
  rowCount: number,
  colCount: number,
): GridSelection | null {
  if (rowCount <= 0 || colCount <= 0) return null
  const clamp = (value: number, max: number) => Math.min(max, Math.max(0, value))
  return {
    anchor: {
      row: clamp(selection.anchor.row, rowCount - 1),
      col: clamp(selection.anchor.col, colCount - 1),
    },
    focus: {
      row: clamp(selection.focus.row, rowCount - 1),
      col: clamp(selection.focus.col, colCount - 1),
    },
  }
}

export function hitTest(localX: number, localY: number, layout: HitTestLayout): GridHit {
  if (
    localX < 0 ||
    localY < 0 ||
    localX >= layout.viewportWidth ||
    localY >= layout.viewportHeight
  ) {
    return { region: 'outside', row: -1, col: -1 }
  }
  const filterRowHeight = layout.filterRowHeight ?? 0
  const labelHeight = layout.headerHeight - filterRowHeight
  const inHeaderBlock = localY < layout.headerHeight
  const inFilter = filterRowHeight > 0 && localY >= labelHeight && localY < layout.headerHeight
  const inHeader = inHeaderBlock && !inFilter
  const col = columnAt(localX, layout)
  const row = inHeaderBlock ? -1 : rowAt(localY, layout)
  if (localX < layout.indexWidth) {
    if (inFilter) return { region: 'filter', row: -1, col: -1 }
    if (inHeader) return { region: 'corner', row: -1, col: -1 }
    if (row < 0) return { region: 'outside', row: -1, col: -1 }
    return { region: 'row-number', row, col: -1 }
  }
  if (col < 0) return { region: 'outside', row: -1, col: -1 }
  if (inFilter) return { region: 'filter', row: -1, col }
  if (inHeader) return { region: 'header', row: -1, col }
  if (row < 0) return { region: 'outside', row: -1, col }
  return { region: 'cell', row, col }
}

export function dragFocus(
  mode: DragMode,
  hit: GridHit,
  fallback: GridPoint,
  rowCount: number,
  colCount: number,
): GridPoint {
  const clampRow = (value: number) => Math.min(rowCount - 1, Math.max(0, value))
  const clampCol = (value: number) => Math.min(colCount - 1, Math.max(0, value))
  if (mode === 'row') {
    const row = hit.row >= 0 ? hit.row : fallback.row
    return { row: clampRow(row), col: 0 }
  }
  if (mode === 'column') {
    const col = hit.col >= 0 ? hit.col : fallback.col
    return { row: 0, col: clampCol(col) }
  }
  const row = hit.row >= 0 ? hit.row : hit.region === 'header' ? 0 : fallback.row
  const col = hit.col >= 0 ? hit.col : hit.region === 'row-number' ? 0 : fallback.col
  return { row: clampRow(row), col: clampCol(col) }
}

export function selectionTsv(
  selection: GridSelection,
  displayedRows: SqlCellValue[][],
  visualColumnIndexes: number[],
): string {
  const rect = rectOf(selection)
  const lines: string[] = []
  for (let row = rect.rowStart; row <= rect.rowEnd; row += 1) {
    const cells = displayedRows[row]
    if (!cells) continue
    const line: string[] = []
    for (let col = rect.colStart; col <= rect.colEnd; col += 1) {
      const dataCol = visualColumnIndexes[col]
      line.push(displayCell(dataCol == null ? null : cells[dataCol]))
    }
    lines.push(line.join('\t'))
  }
  return lines.join('\n')
}

function rowAt(localY: number, layout: HitTestLayout): number {
  const index = Math.floor((localY + layout.scrollTop - layout.headerHeight) / layout.rowHeight)
  if (index < 0 || index >= layout.rowCount) return -1
  return index
}

function columnAt(localX: number, layout: HitTestLayout): number {
  const pinnedCount = Math.min(layout.pinnedCount, layout.columnWidths.length)
  let stickyLeft = layout.indexWidth
  for (let index = 0; index < pinnedCount; index += 1) {
    const width = layout.columnWidths[index] ?? 0
    if (localX >= stickyLeft && localX < stickyLeft + width) return index
    stickyLeft += width
  }
  const contentX = localX + layout.scrollLeft
  let left = layout.indexWidth
  for (let index = 0; index < layout.columnWidths.length; index += 1) {
    const width = layout.columnWidths[index] ?? 0
    if (contentX >= left && contentX < left + width) {
      return index < pinnedCount ? -1 : index
    }
    left += width
  }
  return -1
}
