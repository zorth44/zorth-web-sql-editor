import { sqlFetch, sqlRequest } from '@/api/sql-client'
import type { SqlExecutionRequest, SqlExecutionResult } from '@/types/contracts'

export function executeSql(
  request: SqlExecutionRequest,
  signal?: AbortSignal,
): Promise<SqlExecutionResult> {
  return sqlRequest('/api/v1/sql/executions', {
    method: 'POST',
    body: JSON.stringify(request),
    ...(signal ? { signal } : {}),
  })
}
export function cancelExecution(executionId: string): Promise<void> {
  return sqlRequest(`/api/v1/sql/executions/${encodeURIComponent(executionId)}:cancel`, {
    method: 'POST',
  })
}
export async function exportExecution(
  executionId: string,
  rowLimit: number,
  signal?: AbortSignal,
): Promise<{ blob: Blob; filename: string }> {
  const response = await sqlFetch('/api/v1/sql/exports', {
    method: 'POST',
    body: JSON.stringify({ executionId, rowLimit }),
    headers: { Accept: 'text/csv' },
    ...(signal ? { signal } : {}),
  })
  const disposition = response.headers.get('Content-Disposition') || ''
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const quoted = disposition.match(/filename="([^"]+)"/i)?.[1]
  return {
    blob: await response.blob(),
    filename: encoded ? decodeURIComponent(encoded) : quoted || 'sql-export.csv',
  }
}
