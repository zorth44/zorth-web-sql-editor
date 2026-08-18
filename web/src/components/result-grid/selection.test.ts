import { describe, expect, it } from 'vitest'
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
  type HitTestLayout,
} from './selection'

const layout = (overrides: Partial<HitTestLayout> = {}): HitTestLayout => ({
  indexWidth: INDEX_WIDTH,
  headerHeight: HEADER_HEIGHT,
  rowHeight: ROW_HEIGHT,
  columnWidths: [100, 80, 120],
  pinnedCount: 0,
  rowCount: 4,
  colCount: 3,
  scrollLeft: 0,
  scrollTop: 0,
  viewportWidth: 400,
  viewportHeight: 200,
  ...overrides,
})

describe('selection rectangles', () => {
  it('builds a single-cell rectangle', () => {
    const selection = selectCell(2, 1)
    expect(rectOf(selection)).toEqual({ rowStart: 2, rowEnd: 2, colStart: 1, colEnd: 1 })
    expect(inRect(rectOf(selection), 2, 1)).toBe(true)
    expect(inRect(rectOf(selection), 2, 0)).toBe(false)
  })

  it('expands a rectangle from the anchor', () => {
    const selection = extendSelection(selectCell(1, 0), { row: 3, col: 2 })
    expect(rectOf(selection)).toEqual({ rowStart: 1, rowEnd: 3, colStart: 0, colEnd: 2 })
  })

  it('selects full rows and columns', () => {
    expect(rectOf(selectRows(1, 2, 3))).toEqual({
      rowStart: 1,
      rowEnd: 2,
      colStart: 0,
      colEnd: 2,
    })
    expect(rectOf(selectColumns(1, 2, 4))).toEqual({
      rowStart: 0,
      rowEnd: 3,
      colStart: 1,
      colEnd: 2,
    })
  })

  it('keeps row/column drag expansion across the full axis', () => {
    expect(selectionFromDrag('row', { row: 0, col: 0 }, { row: 2, col: 1 }, 4, 3)).toEqual(
      selectRows(0, 2, 3),
    )
    expect(selectionFromDrag('column', { row: 0, col: 1 }, { row: 2, col: 2 }, 4, 3)).toEqual(
      selectColumns(1, 2, 4),
    )
    expect(selectionFromDrag('cell', { row: 0, col: 1 }, { row: 2, col: 2 }, 4, 3)).toEqual({
      anchor: { row: 0, col: 1 },
      focus: { row: 2, col: 2 },
    })
  })

  it('clamps a selection after the visible row set shrinks', () => {
    const next = clampSelection(extendSelection(selectCell(1, 2), { row: 8, col: 9 }), 3, 2)
    expect(next).toEqual({
      anchor: { row: 1, col: 1 },
      focus: { row: 2, col: 1 },
    })
    expect(clampSelection(selectCell(0, 0), 0, 3)).toBeNull()
  })
})

describe('hit testing', () => {
  it('maps pointer coordinates onto cells, row numbers, and headers', () => {
    expect(hitTest(10, HEADER_HEIGHT + 4, layout())).toMatchObject({
      region: 'row-number',
      row: 0,
    })
    expect(hitTest(INDEX_WIDTH + 10, 8, layout())).toMatchObject({ region: 'header', col: 0 })
    expect(hitTest(INDEX_WIDTH + 110, HEADER_HEIGHT + ROW_HEIGHT + 2, layout())).toEqual({
      region: 'cell',
      row: 1,
      col: 1,
    })
    expect(hitTest(8, 8, layout())).toMatchObject({ region: 'corner' })
    expect(hitTest(-1, 40, layout())).toMatchObject({ region: 'outside' })
  })

  it('accounts for vertical scroll when resolving displayed rows', () => {
    expect(hitTest(INDEX_WIDTH + 10, HEADER_HEIGHT + 2, layout({ scrollTop: ROW_HEIGHT }))).toEqual(
      {
        region: 'cell',
        row: 1,
        col: 0,
      },
    )
  })

  it('keeps pinned columns hittable after horizontal scroll', () => {
    const pinned = layout({ pinnedCount: 1, scrollLeft: 90 })
    expect(hitTest(INDEX_WIDTH + 10, HEADER_HEIGHT + 2, pinned)).toEqual({
      region: 'cell',
      row: 0,
      col: 0,
    })
    expect(hitTest(INDEX_WIDTH + 110, HEADER_HEIGHT + 2, pinned)).toEqual({
      region: 'cell',
      row: 0,
      col: 2,
    })
  })

  it('maps drag hits onto the nearest in-range cell', () => {
    expect(
      dragFocus('cell', { region: 'row-number', row: 2, col: -1 }, { row: 0, col: 1 }, 4, 3),
    ).toEqual({
      row: 2,
      col: 0,
    })
    expect(dragFocus('row', { region: 'cell', row: 3, col: 2 }, { row: 1, col: 0 }, 4, 3)).toEqual({
      row: 3,
      col: 0,
    })
    expect(
      dragFocus('column', { region: 'header', row: -1, col: 2 }, { row: 0, col: 0 }, 4, 3),
    ).toEqual({
      row: 0,
      col: 2,
    })
  })
})

describe('selection TSV', () => {
  const rows = [
    ['2', 'beta', { binary: true, size: 12, base64: null }],
    ['1', null, ''],
    ['3', 'alpha', 'x'],
  ]
  const visual = [0, 1, 2]

  it('copies a single cell with display text', () => {
    expect(selectionTsv(selectCell(1, 0), rows, visual)).toBe('1')
  })

  it('copies a rectangle without a header row, using visual column order', () => {
    const selection = extendSelection(selectCell(0, 0), { row: 1, col: 2 })
    expect(selectionTsv(selection, rows, visual)).toBe(
      ['2\tbeta\tBINARY · 12 bytes', '1\tNULL\t'].join('\n'),
    )
  })

  it('follows pinned visual order when serializing', () => {
    const selection = extendSelection(selectCell(0, 0), { row: 0, col: 1 })
    expect(selectionTsv(selection, rows, [1, 0, 2])).toBe('beta\t2')
  })

  it('uses filtered/sorted displayed rows rather than original indexes', () => {
    const filtered = [rows[2]!, rows[1]!]
    expect(selectionTsv(selectRows(0, 1, 2), filtered, [0, 1])).toBe(
      ['3\talpha', '1\tNULL'].join('\n'),
    )
  })
})
