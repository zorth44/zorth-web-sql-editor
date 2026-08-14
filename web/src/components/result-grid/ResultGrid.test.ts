import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ResultGrid from '@/components/result-grid/ResultGrid.vue'
describe('result values', () => {
  it('distinguishes null, numeric strings, and binary data', () => {
    const wrapper = mount(ResultGrid, {
      props: {
        error: null,
        result: {
          executionId: 'e',
          kind: 'RESULT_SET',
          columns: [
            { name: 'a', label: 'a', jdbcType: 'BIGINT', typeName: 'BIGINT' },
            { name: 'b', label: 'b', jdbcType: 'BLOB', typeName: 'BLOB' },
          ],
          rows: [
            ['9007199254740993', { binary: true, size: 12, base64: null }],
            [null, ''],
          ],
          rowCount: 2,
          truncated: false,
          durationMs: 4,
        },
      },
    })
    expect(wrapper.text()).toContain('9007199254740993')
    expect(wrapper.text()).toContain('BINARY · 12 bytes')
    expect(wrapper.text()).toContain('NULL')
    expect(wrapper.get('[title="空字符串"]').text()).toBe('')
  })
})
