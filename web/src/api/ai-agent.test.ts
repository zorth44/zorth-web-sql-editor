import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import {
  deleteConversation,
  getConversation,
  listConversations,
  runAgent,
  streamAgent,
} from '@/api/ai-agent'
import { appEnv } from '@/env'
import { server } from '@/mocks/server'
import { saveToken } from '@/auth/token-storage'
import { ApiError } from '@/api/api-error'

describe('AI agent client', () => {
  it('posts to the agent endpoint with the same Bearer token', async () => {
    saveToken('header-token', false)
    let authorization = ''
    server.use(
      http.post(`${appEnv.aiApiBase}/api/v1/ai/agent`, ({ request }) => {
        authorization = request.headers.get('Authorization') || ''
        return HttpResponse.json({ content: '```sql\nselect 1;\n```', conversationId: 'c1' })
      }),
    )
    const body = await runAgent({
      message: '列出订单',
      conversationId: 'tab-1',
      datasourceId: 'ds-1',
      database: 'orders',
    })
    expect(authorization).toBe('Bearer header-token')
    expect(body.content).toContain('select 1')
  })

  it('surfaces validation failures', async () => {
    saveToken('mock-token', false)
    await expect(
      runAgent({ message: '', datasourceId: 'ds-1', database: 'orders' }),
    ).rejects.toMatchObject({ code: 'VALIDATION_FAILED' } satisfies Partial<ApiError>)
  })

  it('returns a sql fence from the default mock', async () => {
    saveToken('mock-token', false)
    const body = await runAgent({
      message: '列出订单',
      conversationId: 'tab-1',
      datasourceId: 'ds-orders-a',
      database: 'orders',
    })
    expect(body.content).toContain('```sql')
  })

  it('streams tool progress then token deltas', async () => {
    saveToken('header-token', false)
    const events: string[] = []
    const body = await streamAgent(
      {
        message: '列出订单',
        conversationId: 'tab-1',
        datasourceId: 'ds-1',
        database: 'orders',
      },
      (event) => {
        if (event.type === 'tool') events.push(`${event.toolName}:${event.status}`)
        if (event.type === 'delta') events.push(`delta:${event.content.slice(0, 4)}`)
      },
    )
    expect(events).toContain('listTables:STARTED')
    expect(events).toContain('listTables:SUCCESS')
    expect(events.some((item) => item.startsWith('delta:'))).toBe(true)
    expect(body.content).toContain('```sql')
  })

  it('stores userText, lists by user, and follows up with the previous sentence', async () => {
    saveToken('mock-token', false)
    const first = await streamAgent(
      {
        message: '【编辑器上下文】\n当前 SQL:\nSELECT 1\n【用户】\n列出订单',
        userText: '列出订单',
        datasourceId: 'ds-orders-a',
        database: 'orders',
      },
      () => undefined,
    )
    if (!first.conversationId) throw new Error('missing conversation id')
    const second = await streamAgent(
      {
        message: '【编辑器上下文】\n【用户】\n加上时间过滤',
        userText: '加上时间过滤',
        conversationId: first.conversationId,
        datasourceId: 'ds-orders-a',
        database: 'orders',
      },
      () => undefined,
    )
    expect(second.content).toContain('列出订单')
    const listed = await listConversations()
    expect(listed.items).toHaveLength(1)
    expect(listed.items[0]?.title).toBe('列出订单')
    const detail = await getConversation(listed.items[0]!.id)
    expect(detail.messages.map((item) => item.content)).toEqual(
      expect.arrayContaining(['列出订单', '加上时间过滤']),
    )
    expect(JSON.stringify(detail)).not.toContain('userId')
  })

  it('keeps conversation lists isolated by the mock user token', async () => {
    saveToken('mock-token', false)
    await streamAgent(
      {
        message: '列出订单',
        userText: '列出订单',
        datasourceId: 'ds-orders-a',
        database: 'orders',
      },
      () => undefined,
    )
    const mine = await listConversations()
    expect(mine.items).toHaveLength(1)
    saveToken('other-token', false)
    expect(await listConversations()).toEqual({ items: [] })
    await expect(getConversation(mine.items[0]!.id)).rejects.toMatchObject({
      code: 'CONVERSATION_NOT_FOUND',
    } satisfies Partial<ApiError>)
    await expect(deleteConversation(mine.items[0]!.id)).rejects.toMatchObject({
      code: 'CONVERSATION_NOT_FOUND',
    } satisfies Partial<ApiError>)
    saveToken('mock-token', false)
    await deleteConversation(mine.items[0]!.id)
    expect(await listConversations()).toEqual({ items: [] })
  })

  it('normalizes the live AI list array and tool name field', async () => {
    saveToken('header-token', false)
    server.use(
      http.get(`${appEnv.aiApiBase}/api/v1/ai/agent/conversations`, () =>
        HttpResponse.json([
          {
            id: 'conv-a',
            title: '列出订单',
            datasourceId: 'ds-1',
            database: 'orders',
            updatedAt: '2026-08-26T08:00:00Z',
          },
        ]),
      ),
      http.get(`${appEnv.aiApiBase}/api/v1/ai/agent/conversations/:id`, () =>
        HttpResponse.json({
          id: 'conv-a',
          title: '列出订单',
          datasourceId: 'ds-1',
          database: 'orders',
          updatedAt: '2026-08-26T08:00:00Z',
          messages: [
            {
              id: 'm1',
              role: 'user',
              content: '列出订单',
              createdAt: '2026-08-26T08:00:00Z',
            },
            {
              id: 'm2',
              role: 'assistant',
              content: '如下',
              tools: [{ name: 'listTables', status: 'SUCCESS' }],
              createdAt: '2026-08-26T08:00:01Z',
            },
          ],
        }),
      ),
    )
    const listed = await listConversations()
    expect(listed.items).toEqual([
      {
        id: 'conv-a',
        title: '列出订单',
        datasourceId: 'ds-1',
        database: 'orders',
        updatedAt: '2026-08-26T08:00:00Z',
      },
    ])
    const detail = await getConversation('conv-a')
    expect(detail.messages[1]?.tools?.[0]).toMatchObject({
      toolName: 'listTables',
      status: 'SUCCESS',
    })
    expect(detail.messages[1]?.tools?.[0]?.id).toBeTruthy()
  })
})
