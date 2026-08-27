import { afterEach, describe, expect, it, vi } from 'vitest'
import { randomUUID } from '@/uuid'

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

describe('randomUUID', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('uses crypto.randomUUID when available', () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111')
    expect(randomUUID()).toBe('11111111-1111-4111-8111-111111111111')
  })

  it('falls back to getRandomValues when randomUUID is missing', () => {
    const getRandomValues = vi.fn((array: ArrayBufferView) => {
      new Uint8Array(array.buffer, array.byteOffset, array.byteLength).fill(0)
      return array
    })
    vi.stubGlobal('crypto', { getRandomValues })
    expect(randomUUID()).toBe('00000000-0000-4000-8000-000000000000')
    expect(getRandomValues).toHaveBeenCalled()
  })

  it('falls back without Web Crypto', () => {
    vi.stubGlobal('crypto', undefined)
    expect(randomUUID()).toMatch(UUID_V4)
  })
})
