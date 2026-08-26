import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CopilotPanel from '@/components/copilot/CopilotPanel.vue'
import type { CopilotMessage } from '@/stores/copilot'

const assistant: CopilotMessage = {
  id: 'a1',
  role: 'assistant',
  content: '可以用：\n```sql\nSELECT 1;\n```',
}

function render(extra: Record<string, unknown> = {}, messages: CopilotMessage[] = []) {
  return mount(CopilotPanel, {
    props: {
      available: true,
      disabledReason: '请先在左侧选择数据源和数据库',
      dataSourceName: '订单测试库',
      database: 'orders',
      dialect: 'MySQL',
      messages,
      inflight: false,
      canInsertAndRun: true,
      ...extra,
    },
  })
}

describe('copilot panel', () => {
  it('disables input without a bound connection', () => {
    const wrapper = render({
      available: false,
      disabledReason: '请先在左侧选择数据源和数据库',
    })
    const input = wrapper.get('[data-testid="copilot-input"]')
    expect(input.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="copilot-send"]').attributes('disabled')).toBeDefined()
    expect(input.attributes('placeholder')).toContain('选择数据源')
    wrapper.unmount()
  })

  it('offers insert actions for sql fences', async () => {
    const wrapper = render({}, [assistant])
    expect(wrapper.get('[data-testid="copilot-sql"]').text()).toContain('SELECT 1;')
    await wrapper.get('[data-testid="copilot-insert"]').trigger('click')
    expect(wrapper.emitted('insert')?.at(-1)).toEqual(['SELECT 1;', undefined])
    wrapper.unmount()
  })

  it('renders assistant prose as markdown instead of raw markers', () => {
    const wrapper = render({}, [
      {
        id: 'a2',
        role: 'assistant',
        content: '这是 **只读** 查询。\n```sql\nSELECT 1;\n```',
      },
    ])
    expect(wrapper.get('[data-testid="copilot-md"]').html()).toContain('<strong>只读</strong>')
    expect(wrapper.get('[data-testid="copilot-md"]').text()).not.toContain('**只读**')
    wrapper.unmount()
  })

  it('sends and clears the composer on Enter', async () => {
    const wrapper = render()
    const input = wrapper.get('[data-testid="copilot-input"]')
    await input.setValue('列出订单')
    await input.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('send')?.at(-1)).toEqual(['列出订单'])
    expect((input.element as HTMLTextAreaElement).value).toBe('')
    wrapper.unmount()
  })

  it('uses readonly instead of disabled while a reply is in flight', () => {
    const wrapper = render({ inflight: true })
    const input = wrapper.get('[data-testid="copilot-input"]')
    expect(input.attributes('disabled')).toBeUndefined()
    expect(input.attributes('readonly')).toBeDefined()
    wrapper.unmount()
  })

  it('keeps composing IME Enter from sending', async () => {
    const wrapper = render()
    const input = wrapper.get('[data-testid="copilot-input"]')
    await input.setValue('lie')
    await input.trigger('keydown', { key: 'Enter', isComposing: true })
    expect(wrapper.emitted('send')).toBeUndefined()
    expect((input.element as HTMLTextAreaElement).value).toBe('lie')
    wrapper.unmount()
  })

  it('does not let compositionend restore the sent prompt', async () => {
    const wrapper = render()
    const input = wrapper.get('[data-testid="copilot-input"]')
    const el = input.element as HTMLTextAreaElement
    await input.setValue('列出订单')
    await input.trigger('keydown', { key: 'Enter' })
    await wrapper.setProps({ inflight: true })
    el.value = '列出订单'
    await input.trigger('compositionend')
    expect(wrapper.emitted('send')).toHaveLength(1)
    expect(el.value).toBe('')
    wrapper.unmount()
  })

  it('shows live tool progress while the assistant is streaming', () => {
    const wrapper = render({}, [
      {
        id: 'a3',
        role: 'assistant',
        content: '',
        streaming: true,
        tools: [{ id: 't1', toolName: 'listTables', status: 'STARTED' }],
      },
    ])
    expect(wrapper.get('[data-testid="copilot-tools"]').text()).toContain('正在列出数据表')
    expect(wrapper.find('[data-testid="copilot-caret"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
