import { appEnv } from '@/env'
import { clearToken, getToken } from '@/auth/token-storage'
import { ApiError, isAbortError } from '@/api/api-error'
import type { ApiErrorBody } from '@/types/contracts'
import { randomUUID } from '@/uuid'

let unauthorizedHandler: (() => Promise<void> | void) | undefined
let unauthorizedFlight: Promise<void> | null = null

export function setUnauthorizedHandler(handler: () => Promise<void> | void): void {
  unauthorizedHandler = handler
}

async function runUnauthorizedHandler(): Promise<void> {
  if (!unauthorizedFlight) {
    unauthorizedFlight = Promise.resolve()
      .then(() => {
        clearToken()
        return unauthorizedHandler?.()
      })
      .finally(() => {
        unauthorizedFlight = null
      })
  }
  return unauthorizedFlight
}

function fallbackError(response: Response): ApiErrorBody {
  return {
    requestId: response.headers.get('X-Request-Id') || randomUUID(),
    code: `HTTP_${response.status}`,
    message: response.status >= 500 ? '服务暂时不可用' : '请求失败',
  }
}

export async function bearerFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken()
  const headers = new Headers(init.headers)
  headers.set('X-Request-Id', randomUUID())
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body !== undefined) headers.set('Content-Type', 'application/json')
  let response: Response
  try {
    response = await fetch(url, { ...init, headers })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new ApiError(0, {
      requestId: headers.get('X-Request-Id')!,
      code: 'NETWORK_ERROR',
      message: '网络连接失败，请检查网络后重试',
    })
  }
  if (response.status === 401) await runUnauthorizedHandler()
  if (!response.ok) {
    const raw: unknown = await response.json().catch(() => null)
    const body = raw && typeof raw === 'object' ? (raw as ApiErrorBody) : fallbackError(response)
    throw new ApiError(response.status, { ...fallbackError(response), ...body })
  }
  return response
}

export async function sqlFetch(path: string, init: RequestInit = {}): Promise<Response> {
  return bearerFetch(`${appEnv.sqlApiBase}${path}`, init)
}

export async function sqlRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await sqlFetch(path, init)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
