import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import TableViewer from '@/components/table-viewer/TableViewer.vue'
import { saveToken } from '@/auth/token-storage'

function render(pane: 'data' | 'properties' = 'data') {
  return mount(TableViewer, {
    props: {
      dataSourceId: 'ds-orders-a',
      database: 'orders',
      table: 'order_item',
      tableType: 'TABLE',
      tableComment: '订单明细',
      pane,
      result: {
        executionId: 'e',
        kind: 'RESULT_SET',
        columns: [{ name: 'id', label: 'id', jdbcType: 'BIGINT', typeName: 'BIGINT' }],
        rows: [['1']],
        rowCount: 1,
        truncated: false,
        durationMs: 4,
      },
      error: null,
      running: false,
      canExport: true,
      exporting: false,
      rowLimit: 1000,
    },
    attachTo: document.body,
  })
}

describe('table viewer', () => {
  beforeEach(() => saveToken('mock-token', false))

  it('defaults to Data and does not offer Diagram', async () => {
    const wrapper = render()
    expect(wrapper.get('[data-testid="table-viewer-tab-data"]').attributes('aria-selected')).toBe(
      'true',
    )
    expect(wrapper.text()).toContain('Data')
    expect(wrapper.text()).toContain('Properties')
    expect(wrapper.text()).not.toContain('Diagram')
    expect(wrapper.text()).toContain('1')
    await wrapper.get('[data-testid="table-viewer-tab-properties"]').trigger('click')
    expect(wrapper.emitted('update:pane')?.at(-1)).toEqual(['properties'])
    wrapper.unmount()
  })

  it('shows table columns in Properties', async () => {
    const wrapper = render('properties')
    await flushPromises()
    await wrapper.get('[data-testid="table-properties-nav-columns"]').trigger('click')
    expect(wrapper.text()).toContain('amount')
    expect(wrapper.text()).toContain('PK')
    wrapper.unmount()
  })

  it('shows table DDL in Properties', async () => {
    const wrapper = render('properties')
    await flushPromises()
    await wrapper.get('[data-testid="table-properties-nav-ddl"]').trigger('click')
    expect(wrapper.get('[data-testid="table-properties-ddl"]').text()).toContain(
      'CREATE TABLE `order_item`',
    )
    wrapper.unmount()
  })
})
