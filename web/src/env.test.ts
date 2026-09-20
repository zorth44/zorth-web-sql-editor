import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  applyAppEnv,
  appEnv,
  assertProductionConfig,
  fetchRuntimeConfig,
  loadAppEnv,
  loadRuntimeAppEnv,
  runtimeConfigUrl,
} from '@/env'

function source(overrides: Partial<ImportMetaEnv> = {}): ImportMetaEnv {
  return {
    ...import.meta.env,
    MODE: 'development',
    VITE_SQL_API_BASE: '/sql-api',
    VITE_AUTH_API_BASE: '/auth-api',
    VITE_AI_API_BASE: '/ai-api',
    VITE_AUTH_PRODUCT_TYPE: 'chinaBank',
    VITE_AUTH_BRIDGE_ALLOWED_ORIGINS: 'http://localhost:8080',
    VITE_LEGACY_PORTAL_URL: 'http://localhost:8080/account/bind',
    VITE_ENABLE_API_MOCK: 'true',
    ...overrides,
  }
}

describe('runtime app config', () => {
  afterEach(() => {
    applyAppEnv(loadAppEnv())
  })

  it('builds the config URL from the app base', () => {
    expect(runtimeConfigUrl('/')).toBe('/app-config.json')
    expect(runtimeConfigUrl('/sql-editor/')).toBe('/sql-editor/app-config.json')
    expect(runtimeConfigUrl('/sql-editor')).toBe('/sql-editor/app-config.json')
  })

  it('lets runtime JSON override baked Vite values', () => {
    const env = loadAppEnv(source(), {
      sqlApiBase: '/sql',
      authApiBase: '/auth',
      aiApiBase: '/ai',
      authProductType: 'synthetical',
      legacyPortalUrl: 'http://81.88.161.186:8505/account/bind',
      authBridgeAllowedOrigins: ['http://81.88.161.187:18088'],
    })
    expect(env.sqlApiBase).toBe('/sql')
    expect(env.authApiBase).toBe('/auth')
    expect(env.aiApiBase).toBe('/ai')
    expect(env.authProductType).toBe('synthetical')
    expect(env.legacyPortalUrl).toBe('http://81.88.161.186:8505/account/bind')
    expect([...env.bridgeAllowedOrigins]).toEqual(['http://81.88.161.187:18088'])
  })

  it('ignores runtime mock in production', () => {
    const env = loadAppEnv(source({ MODE: 'production', VITE_ENABLE_API_MOCK: 'false' }), {
      enableApiMock: true,
      sqlApiBase: '/sql',
    })
    expect(env.apiMockEnabled).toBe(false)
  })

  it('rejects incomplete production config', () => {
    expect(() =>
      assertProductionConfig(
        loadAppEnv(source({ MODE: 'production', VITE_LEGACY_PORTAL_URL: '' })),
      ),
    ).toThrow(/legacyPortalUrl/)
  })

  it('applies fetched production config onto appEnv', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        sqlApiBase: '/sql',
        authApiBase: '/auth',
        aiApiBase: '/ai',
        legacyPortalUrl: 'http://portal.example/account/bind',
        authBridgeAllowedOrigins: ['http://portal.example'],
      }),
    })
    await loadRuntimeAppEnv(source({ MODE: 'production', BASE_URL: '/' }), fetchImpl)
    expect(fetchImpl).toHaveBeenCalledWith(
      '/app-config.json',
      expect.objectContaining({ cache: 'no-store' }),
    )
    expect(appEnv.sqlApiBase).toBe('/sql')
    expect(appEnv.legacyPortalUrl).toBe('http://portal.example/account/bind')
    expect(appEnv.bridgeAllowedOrigins.has('http://portal.example')).toBe(true)
  })

  it('returns null when runtime config cannot be read', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('network'))
    await expect(fetchRuntimeConfig('/app-config.json', fetchImpl)).resolves.toBeNull()
  })

  it('fails production boot when app-config.json is missing', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({ ok: false })
    await expect(
      loadRuntimeAppEnv(source({ MODE: 'production', BASE_URL: '/' }), fetchImpl),
    ).rejects.toThrow(/app-config.json/)
  })
})
