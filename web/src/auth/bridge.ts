export interface BridgePayload {
  type: 'ZORTH_SQL_AUTH_TOKEN'
  version: 1
  token: string
}

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
