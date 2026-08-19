import { sqlRequest } from '@/api/sql-client'
import type { EngineCatalog } from '@/types/contracts'

export function listEngines(): Promise<EngineCatalog> {
  return sqlRequest('/api/v1/engines')
}
