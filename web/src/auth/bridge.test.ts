import { describe, expect, it } from 'vitest'
import { isBridgePayload } from '@/auth/bridge'

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
