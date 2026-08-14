import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ResultGrid from '@/components/result-grid/ResultGrid.vue'
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

function render(extra: Record<string, unknown> = {}) {
  return mount(ResultGrid, {
    props: { error: null, result, canExport: true, ...extra },
    attachTo: document.body,
  })
}

describe('result values', () => {
  it('distinguishes null, numeric strings, and binary data', () => {
    const wrapper = render()
    expect(wrapper.text()).toContain('9007199254740993')
    expect(wrapper.text()).toContain('BINARY · 12 bytes')
    expect(wrapper.text()).toContain('NULL')
    expect(wrapper.get('[title="空字符串"]').text()).toBe('')
    wrapper.unmount()
  })

  it('shows type glyphs and a CloudBeaver-style footer', () => {
    const wrapper = render()
    expect(wrapper.get('[data-testid="result-header-0"]').text()).toContain('123')
    expect(wrapper.get('[data-testid="result-header-1"]').text()).toContain('A-Z')
    expect(wrapper.get('[data-testid="result-header-2"]').text()).toContain('BIN')
    expect(wrapper.get('[data-testid="result-limit"]').element).toHaveValue('1000')
    expect(wrapper.get('[data-testid="result-export"]').text()).toContain('导出')
    wrapper.unmount()
  })

  it('sorts by header click and opens the value panel', async () => {
    const wrapper = render()
    await wrapper.get('[data-testid="result-header-0"]').trigger('click')
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
    expect(menu?.textContent).toContain('固定列')
    const pin = Array.from(menu?.querySelectorAll('button') || []).find((button) =>
      button.textContent?.includes('固定列'),
    )
    pin?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="result-header-1"]').classes()).toContain('result-col-pinned')
    wrapper.unmount()
  })
})
