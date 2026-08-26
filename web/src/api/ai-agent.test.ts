import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { runAgent, streamAgent } from '@/api/ai-agent'
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
})
