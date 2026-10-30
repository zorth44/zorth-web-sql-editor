import { createPinia, setActivePinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { appEnv } from '@/env'
import { server } from '@/mocks/server'
import { queryClient } from '@/query/client'
import { TOKEN_KEY } from '@/auth/token-storage'
import { useAuthStore } from '@/stores/auth'
import { router } from '@/router'

describe('remembered session navigation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    queryClient.clear()
    localStorage.clear()
    sessionStorage.clear()
  })

  it('preserves the token during an outage and restores the session after retry', async () => {
    localStorage.setItem(TOKEN_KEY, 'mock-token')
    server.use(
      http.get(`${appEnv.sqlApiBase}/api/v1/session`, () => new HttpResponse(null, { status: 503 })),
    )
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(localStorage.getItem(TOKEN_KEY)).toBe('mock-token')
    expect(useAuthStore().sessionUnavailable).toBe(true)

    server.resetHandlers()
    await useAuthStore().validateSession({ force: true })
    await router.push('/data-sources')
    expect(router.currentRoute.value.path).toBe('/data-sources')
    expect(useAuthStore().sessionUnavailable).toBe(false)
  })

  it('clears a rejected remembered token', async () => {
    localStorage.setItem(TOKEN_KEY, 'invalid-token')
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })
})
