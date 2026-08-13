export const TOKEN_KEY = 'zorth.sql.token'

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
}

export function saveToken(token: string, remember: boolean): void {
  clearToken()
  ;(remember ? localStorage : sessionStorage).setItem(TOKEN_KEY, token)
}

export function getToken(): string | null {
  const local = localStorage.getItem(TOKEN_KEY)
  const session = sessionStorage.getItem(TOKEN_KEY)
  if (local && session) sessionStorage.removeItem(TOKEN_KEY)
  return local || session
}
