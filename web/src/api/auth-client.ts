import { appEnv } from '@/env'
import { encodeLdapPassword } from '@/auth/password'
import { getToken } from '@/auth/token-storage'
import type { BoundAccount, LoginResult } from '@/types/contracts'

interface LoginInput {
  username: string
  password: string
  selectUserId?: string
}

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function sanitizeAccounts(value: unknown): BoundAccount[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((entry) => {
    const item = record(entry)
    if (!item) return []
    const id = stringValue(item.id || item.userId)
    if (!id) return []
    return [
      {
        id,
        username: stringValue(item.username || item.userName),
        displayName: stringValue(item.displayName || item.nickName || item.username),
      },
    ]
  })
}

async function readAjaxResult(response: Response): Promise<Record<string, unknown>> {
  const raw: unknown = await response.json().catch(() => null)
  const body = record(raw)
  if (!response.ok || !body) throw new Error('授权服务暂时不可用')
  if (body.code !== 200) throw new Error(stringValue(body.msg) || '登录失败')
  return body
}

export async function login(input: LoginInput): Promise<LoginResult> {
  const payload: Record<string, string> = {
    username: input.username,
    password: encodeLdapPassword(input.password),
    productType: appEnv.authProductType,
  }
  if (input.selectUserId) payload.selectUserId = input.selectUserId
  const body = await readAjaxResult(
    await fetch(`${appEnv.authApiBase}/ldap/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  )
  const token = stringValue(body.token)
  if (token) return { kind: 'authenticated', token }
  if (body.needSelectAccount === true)
    return { kind: 'select-account', accounts: sanitizeAccounts(body.bindAccounts) }
  if (body.needBind === true) return { kind: 'binding-required' }
  throw new Error('授权服务返回了无法识别的登录结果')
}

export async function logoutRemote(): Promise<void> {
  const token = getToken()
  const headers = new Headers()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${appEnv.authApiBase}/logout`, {
    method: 'POST',
    headers,
  })
  await readAjaxResult(response)
}
