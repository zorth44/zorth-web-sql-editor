import { describe, expect, it } from 'vitest'
import { bridgeOpenerOrigin, isBridgePayload } from '@/auth/bridge'

describe('bridge payload validation', () => {
  it('accepts only the exact message contract', () => {
    expect(isBridgePayload({ type: 'ZORTH_SQL_AUTH_TOKEN', version: 1, token: 'candidate' })).toBe(
      true,
    )
    expect(isBridgePayload({ type: 'ZORTH_SQL_AUTH_TOKEN', version: 2, token: 'candidate' })).toBe(
      false,
    )
    expect(isBridgePayload({ type: 'other', version: 1, token: 'candidate' })).toBe(false)
    expect(isBridgePayload({ type: 'ZORTH_SQL_AUTH_TOKEN', version: 1, token: '' })).toBe(false)
  })
})

describe('bridge opener origin', () => {
  const allowed = new Set(['http://legacy.example.test'])

  it('uses the legacy portal origin when it is allow-listed', () => {
    expect(bridgeOpenerOrigin('http://legacy.example.test/bind', allowed)).toBe(
      'http://legacy.example.test',
    )
  })

  it('does not fall back to a wildcard when the portal origin is missing', () => {
    expect(bridgeOpenerOrigin('http://other.example.test/bind', allowed)).toBeNull()
    expect(bridgeOpenerOrigin('', allowed)).toBeNull()
  })
})
