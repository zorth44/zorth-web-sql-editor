import { sqlRequest } from '@/api/sql-client'
import type { CursorPage, DatabaseItem, TableDetail, TableItem } from '@/types/contracts'

export function listDatabases(
  dataSourceId: string,
  keyword = '',
  includeSystem = false,
  pageToken?: string,
): Promise<CursorPage<DatabaseItem>> {
  const query = new URLSearchParams({
    keyword,
    pageSize: '100',
    includeSystem: String(includeSystem),
  })
  if (pageToken) query.set('pageToken', pageToken)
  return sqlRequest(`/api/v1/data-sources/${encodeURIComponent(dataSourceId)}/databases?${query}`)
}
export function listTables(
  dataSourceId: string,
  database: string,
  keyword = '',
  pageToken?: string,
): Promise<CursorPage<TableItem>> {
  const query = new URLSearchParams({ database, keyword, types: 'TABLE,VIEW', pageSize: '200' })
  if (pageToken) query.set('pageToken', pageToken)
  return sqlRequest(`/api/v1/data-sources/${encodeURIComponent(dataSourceId)}/tables?${query}`)
}
export function getTableDetail(
  dataSourceId: string,
  database: string,
  table: string,
): Promise<TableDetail> {
  const query = new URLSearchParams({ database, table })
  return sqlRequest(
    `/api/v1/data-sources/${encodeURIComponent(dataSourceId)}/table-detail?${query}`,
  )
}
