const AUTH_PATHS = new Set(['/login', '/auth/bridge'])

export function safeRelativeRedirect(value: unknown, fallback = '/sql-editor'): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return fallback
  try {
    const url = new URL(value, 'https://internal.invalid')
    if (url.origin !== 'https://internal.invalid' || AUTH_PATHS.has(url.pathname)) return fallback
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return fallback
  }
}
