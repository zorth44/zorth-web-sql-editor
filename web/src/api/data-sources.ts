import { sqlRequest } from '@/api/sql-client'
import {
  mapCreateRequest,
  mapCreateTestRequest,
  mapEditTestRequest,
  mapUpdateRequest,
  type DataSourceFormModel,
} from '@/data-sources/model'
import type {
  ConnectionTestResult,
  CursorPage,
  DataSourceDetail,
  DataSourceListItem,
  DataSourceListParams,
  EngineDescriptor,
} from '@/types/contracts'

export function listDataSources(
  params: DataSourceListParams,
): Promise<CursorPage<DataSourceListItem>> {
  const query = new URLSearchParams({ keyword: params.keyword, pageSize: String(params.pageSize) })
  if (params.pageToken) query.set('pageToken', params.pageToken)
  return sqlRequest(`/api/v1/data-sources?${query}`)
}
export function getDataSource(id: string): Promise<DataSourceDetail> {
  return sqlRequest(`/api/v1/data-sources/${encodeURIComponent(id)}`)
}
export function createDataSource(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): Promise<DataSourceDetail> {
  return sqlRequest('/api/v1/data-sources', {
    method: 'POST',
    body: JSON.stringify(mapCreateRequest(form, descriptor)),
  })
}
export function updateDataSource(
  id: string,
  form: DataSourceFormModel,
  version: number,
  descriptor?: EngineDescriptor,
): Promise<DataSourceDetail> {
  return sqlRequest(`/api/v1/data-sources/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(mapUpdateRequest(form, version, descriptor)),
  })
}
export function deleteDataSource(id: string, version: number): Promise<void> {
  return sqlRequest(`/api/v1/data-sources/${encodeURIComponent(id)}?version=${version}`, {
    method: 'DELETE',
  })
}
export function testCreateForm(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): Promise<ConnectionTestResult> {
  return sqlRequest('/api/v1/data-sources:test', {
    method: 'POST',
    body: JSON.stringify(mapCreateTestRequest(form, descriptor)),
  })
}
export function testEditForm(
  id: string,
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): Promise<ConnectionTestResult> {
  return sqlRequest(`/api/v1/data-sources/${encodeURIComponent(id)}:test`, {
    method: 'POST',
    body: JSON.stringify(mapEditTestRequest(form, descriptor)),
  })
}
export function testSavedDataSource(id: string): Promise<ConnectionTestResult> {
  return sqlRequest(`/api/v1/data-sources/${encodeURIComponent(id)}:test`, { method: 'POST' })
}
