export interface AppEnv {
  sqlApiBase: string
  authApiBase: string
  aiApiBase: string
  authProductType: 'synthetical' | 'chinaBank' | 'oversea'
  bridgeAllowedOrigins: ReadonlySet<string>
  legacyPortalUrl: string
  apiMockEnabled: boolean
}

const PRODUCT_TYPES = ['synthetical', 'chinaBank', 'oversea'] as const

function validUrl(value: string, label: string, allowRelative = true): string {
  const trimmed = value.trim().replace(/\/$/, '')
  if (!trimmed && allowRelative) return ''
  if (allowRelative && trimmed.startsWith('/')) return trimmed
  try {
    return new URL(trimmed).toString().replace(/\/$/, '')
  } catch {
    throw new Error(`${label} 不是有效 URL`)
  }
}

export function loadAppEnv(source: ImportMetaEnv = import.meta.env): AppEnv {
  const productType = source.VITE_AUTH_PRODUCT_TYPE || 'chinaBank'
  if (!PRODUCT_TYPES.includes(productType as (typeof PRODUCT_TYPES)[number]))
    throw new Error('VITE_AUTH_PRODUCT_TYPE 配置无效')
  const apiMockEnabled = source.VITE_ENABLE_API_MOCK === 'true'
  const productionMode = source.MODE === 'production'
  if (productionMode && apiMockEnabled) throw new Error('生产环境禁止启用 API Mock')
  const legacyPortalUrl = validUrl(
    source.VITE_LEGACY_PORTAL_URL || '',
    'VITE_LEGACY_PORTAL_URL',
    !productionMode,
  )
  if (productionMode && !legacyPortalUrl) throw new Error('生产环境缺少 VITE_LEGACY_PORTAL_URL')
  const origins = (source.VITE_AUTH_BRIDGE_ALLOWED_ORIGINS || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => new URL(item).origin)
  return {
    sqlApiBase: validUrl(source.VITE_SQL_API_BASE || '', 'VITE_SQL_API_BASE'),
    authApiBase: validUrl(source.VITE_AUTH_API_BASE || '', 'VITE_AUTH_API_BASE'),
    aiApiBase: validUrl(source.VITE_AI_API_BASE || '', 'VITE_AI_API_BASE'),
    authProductType: productType as AppEnv['authProductType'],
    bridgeAllowedOrigins: new Set(origins),
    legacyPortalUrl,
    apiMockEnabled,
  }
}

export const appEnv = loadAppEnv()
