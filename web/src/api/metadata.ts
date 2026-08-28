import { sqlRequest } from '@/api/sql-client'
import type { CursorPage, DatabaseItem, TableDetail, TableItem } from '@/types/contracts'

const DATABASE_PAGE_SIZE = '200'
const TABLE_PAGE_SIZE = '200'
const MAX_METADATA_PAGES = 100

export function listDatabases(
  dataSourceId: string,
  keyword = '',
  includeSystem = false,
  pageToken?: string,
): Promise<CursorPage<DatabaseItem>> {
  const query = new URLSearchParams({
    keyword,
    pageSize: DATABASE_PAGE_SIZE,
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
  const query = new URLSearchParams({
    database,
    keyword,
    types: 'TABLE,VIEW',
    pageSize: TABLE_PAGE_SIZE,
  })
  if (pageToken) query.set('pageToken', pageToken)
  return sqlRequest(`/api/v1/data-sources/${encodeURIComponent(dataSourceId)}/tables?${query}`)
}

export function listAllDatabases(
  dataSourceId: string,
  keyword = '',
  includeSystem = false,
): Promise<DatabaseItem[]> {
  return collectPages((pageToken) => listDatabases(dataSourceId, keyword, includeSystem, pageToken))
}

export function listAllTables(
  dataSourceId: string,
  database: string,
  keyword = '',
): Promise<TableItem[]> {
  return collectPages((pageToken) => listTables(dataSourceId, database, keyword, pageToken))
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

async function collectPages<T>(
  fetchPage: (pageToken?: string) => Promise<CursorPage<T>>,
): Promise<T[]> {
  const items: T[] = []
  const seenTokens = new Set<string>()
  let pageToken: string | undefined
  for (let page = 0; page < MAX_METADATA_PAGES; page += 1) {
    const result = await fetchPage(pageToken)
    if (result.items?.length) items.push(...result.items)
    const next = result.nextPageToken?.trim()
    if (!next || seenTokens.has(next)) break
    seenTokens.add(next)
    pageToken = next
  }
  return items
}
