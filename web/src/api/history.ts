import { sqlRequest } from '@/api/sql-client'
import type {
  CursorPage,
  HistoryDetail,
  HistoryListParams,
  HistorySummary,
} from '@/types/contracts'
export function listHistory(params: HistoryListParams): Promise<CursorPage<HistorySummary>> {
  const query = new URLSearchParams({
    keyword: params.keyword || '',
    pageSize: String(params.pageSize || 30),
  })
  if (params.dataSourceId) query.set('dataSourceId', params.dataSourceId)
  if (params.database) query.set('database', params.database)
  if (params.status) query.set('status', params.status)
  if (params.statementType) query.set('statementType', params.statementType)
  if (params.pageToken) query.set('pageToken', params.pageToken)
  return sqlRequest(`/api/v1/sql/history?${query}`)
}
export function getHistory(id: string): Promise<HistoryDetail> {
  return sqlRequest(`/api/v1/sql/history/${encodeURIComponent(id)}`)
}
