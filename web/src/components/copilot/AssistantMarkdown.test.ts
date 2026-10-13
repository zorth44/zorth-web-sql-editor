import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AssistantMarkdown from './AssistantMarkdown'
describe('assistant Markdown nodes', () => {
  it('preserves Markdown formatting while rejecting raw HTML and executable attributes', () => {
    const wrapper = mount(AssistantMarkdown, {
      props: {
        text: '**安全** [链接](https://example.com) [坏](javascript:alert(1))\n\n<img src=x onerror=alert(1)><script>alert(1)</script>',
      },
    })
    expect(wrapper.get('strong').text()).toBe('安全')
    expect(wrapper.get('a').attributes('rel')).toBe('noreferrer noopener')
    expect(wrapper.html()).not.toContain('javascript:')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
  })
})
