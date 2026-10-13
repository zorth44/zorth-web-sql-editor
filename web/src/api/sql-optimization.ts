import { sqlFetch, sqlRequest } from '@/api/sql-client'
import type {
  AcceptedTask,
  CreateTaskRequest,
  TaskDetail,
  TaskListResponse,
  TaskStatus,
} from '@/sql-optimizer/contracts'

export const tasksPath = '/api/v1/sql-optimization/tasks'
async function accepted(
  path: string,
  key: string,
  payload?: CreateTaskRequest,
): Promise<AcceptedTask> {
  const response = await sqlFetch(path, {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    ...(payload ? { body: JSON.stringify(payload) } : {}),
  })
  if (response.status !== 202) throw new Error('任务提交响应格式异常')
  const value = (await response.json()) as AcceptedTask
  if (!value.id || !['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(value.status))
    throw new Error('任务提交响应格式异常')
  return value
}
export function createOptimizationTask(
  payload: CreateTaskRequest,
  key: string,
): Promise<AcceptedTask> {
  return accepted(tasksPath, key, payload)
}
export function retryOptimizationTask(id: string, key: string): Promise<AcceptedTask> {
  return accepted(`${tasksPath}/${encodeURIComponent(id)}/retry`, key)
}
export function listOptimizationTasks(
  params: { status?: TaskStatus; pageSize?: number; pageToken?: string },
  signal?: AbortSignal,
): Promise<TaskListResponse> {
  const query = new URLSearchParams({ pageSize: String(params.pageSize ?? 20) })
  if (params.status) query.set('status', params.status)
  if (params.pageToken) query.set('pageToken', params.pageToken)
  return sqlRequest(`${tasksPath}?${query}`, signal ? { signal } : {})
}
export function getOptimizationTask(id: string, signal?: AbortSignal): Promise<TaskDetail> {
  return sqlRequest(`${tasksPath}/${encodeURIComponent(id)}`, signal ? { signal } : {})
}
