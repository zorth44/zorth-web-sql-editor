import { describe, expect, it, vi } from 'vitest'
import { encodeLdapPassword } from '@/auth/password'

describe('LDAP password compatibility encoder', () => {
  it('encodes UTF-8 bytes and adds exactly 12 random characters', () => {
    vi.spyOn(crypto, 'getRandomValues').mockImplementation((array) => {
      ;(array as Uint8Array).fill(0)
      return array
    })
    const encoded = encodeLdapPassword('密码päss')
    const expected = btoa(String.fromCharCode(...new TextEncoder().encode('密码päss')))
    expect(encoded.slice(0, -12)).toBe(expected)
    expect(encoded.slice(-12)).toBe('A'.repeat(12))
  })
})
