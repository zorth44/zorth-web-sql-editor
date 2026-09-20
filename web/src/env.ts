export interface AppEnv {
  sqlApiBase: string
  authApiBase: string
  aiApiBase: string
  authProductType: 'synthetical' | 'chinaBank' | 'oversea'
  bridgeAllowedOrigins: ReadonlySet<string>
  legacyPortalUrl: string
  apiMockEnabled: boolean
}

export interface RuntimeAppConfig {
  sqlApiBase?: string
  authApiBase?: string
  aiApiBase?: string
  authProductType?: string
  legacyPortalUrl?: string
  authBridgeAllowedOrigins?: string | string[]
  enableApiMock?: boolean
}

const PRODUCT_TYPES = ['synthetical', 'chinaBank', 'oversea'] as const
const RUNTIME_CONFIG_TIMEOUT_MS = 5_000

function validUrl(value: string, label: string, allowRelative = true): string {
  const trimmed = value.trim().replace(/\/$/, '')
  if (!trimmed) return ''
  if (allowRelative && trimmed.startsWith('/')) return trimmed
  try {
    return new URL(trimmed).toString().replace(/\/$/, '')
  } catch {
    throw new Error(`${label} 不是有效 URL`)
  }
}

function originsFrom(value: string | string[] | undefined): string[] {
  const items = Array.isArray(value)
    ? value
    : (value || '')
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean)
  return items.map((item) => new URL(item).origin)
}

function overlayFromJson(value: unknown): RuntimeAppConfig | null {
  if (!value || typeof value !== 'object') return null
  const item = value as Record<string, unknown>
  const overlay: RuntimeAppConfig = {}
  if (typeof item.sqlApiBase === 'string') overlay.sqlApiBase = item.sqlApiBase
  if (typeof item.authApiBase === 'string') overlay.authApiBase = item.authApiBase
  if (typeof item.aiApiBase === 'string') overlay.aiApiBase = item.aiApiBase
  if (typeof item.authProductType === 'string') overlay.authProductType = item.authProductType
  if (typeof item.legacyPortalUrl === 'string') overlay.legacyPortalUrl = item.legacyPortalUrl
  if (typeof item.authBridgeAllowedOrigins === 'string')
    overlay.authBridgeAllowedOrigins = item.authBridgeAllowedOrigins
  else if (Array.isArray(item.authBridgeAllowedOrigins))
    overlay.authBridgeAllowedOrigins = item.authBridgeAllowedOrigins.filter(
      (origin): origin is string => typeof origin === 'string',
    )
  if (typeof item.enableApiMock === 'boolean') overlay.enableApiMock = item.enableApiMock
  return overlay
}

export function loadAppEnv(
  source: ImportMetaEnv = import.meta.env,
  overlay: RuntimeAppConfig | null = null,
): AppEnv {
  const productType = overlay?.authProductType || source.VITE_AUTH_PRODUCT_TYPE || 'chinaBank'
  if (!PRODUCT_TYPES.includes(productType as (typeof PRODUCT_TYPES)[number]))
    throw new Error('VITE_AUTH_PRODUCT_TYPE 配置无效')
  const productionMode = source.MODE === 'production'
  const apiMockEnabled =
    !productionMode &&
    (overlay?.enableApiMock === true ||
      (overlay?.enableApiMock !== false && source.VITE_ENABLE_API_MOCK === 'true'))
  const legacyPortalUrl = validUrl(
    overlay?.legacyPortalUrl ?? source.VITE_LEGACY_PORTAL_URL ?? '',
    'legacyPortalUrl',
    !productionMode,
  )
  const originSource = overlay?.authBridgeAllowedOrigins ?? source.VITE_AUTH_BRIDGE_ALLOWED_ORIGINS
  return {
    sqlApiBase: validUrl(overlay?.sqlApiBase ?? source.VITE_SQL_API_BASE ?? '', 'sqlApiBase'),
    authApiBase: validUrl(overlay?.authApiBase ?? source.VITE_AUTH_API_BASE ?? '', 'authApiBase'),
    aiApiBase: validUrl(overlay?.aiApiBase ?? source.VITE_AI_API_BASE ?? '', 'aiApiBase'),
    authProductType: productType as AppEnv['authProductType'],
    bridgeAllowedOrigins: new Set(originsFrom(originSource)),
    legacyPortalUrl,
    apiMockEnabled,
  }
}

export function assertProductionConfig(env: AppEnv): void {
  if (env.apiMockEnabled) throw new Error('生产环境禁止启用 API Mock')
  if (!env.sqlApiBase) throw new Error('生产环境缺少 sqlApiBase')
  if (!env.authApiBase) throw new Error('生产环境缺少 authApiBase')
  if (!env.aiApiBase) throw new Error('生产环境缺少 aiApiBase')
  if (!env.legacyPortalUrl) throw new Error('生产环境缺少 legacyPortalUrl')
  if (!env.bridgeAllowedOrigins.size) throw new Error('生产环境缺少 authBridgeAllowedOrigins')
}

function assignAppEnv(target: AppEnv, source: AppEnv): void {
  target.sqlApiBase = source.sqlApiBase
  target.authApiBase = source.authApiBase
  target.aiApiBase = source.aiApiBase
  target.authProductType = source.authProductType
  target.bridgeAllowedOrigins = source.bridgeAllowedOrigins
  target.legacyPortalUrl = source.legacyPortalUrl
  target.apiMockEnabled = source.apiMockEnabled
}

export const appEnv: AppEnv = loadAppEnv()

export function applyAppEnv(next: AppEnv): void {
  assignAppEnv(appEnv, next)
}

export function runtimeConfigUrl(baseUrl = import.meta.env.BASE_URL): string {
  const prefix = !baseUrl ? '/' : baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`
  return `${prefix}app-config.json`
}

export async function fetchRuntimeConfig(
  url = runtimeConfigUrl(),
  fetchImpl: typeof fetch = fetch,
): Promise<RuntimeAppConfig | null> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), RUNTIME_CONFIG_TIMEOUT_MS)
  try {
    const response = await fetchImpl(url, { cache: 'no-store', signal: controller.signal })
    if (!response.ok) return null
    return overlayFromJson(await response.json())
  } catch {
    return null
  } finally {
    clearTimeout(timeout)
  }
}

export async function loadRuntimeAppEnv(
  source: ImportMetaEnv = import.meta.env,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  if (source.MODE !== 'production') return
  const overlay = await fetchRuntimeConfig(runtimeConfigUrl(source.BASE_URL), fetchImpl)
  if (!overlay) throw new Error('无法读取 /app-config.json，请确认该路径返回 JSON 而不是 HTML')
  applyAppEnv(loadAppEnv(source, overlay))
  assertProductionConfig(appEnv)
}
