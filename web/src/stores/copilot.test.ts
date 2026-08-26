import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCopilotStore } from '@/stores/copilot'
import { streamAgent } from '@/api/ai-agent'

vi.mock('@/api/ai-agent', () => ({
  streamAgent: vi.fn(),
}))

const streamAgentMock = vi.mocked(streamAgent)

beforeEach(() => {
  setActivePinia(createPinia())
  streamAgentMock.mockReset()
  streamAgentMock.mockImplementation(async (body, onEvent) => {
    onEvent({ type: 'start', conversationId: 'c-1' })
    const content = body.message.includes('__NO_SQL__')
      ? '当前没有可插入的 SQL。'
      : '可以用：\n```sql\nSELECT id FROM order_item;\n```'
    onEvent({ type: 'tool', toolName: 'listTables', status: 'STARTED' })
    onEvent({ type: 'tool', toolName: 'listTables', status: 'SUCCESS' })
    onEvent({ type: 'delta', content })
    onEvent({ type: 'completed', conversationId: 'c-1' })
    return { content, conversationId: 'c-1' }
  })
})

describe('copilot store', () => {
  it('keeps conversations isolated by sql tab and does not persist them', async () => {
    const store = useCopilotStore()
    await store.send({
      tabId: 'tab-a',
      userText: '列出订单',
      message: '列出订单',
      datasourceId: 'ds-orders-a',
      database: 'orders',
    })
    await store.send({
      tabId: 'tab-b',
      userText: '没有代码块',
      message: '__NO_SQL__',
      datasourceId: 'ds-orders-a',
      database: 'orders',
    })
    expect(store.messagesOf('tab-a').map((item) => item.role)).toEqual(['user', 'assistant'])
    expect(store.messagesOf('tab-a').at(-1)?.content).toContain('```sql')
    expect(store.messagesOf('tab-a').at(-1)?.tools?.[0]?.toolName).toBe('listTables')
    expect(store.messagesOf('tab-b').at(-1)?.content).toContain('没有可插入')
    expect(sessionStorage.getItem('zorth.sql-editor.drafts.v1')).toBeNull()
    store.retain(['tab-b'])
    expect(store.messagesOf('tab-a')).toEqual([])
    expect(store.messagesOf('tab-b').length).toBe(2)
  })

  it('cancels an inflight request and does not keep a partial assistant message', async () => {
    streamAgentMock.mockImplementation((_body, _onEvent, signal) => {
      return new Promise((_, reject) => {
        const fail = () =>
          reject(Object.assign(new Error('Aborted'), { name: 'AbortError' }))
        if (signal?.aborted) fail()
        else signal?.addEventListener('abort', fail)
      })
    })
    const store = useCopilotStore()
    const pending = store.send({
      tabId: 'tab-a',
      userText: '慢',
      message: '慢',
      datasourceId: 'ds-orders-a',
      database: 'orders',
    })
    await vi.waitFor(() => expect(store.inflight).toBe(true))
    store.cancel()
    await pending
    expect(store.inflight).toBe(false)
    expect(store.messagesOf('tab-a').map((item) => item.role)).toEqual(['user'])
  })

  it('appends streamed deltas onto a live assistant message', async () => {
    const store = useCopilotStore()
    let seen = ''
    streamAgentMock.mockImplementation(async (_body, onEvent) => {
      onEvent({ type: 'delta', content: '用' })
      seen = store.messagesOf('tab-a').at(-1)?.content || ''
      onEvent({ type: 'delta', content: '下面的语句' })
      onEvent({ type: 'completed', conversationId: 'c-1' })
      return { content: '用下面的语句', conversationId: 'c-1' }
    })
    await store.send({
      tabId: 'tab-a',
      userText: '列出订单',
      message: '列出订单',
      datasourceId: 'ds-orders-a',
      database: 'orders',
    })
    expect(seen).toBe('用')
    expect(store.messagesOf('tab-a').at(-1)?.content).toBe('用下面的语句')
    expect(store.messagesOf('tab-a').at(-1)?.streaming).toBe(false)
  })
})
