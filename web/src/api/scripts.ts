import { sqlRequest } from '@/api/sql-client'
import type {
  CursorPage,
  ScriptDetail,
  ScriptListParams,
  ScriptSummary,
  ScriptWriteRequest,
} from '@/types/contracts'

export function listScripts(params: ScriptListParams = {}): Promise<CursorPage<ScriptSummary>> {
  const query = new URLSearchParams({
    keyword: params.keyword || '',
    pageSize: String(params.pageSize || 30),
  })
  if (params.dataSourceId) query.set('dataSourceId', params.dataSourceId)
  if (params.database) query.set('database', params.database)
  if (params.pageToken) query.set('pageToken', params.pageToken)
  return sqlRequest(`/api/v1/sql/scripts?${query}`)
}

export function getScript(id: string): Promise<ScriptDetail> {
  return sqlRequest(`/api/v1/sql/scripts/${encodeURIComponent(id)}`)
}

export function createScript(body: ScriptWriteRequest): Promise<ScriptDetail> {
  return sqlRequest('/api/v1/sql/scripts', { method: 'POST', body: JSON.stringify(body) })
}

export function updateScript(id: string, body: ScriptWriteRequest): Promise<ScriptDetail> {
  return sqlRequest(`/api/v1/sql/scripts/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function deleteScript(id: string, version: number): Promise<void> {
  return sqlRequest(
    `/api/v1/sql/scripts/${encodeURIComponent(id)}?version=${encodeURIComponent(String(version))}`,
    { method: 'DELETE' },
  )
}
