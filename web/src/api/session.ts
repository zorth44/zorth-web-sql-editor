import { sqlRequest } from '@/api/sql-client'
import type { Session } from '@/types/contracts'

export function fetchSession(): Promise<Session> {
  return sqlRequest('/api/v1/session')
}
export function isSessionValid(session: Session): boolean {
  return Date.parse(session.expiresAt) > Date.now()
}
export function canManageDataSources(session: Session | undefined): boolean {
  return session?.capabilities.includes('DATA_SOURCE_MANAGE') ?? false
}
