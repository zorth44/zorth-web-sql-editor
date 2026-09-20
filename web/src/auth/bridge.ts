export interface BridgePayload {
  type: 'ZORTH_SQL_AUTH_TOKEN'
  version: 1
  token: string
}

export const BRIDGE_READY = { type: 'ZORTH_SQL_AUTH_READY', version: 1 } as const
export const BRIDGE_ACCEPTED = { type: 'ZORTH_SQL_AUTH_ACCEPTED', version: 1 } as const
export const BRIDGE_FAILED = { type: 'ZORTH_SQL_AUTH_FAILED', version: 1 } as const

export function isBridgePayload(value: unknown): value is BridgePayload {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  return (
    item.type === 'ZORTH_SQL_AUTH_TOKEN' &&
    item.version === 1 &&
    typeof item.token === 'string' &&
    item.token.length > 0 &&
    item.token.length <= 4096
  )
}

export function bridgeOpenerOrigin(
  legacyPortalUrl: string,
  allowedOrigins: ReadonlySet<string>,
): string | null {
  try {
    const origin = new URL(legacyPortalUrl).origin
    return allowedOrigins.has(origin) ? origin : null
  } catch {
    return null
  }
}
