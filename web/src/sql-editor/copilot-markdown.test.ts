import { describe, expect, it } from 'vitest'
import { renderAssistantMarkdown } from '@/sql-editor/copilot-markdown'

describe('copilot markdown', () => {
  it('renders headings, emphasis, lists and inline code', () => {
    const html = renderAssistantMarkdown(
      '## 说明\n\n这是 **只读** 查询，字段是 `amount`。\n\n- 先看表\n- 再过滤时间',
    )
    expect(html).toContain('<h2>')
    expect(html).toContain('<strong>只读</strong>')
    expect(html).toContain('<code>amount</code>')
    expect(html).toContain('<li>')
    expect(html).not.toContain('**只读**')
  })

  it('does not keep raw html or unsafe links', () => {
    const html = renderAssistantMarkdown(
      '<script>alert(1)</script>\n\n[安全](https://example.com) [危险](javascript:alert(1))',
    )
    expect(html).not.toContain('<script>')
    expect(html).toContain('href="https://example.com"')
    expect(html).not.toContain('javascript:')
    expect(html).toContain('危险')
  })
})
