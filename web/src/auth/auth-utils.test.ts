import { describe, expect, it } from 'vitest'
import { safeRelativeRedirect } from '@/auth/redirect'
import { TOKEN_KEY, clearToken, getToken, saveToken } from '@/auth/token-storage'
import {
  clearCredentials,
  consumeCredentials,
  retainCredentials,
} from '@/auth/transient-credentials'

describe('Token storage', () => {
  it('stores a non-remembered Token only in session storage', () => {
    localStorage.setItem(TOKEN_KEY, 'old')
    saveToken('session-token', false)
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('session-token')
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })
  it('stores a remembered Token only in local storage and clears both', () => {
    sessionStorage.setItem(TOKEN_KEY, 'old')
    saveToken('local-token', true)
    expect(localStorage.getItem(TOKEN_KEY)).toBe('local-token')
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    clearToken()
    expect(getToken()).toBeNull()
  })
})

describe('safe redirects and volatile credentials', () => {
  it.each(['https://evil.test/x', '//evil.test/x', 'data:text/plain,x', '/login', '/auth/bridge'])(
    'rejects %s',
    (value) => expect(safeRelativeRedirect(value)).toBe('/sql-editor'),
  )
  it('keeps an internal route and never serializes credentials', () => {
    expect(safeRelativeRedirect('/data-sources/ds-1/edit?tab=a')).toBe(
      '/data-sources/ds-1/edit?tab=a',
    )
    retainCredentials('user', 'clear-secret')
    expect(consumeCredentials()).toEqual({ username: 'user', password: 'clear-secret' })
    expect(JSON.stringify(localStorage)).not.toContain('clear-secret')
    expect(JSON.stringify(sessionStorage)).not.toContain('clear-secret')
    clearCredentials()
    expect(consumeCredentials()).toBeNull()
  })
})
