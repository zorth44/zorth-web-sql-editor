import type { ApiErrorBody } from '@/types/contracts'

export class ApiError extends Error {
  readonly status: number
  readonly requestId: string
  readonly code: string
  readonly details: ApiErrorBody['details']

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.requestId = body.requestId
    this.code = body.code
    this.details = body.details
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}

export function isAbortError(error: unknown): boolean {
  return Boolean(error && typeof error === 'object' && 'name' in error && error.name === 'AbortError')
}

export function safeErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (isApiError(error)) return error.message
  return fallback
}
