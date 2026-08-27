import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ResultGrid from '@/components/result-grid/ResultGrid.vue'
import {
  FILTER_ROW_HEIGHT,
  HEADER_HEIGHT,
  INDEX_WIDTH,
  ROW_HEIGHT,
} from '@/components/result-grid/selection'
import type { SqlExecutionResult } from '@/types/contracts'

const result: SqlExecutionResult = {
  executionId: 'e',
  kind: 'RESULT_SET',
  columns: [
    { name: 'id', label: 'id', jdbcType: 'BIGINT', typeName: 'BIGINT' },
    { name: 'note', label: 'note', jdbcType: 'VARCHAR', typeName: 'VARCHAR' },
    { name: 'bin', label: 'bin', jdbcType: 'BLOB', typeName: 'BLOB' },
  ],
  rows: [
    ['2', 'beta', { binary: true, size: 12, base64: null }],
    ['9007199254740993', null, ''],
    ['1', 'alpha', null],
  ],
  rowCount: 3,
  truncated: false,
  durationMs: 4,
}

const writeText = vi.fn().mockResolvedValue(undefined)

function render(extra: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(ResultGrid, {
    props: { error: null, result, canExport: true, ...extra },
    slots,
    attachTo: document.body,
  })
}

function mockGrid(wrapper: ReturnType<typeof render>): HTMLElement {
  const el = wrapper.get('[data-testid="result-scroll"]').element as HTMLElement
  Object.defineProperty(el, 'clientWidth', { configurable: true, value: 800 })
  Object.defineProperty(el, 'clientHeight', { configurable: true, value: 400 })
  el.scrollLeft = 0
  el.scrollTop = 0
  el.getBoundingClientRect = () =>
    ({
      x: 0,
      y: 0,
      left: 0,
      top: 0,
      width: 800,
      height: 400,
      right: 800,
      bottom: 400,
      toJSON() {
        return {}
      },
    }) as DOMRect
  return el
}

function cellPoint(visualCol: number, displayedRow: number): { x: number; y: number } {
  return {
    x: INDEX_WIDTH + visualCol * 120 + 8,
    y: HEADER_HEIGHT + displayedRow * ROW_HEIGHT + 4,
  }
}

async function pointer(
  wrapper: ReturnType<typeof render>,
  type: 'pointerdown' | 'pointermove' | 'pointerup',
  x: number,
  y: number,
  extra: Record<string, unknown> = {},
): Promise<void> {
  mockGrid(wrapper)
  await wrapper.get('[data-testid="result-scroll"]').trigger(type, {
    button: 0,
    clientX: x,
    clientY: y,
    pointerId: 1,
    isPrimary: true,
    bubbles: true,
    ...extra,
  })
}

describe('result values', () => {
  afterEach(() => {
    writeText.mockClear()
  })

  it('distinguishes null, numeric strings, and binary data', () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('9007199254740993')
    expect(wrapper.text()).toContain('BINARY · 12 bytes')
    expect(wrapper.text()).toContain('NULL')
    expect(wrapper.get('[title="空字符串"]').text()).toBe('')
    wrapper.unmount()
  })

  it('shows type glyphs and a CloudBeaver-style footer', () => {
    const wrapper = render({}, { status: '订单测试库 / orders | Query 1' })
    expect(wrapper.get('[data-testid="result-header-0"]').text()).toContain('123')
    expect(wrapper.get('[data-testid="result-header-1"]').text()).toContain('A-Z')
    expect(wrapper.get('[data-testid="result-header-2"]').text()).toContain('BIN')
    expect(wrapper.get('[data-testid="result-limit"]').element).toHaveValue('1000')
    expect(wrapper.get('[data-testid="result-export"]').text()).toContain('导出')
    expect(wrapper.get('[data-testid="result-footer-status"]').text()).toContain('订单测试库')
    wrapper.unmount()
  })

  it('sorts from the type glyph and opens the value panel', async () => {
    const wrapper = render()
    await wrapper.get('[data-testid="result-header-0"]').trigger('click')
    expect(wrapper.get('.result-row').text()).toContain('2')
    await wrapper.get('[data-testid="result-sort-glyph-0"]').trigger('click')
    expect(wrapper.get('.result-row').text()).toContain('1')
    await wrapper.get('[data-testid="result-cell-2-1"]').trigger('dblclick')
    expect(wrapper.get('[data-testid="result-value-panel"]').text()).toContain('alpha')
    wrapper.unmount()
  })

  it('pins a column from the cell context menu', async () => {
    const wrapper = render()
    await wrapper.get('[data-testid="result-cell-0-1"]').trigger('contextmenu')
    const menu = document.querySelector('[data-testid="result-context-menu"]')
    expect(menu?.textContent).toContain('在值面板中显示')
    expect(menu?.textContent).toContain('复制选区')
    expect(menu?.textContent).toContain('固定列')
    const pin = Array.from(menu?.querySelectorAll('button') || []).find((button) =>
      button.textContent?.includes('固定列'),
    )
    pin?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="result-header-1"]').classes()).toContain('result-col-pinned')
    wrapper.unmount()
  })

  it('highlights a hovered cell without changing selection', async () => {
    const wrapper = render()
    const { x, y } = cellPoint(1, 0)
    await pointer(wrapper, 'pointermove', x, y)
    expect(wrapper.get('[data-testid="result-cell-0-1"]').classes()).toContain('result-cell-hover')
    expect(wrapper.get('.result-row').classes()).toContain('result-row-hover')
    expect(wrapper.find('.result-cell-selected').exists()).toBe(false)
    wrapper.unmount()
  })

  it('drag-selects a rectangle of cells', async () => {
    const wrapper = render()
    const start = cellPoint(0, 0)
    const end = cellPoint(1, 1)
    await pointer(wrapper, 'pointerdown', start.x, start.y)
    await pointer(wrapper, 'pointermove', end.x, end.y)
    await pointer(wrapper, 'pointerup', end.x, end.y)
    expect(wrapper.get('[data-testid="result-cell-0-0"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-0-1"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-1-0"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-1-1"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-2-0"]').classes()).not.toContain(
      'result-cell-selected',
    )
    wrapper.unmount()
  })

  it('extends the selection with Shift-click', async () => {
    const wrapper = render()
    const origin = cellPoint(0, 0)
    const next = cellPoint(1, 2)
    await pointer(wrapper, 'pointerdown', origin.x, origin.y)
    await pointer(wrapper, 'pointerup', origin.x, origin.y)
    await pointer(wrapper, 'pointerdown', next.x, next.y, { shiftKey: true })
    await pointer(wrapper, 'pointerup', next.x, next.y, { shiftKey: true })
    expect(wrapper.get('[data-testid="result-cell-2-1"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-0-0"]').classes()).toContain(
      'result-cell-selected',
    )
    wrapper.unmount()
  })

  it('selects a row from the row-number column and a column from the header', async () => {
    const wrapper = render()
    await pointer(wrapper, 'pointerdown', 8, HEADER_HEIGHT + ROW_HEIGHT + 4)
    await pointer(wrapper, 'pointerup', 8, HEADER_HEIGHT + ROW_HEIGHT + 4)
    expect(wrapper.get('[data-testid="result-cell-1-0"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-1-2"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-0-0"]').classes()).not.toContain(
      'result-cell-selected',
    )
    await pointer(wrapper, 'pointerdown', INDEX_WIDTH + 128, 8)
    await pointer(wrapper, 'pointerup', INDEX_WIDTH + 128, 8)
    expect(wrapper.get('[data-testid="result-cell-0-1"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-2-1"]').classes()).toContain(
      'result-cell-selected',
    )
    expect(wrapper.get('[data-testid="result-cell-0-0"]').classes()).not.toContain(
      'result-cell-selected',
    )
    wrapper.unmount()
  })

  it('copies the selected rectangle as header-less TSV', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    const wrapper = render()
    const start = cellPoint(0, 0)
    const end = cellPoint(1, 1)
    await pointer(wrapper, 'pointerdown', start.x, start.y)
    await pointer(wrapper, 'pointermove', end.x, end.y)
    await pointer(wrapper, 'pointerup', end.x, end.y)
    await wrapper.get('[data-testid="result-pane"]').trigger('keydown', {
      key: 'c',
      metaKey: true,
    })
    expect(writeText).toHaveBeenCalledWith(['2\tbeta', '9007199254740993\tNULL'].join('\n'))
    wrapper.unmount()
  })

  it('offers AI fix on a failed result and can disable it', async () => {
    const wrapper = render({ result: null, error: "Table 'orders.mock_error' doesn't exist" })
    expect(wrapper.find('[data-testid="copilot-fix"]').exists()).toBe(false)
    await wrapper.setProps({ canFixWithAi: true, fixDisabled: true })
    const button = wrapper.get('[data-testid="copilot-fix"]')
    expect(button.attributes('disabled')).toBeDefined()
    await wrapper.setProps({ fixDisabled: false })
    await wrapper.get('[data-testid="copilot-fix"]').trigger('click')
    expect(wrapper.emitted('fix-with-ai')).toHaveLength(1)
    wrapper.unmount()
  })

  it('keeps SQL result grids on client-side filter without a header filter row', () => {
    const wrapper = render()
    expect(wrapper.find('[data-testid="result-header-filter-row"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="result-quick-filter"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('shows header filter inputs on table Data and applies them on Enter', async () => {
    const wrapper = render({
      headerFilters: true,
      filterDrafts: { note: 'alpha' },
      filterErrors: { id: '数字列需要有效数字' },
    })
    expect(wrapper.find('[data-testid="result-header-filter-row"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="result-quick-filter"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="result-header-filter-1"]').element).toHaveValue('alpha')
    expect(wrapper.get('[data-testid="result-header-filter-0"]').classes()).toContain(
      'result-header-filter-error',
    )
    await wrapper.get('[data-testid="result-header-filter-1"]').setValue('beta')
    expect(wrapper.emitted('update:filterDrafts')?.at(-1)).toEqual([{ note: 'beta' }])
    await wrapper.get('[data-testid="result-header-filter-1"]').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('apply-filters')).toHaveLength(1)
    wrapper.unmount()
  })

  it('keeps header filter drafts after a new table Data result arrives', async () => {
    const wrapper = render({ headerFilters: true, filterDrafts: { note: 'keep' } })
    await wrapper.setProps({
      result: { ...result, executionId: 'e2', rows: [['3', 'gamma', null]] },
    })
    expect(wrapper.get('[data-testid="result-header-filter-1"]').element).toHaveValue('keep')
    wrapper.unmount()
  })

  it('does not select a column from the header filter row', async () => {
    const wrapper = render({ headerFilters: true })
    await pointer(wrapper, 'pointerdown', INDEX_WIDTH + 128, HEADER_HEIGHT + FILTER_ROW_HEIGHT / 2)
    await pointer(wrapper, 'pointerup', INDEX_WIDTH + 128, HEADER_HEIGHT + FILTER_ROW_HEIGHT / 2)
    expect(wrapper.find('.result-cell-selected').exists()).toBe(false)
    wrapper.unmount()
  })

  it('emits sort changes instead of sorting returned rows in table Data mode', async () => {
    const wrapper = render({ headerFilters: true })
    expect(wrapper.get('.result-row').text()).toContain('2')
    await wrapper.get('[data-testid="result-sort-glyph-0"]').trigger('click')
    expect(wrapper.emitted('update:sortState')?.at(-1)).toEqual([{ column: 'id', dir: 'asc' }])
    expect(wrapper.get('.result-row').text()).toContain('2')
    wrapper.unmount()
  })

  it('hides context-menu column filter in table Data mode', async () => {
    const wrapper = render({ headerFilters: true })
    await wrapper.get('[data-testid="result-cell-0-1"]').trigger('contextmenu')
    const menu = document.querySelector('[data-testid="result-context-menu"]')
    expect(menu?.textContent).toContain('排序')
    expect(menu?.textContent).toContain('固定列')
    expect(menu?.textContent).not.toContain('筛选')
    wrapper.unmount()
  })

  it('opens a date range picker on date columns and applies the range', async () => {
    const wrapper = render({
      headerFilters: true,
      result: {
        ...result,
        columns: [
          { name: 'created_at', label: 'created_at', jdbcType: 'DATE', typeName: 'DATE' },
          { name: 'note', label: 'note', jdbcType: 'VARCHAR', typeName: 'VARCHAR' },
        ],
        rows: [['2026-01-02', 'beta']],
      },
    })
    await wrapper.get('[data-testid="result-header-filter-0"]').trigger('click')
    await nextTick()
    const start = document.querySelector('[data-testid="result-date-range-start"]')
    const end = document.querySelector('[data-testid="result-date-range-end"]')
    const apply = document.querySelector('[data-testid="result-date-range-apply"]')
    expect(start).toBeInstanceOf(HTMLInputElement)
    expect(end).toBeInstanceOf(HTMLInputElement)
    expect(apply).toBeInstanceOf(HTMLButtonElement)
    if (
      !(start instanceof HTMLInputElement) ||
      !(end instanceof HTMLInputElement) ||
      !(apply instanceof HTMLButtonElement)
    ) {
      return
    }
    start.value = '2026-01-01'
    start.dispatchEvent(new Event('input', { bubbles: true }))
    end.value = '2026-01-31'
    end.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()
    apply.click()
    await nextTick()
    expect(wrapper.emitted('update:filterDrafts')?.at(-1)).toEqual([
      { created_at: '2026-01-01..2026-01-31' },
    ])
    expect(wrapper.emitted('apply-filters')).toHaveLength(1)
    wrapper.unmount()
  })
})
