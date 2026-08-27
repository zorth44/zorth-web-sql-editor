import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import CopilotPanel from '@/components/copilot/CopilotPanel.vue'
import type { CopilotMessage } from '@/stores/copilot'

const assistant: CopilotMessage = {
  id: 'a1',
  role: 'assistant',
  content: '可以用：\n```sql\nSELECT 1;\n```',
}

const listed = {
  id: 'c-1',
  title: '列出订单',
  updatedAt: '2026-08-26T08:00:00Z',
  datasourceId: 'ds-orders-a',
  database: 'orders',
}

function render(
  extra: Record<string, unknown> = {},
  messages: CopilotMessage[] = [],
  mountOptions: Record<string, unknown> = {},
) {
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
    ...mountOptions,
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

  it('emits new conversation from the header', async () => {
    const wrapper = render()
    expect(wrapper.get('[data-testid="copilot-new"]').text()).toContain('新对话')
    expect(wrapper.get('[data-testid="copilot-history-toggle"]').text()).toContain('历史')
    await wrapper.get('[data-testid="copilot-new"]').trigger('click')
    expect(wrapper.emitted('new-conversation')).toHaveLength(1)
    wrapper.unmount()
  })

  it('hides history on an empty chat until the list is opened', async () => {
    const wrapper = render({ conversations: [listed] })
    const toggle = wrapper.get('[data-testid="copilot-history-toggle"]')
    expect(wrapper.find('[data-testid="copilot-history"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="copilot-input"]').exists()).toBe(true)
    expect(toggle.attributes('aria-pressed')).toBe('false')
    await toggle.trigger('click')
    expect(wrapper.get('[data-testid="copilot-history"]').text()).toContain('列出订单')
    expect(wrapper.find('[data-testid="copilot-input"]').exists()).toBe(false)
    expect(toggle.classes()).toContain('activity-btn-active')
    expect(toggle.attributes('aria-pressed')).toBe('true')
    await toggle.trigger('click')
    expect(wrapper.find('[data-testid="copilot-history"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="copilot-input"]').exists()).toBe(true)
    expect(toggle.attributes('aria-pressed')).toBe('false')
    wrapper.unmount()
  })

  it('explains an empty history list after opening history', async () => {
    const wrapper = render()
    await wrapper.get('[data-testid="copilot-history-toggle"]').trigger('click')
    expect(wrapper.get('[data-testid="copilot-history-empty"]').text()).toContain('还没有历史对话')
    wrapper.unmount()
  })

  it('opens a conversation from the history view and returns to chat', async () => {
    const wrapper = render({
      sources: [{ id: 'ds-orders-a', name: '订单测试库' }],
      conversations: [listed],
    })
    await wrapper.get('[data-testid="copilot-history-toggle"]').trigger('click')
    expect(wrapper.get('[data-testid="copilot-history"]').text()).toContain('列出订单')
    expect(wrapper.get('[data-testid="copilot-history"]').text()).toContain('订单测试库 / orders')
    expect(wrapper.get('[data-testid="copilot-history"]').text()).toContain('ds-orders-a')
    await wrapper.get('[data-testid="copilot-history-item-c-1"]').trigger('click')
    expect(wrapper.emitted('open-conversation')?.at(-1)).toEqual(['c-1'])
    expect(wrapper.find('[data-testid="copilot-history"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="copilot-input"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('replaces the chat with the history view while chatting', async () => {
    const wrapper = render({ conversations: [listed] }, [assistant])
    expect(wrapper.find('[data-testid="copilot-history"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="copilot-messages"]').text()).toContain('SELECT 1;')
    await wrapper.get('[data-testid="copilot-history-toggle"]').trigger('click')
    expect(wrapper.get('[data-testid="copilot-history"]').text()).toContain('列出订单')
    expect(wrapper.find('[data-testid="copilot-messages"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="copilot-input"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="copilot-history-toggle"]').attributes('aria-pressed')).toBe(
      'true',
    )
    wrapper.unmount()
  })

  it('returns to chat when starting a new conversation from history', async () => {
    const wrapper = render({ conversations: [listed] })
    await wrapper.get('[data-testid="copilot-history-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="copilot-history"]').exists()).toBe(true)
    await wrapper.get('[data-testid="copilot-new"]').trigger('click')
    expect(wrapper.emitted('new-conversation')).toHaveLength(1)
    expect(wrapper.find('[data-testid="copilot-history"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="copilot-input"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('confirms deleting a conversation and stays on history', async () => {
    const wrapper = render({ conversations: [listed] }, [], { attachTo: document.body })
    await wrapper.get('[data-testid="copilot-history-toggle"]').trigger('click')
    await wrapper.get('[data-testid="copilot-history-delete-c-1"]').trigger('click')
    await nextTick()
    const confirm = document.body.querySelector('button.btn-danger') as HTMLButtonElement
    expect(confirm).toBeTruthy()
    confirm.click()
    await nextTick()
    expect(wrapper.emitted('delete-conversation')?.at(-1)).toEqual(['c-1'])
    expect(wrapper.find('[data-testid="copilot-history"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
