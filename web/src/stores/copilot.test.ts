import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCopilotStore } from '@/stores/copilot'
import {
  deleteConversation,
  getConversation,
  listConversations,
  streamAgent,
} from '@/api/ai-agent'
import { ApiError } from '@/api/api-error'

vi.mock('@/api/ai-agent', () => ({
  streamAgent: vi.fn(),
  listConversations: vi.fn(),
  getConversation: vi.fn(),
  deleteConversation: vi.fn(),
}))

const streamAgentMock = vi.mocked(streamAgent)
const listConversationsMock = vi.mocked(listConversations)
const getConversationMock = vi.mocked(getConversation)
const deleteConversationMock = vi.mocked(deleteConversation)

beforeEach(() => {
  setActivePinia(createPinia())
  streamAgentMock.mockReset()
  listConversationsMock.mockReset()
  getConversationMock.mockReset()
  deleteConversationMock.mockReset()
  listConversationsMock.mockResolvedValue({ items: [] })
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

const sampleSend = {
  userText: '列出订单',
  message: '列出订单',
  datasourceId: 'ds-orders-a',
  database: 'orders',
}

describe('copilot store', () => {
  it('keeps the conversation after the sql tab would have closed and does not persist locally', async () => {
    const store = useCopilotStore()
    await store.send(sampleSend)
    expect(store.messages.map((item) => item.role)).toEqual(['user', 'assistant'])
    expect(store.messages.at(-1)?.content).toContain('```sql')
    expect(store.messages.at(-1)?.tools?.[0]?.toolName).toBe('listTables')
    expect(sessionStorage.getItem('zorth.sql-editor.drafts.v1')).toBeNull()
    expect(store.conversationId).toBe('c-1')
  })

  it('omits conversationId on the first send and reuses the server id afterwards', async () => {
    const store = useCopilotStore()
    await store.send(sampleSend)
    expect(streamAgentMock.mock.calls[0]?.[0]).not.toHaveProperty('conversationId')
    expect(streamAgentMock.mock.calls[0]?.[0]).not.toHaveProperty('userId')
    expect(streamAgentMock.mock.calls[0]?.[0].userText).toBe('列出订单')
    await store.send({
      userText: '没有代码块',
      message: '__NO_SQL__',
      datasourceId: 'ds-orders-a',
      database: 'orders',
    })
    expect(streamAgentMock.mock.calls[1]?.[0].conversationId).toBe('c-1')
    expect(streamAgentMock.mock.calls[1]?.[0]).not.toHaveProperty('userId')
    expect(store.messages).toHaveLength(4)
    expect(store.messages.at(-1)?.content).toContain('没有可插入')
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
    const pending = store.send(sampleSend)
    await vi.waitFor(() => expect(store.inflight).toBe(true))
    store.cancel()
    await pending
    expect(store.inflight).toBe(false)
    expect(store.messages.map((item) => item.role)).toEqual(['user'])
  })

  it('appends streamed deltas onto a live assistant message', async () => {
    const store = useCopilotStore()
    let seen = ''
    streamAgentMock.mockImplementation(async (_body, onEvent) => {
      onEvent({ type: 'delta', content: '用' })
      seen = store.messages.at(-1)?.content || ''
      onEvent({ type: 'delta', content: '下面的语句' })
      onEvent({ type: 'completed', conversationId: 'c-1' })
      return { content: '用下面的语句', conversationId: 'c-1' }
    })
    await store.send(sampleSend)
    expect(seen).toBe('用')
    expect(store.messages.at(-1)?.content).toBe('用下面的语句')
    expect(store.messages.at(-1)?.streaming).toBe(false)
  })

  it('clears local copilot state on reset', async () => {
    const store = useCopilotStore()
    store.show()
    await store.send(sampleSend)
    store.reset()
    expect(store.open).toBe(false)
    expect(store.messages).toEqual([])
    expect(store.conversationId).toBeNull()
    expect(store.conversations).toEqual([])
  })

  it('starts a new thread when the current conversation is gone', async () => {
    const store = useCopilotStore()
    await store.send(sampleSend)
    streamAgentMock.mockImplementationOnce(async () => {
      throw new ApiError(404, {
        requestId: 'r1',
        code: 'CONVERSATION_NOT_FOUND',
        message: '对话不存在',
      })
    })
    streamAgentMock.mockImplementationOnce(async (body, onEvent) => {
      expect(body).not.toHaveProperty('conversationId')
      onEvent({ type: 'start', conversationId: 'c-2' })
      onEvent({ type: 'delta', content: '新的回复' })
      onEvent({ type: 'completed', conversationId: 'c-2' })
      return { content: '新的回复', conversationId: 'c-2' }
    })
    await store.send({
      userText: '再加上过滤',
      message: '再加上过滤',
      datasourceId: 'ds-orders-a',
      database: 'orders',
    })
    expect(store.conversationId).toBe('c-2')
    expect(store.notice).toContain('已开始新对话')
    expect(store.messages.at(-1)?.content).toBe('新的回复')
  })

  it('restores the stored datasource when opening a conversation', async () => {
    getConversationMock.mockResolvedValue({
      id: 'c-9',
      title: '列出订单',
      datasourceId: 'ds-orders-a',
      database: 'orders',
      updatedAt: '2026-08-26T08:00:00Z',
      messages: [
        {
          id: 'm1',
          role: 'user',
          content: '列出订单',
          createdAt: '2026-08-26T08:00:00Z',
        },
      ],
    })
    const store = useCopilotStore()
    await store.openConversation('c-9')
    expect(store.conversationId).toBe('c-9')
    expect(store.datasourceId).toBe('ds-orders-a')
    expect(store.database).toBe('orders')
    expect(store.messages.map((item) => item.content)).toEqual(['列出订单'])
  })
})
