import { createPinia, setActivePinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { appEnv } from '@/env'
import { server } from '@/mocks/server'
import { queryClient } from '@/query/client'
import { useAuthStore } from '@/stores/auth'
import { TOKEN_KEY } from '@/auth/token-storage'
import { canManageDataSources, isSessionValid } from '@/api/session'

describe('auth store and Session', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    queryClient.clear()
  })

  it('retains credentials only for account selection and stores no raw response fields', async () => {
    const store = useAuthStore()
    expect(await store.submitLogin('multi', 'ldap-secret', false)).toBe('select-account')
    expect(store.accounts).toHaveLength(2)
    expect(JSON.stringify(store.$state)).not.toContain('ldap-secret')
    expect(JSON.stringify(store.$state)).not.toContain('ldapUser')
    await store.selectAccount('1001')
    expect(store.session?.user.username).toBe('zhangsan')
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('mock-token')
  })

  it('clears local state even when remote logout fails', async () => {
    server.use(
      http.post(`${appEnv.authApiBase}/logout`, () =>
        HttpResponse.json({ code: 500, msg: 'remote failed' }),
      ),
    )
    const store = useAuthStore()
    await store.submitLogin('normal', 'secret', true)
    await store.logout()
    expect(store.session).toBeNull()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('evaluates expiry and management capability', () => {
    const session = {
      user: { id: '1', username: 'u', displayName: 'U' },
      product: { id: 'p', name: 'P' },
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      capabilities: ['DATA_SOURCE_MANAGE'] as const,
    }
    expect(isSessionValid({ ...session, capabilities: [...session.capabilities] })).toBe(true)
    expect(canManageDataSources({ ...session, capabilities: [...session.capabilities] })).toBe(true)
    expect(
      isSessionValid({
        ...session,
        expiresAt: '2000-01-01T00:00:00Z',
        capabilities: [...session.capabilities],
      }),
    ).toBe(false)
    expect(canManageDataSources({ ...session, capabilities: [] })).toBe(false)
  })
})
