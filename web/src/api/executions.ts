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
  signal?: AbortSignal | undefined,
  writable?: WritableStream<Uint8Array> | undefined,
): Promise<{ blob: Blob | null; filename: string }> {
  let owned = writable
  try {
    const response = await sqlFetch('/api/v1/sql/exports', {
      method: 'POST',
      body: JSON.stringify({ executionId, rowLimit }),
      headers: { Accept: 'text/csv' },
      ...(signal ? { signal } : {}),
    })
    const filename = filenameFromDisposition(response.headers.get('Content-Disposition'))
    if (!owned) {
      return { blob: await response.blob(), filename }
    }
    if (!response.body) throw new TypeError('CSV 响应没有可读流')
    const dest = owned
    owned = undefined
    await response.body.pipeTo(dest, signal ? { signal } : {})
    return { blob: null, filename }
  } finally {
    if (owned) await owned.abort().catch(() => undefined)
  }
}

function filenameFromDisposition(disposition: string | null): string {
  const header = disposition || ''
  const encoded = header.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const quoted = header.match(/filename="([^"]+)"/i)?.[1]
  return encoded ? decodeURIComponent(encoded) : quoted || 'sql-export.csv'
}
