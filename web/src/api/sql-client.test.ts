import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { setUnauthorizedHandler, sqlRequest } from '@/api/sql-client'
import { appEnv } from '@/env'
import { server } from '@/mocks/server'
import { saveToken } from '@/auth/token-storage'

describe('SQL transport', () => {
  it('adds Bearer and UUID request headers', async () => {
    saveToken('header-token', false)
    server.use(
      http.get(`${appEnv.sqlApiBase}/header-check`, ({ request }) =>
        HttpResponse.json({
          authorization: request.headers.get('Authorization'),
          requestId: request.headers.get('X-Request-Id'),
        }),
      ),
    )
    const body = await sqlRequest<{ authorization: string; requestId: string }>('/header-check')
    expect(body.authorization).toBe('Bearer header-token')
    expect(body.requestId).toMatch(/^[0-9a-f-]{36}$/)
  })
  it('coordinates simultaneous 401 teardown once', async () => {
    saveToken('invalid-token', false)
    const teardown = vi.fn(async () => await Promise.resolve())
    setUnauthorizedHandler(teardown)
    await Promise.allSettled([sqlRequest('/api/v1/session'), sqlRequest('/api/v1/session')])
    expect(teardown).toHaveBeenCalledTimes(1)
  })
})
