import { randomUUID } from '@/uuid'
import { scanSql } from '@/sql-editor/sql'
import type { CreateTaskRequest } from './contracts'

export function validateTaskInput(input: CreateTaskRequest): string[] {
  const errors: string[] = []
  if (!input.datasourceId) errors.push('请选择数据源。')
  if (!input.database.trim()) errors.push('请选择数据库 / 命名空间。')
  if (
    Array.from(input.datasourceId + input.database).some(
      (c) => c.charCodeAt(0) < 32 || c.charCodeAt(0) === 127,
    )
  )
    errors.push('数据源坐标不能包含控制字符。')
  if (!input.sql.trim()) errors.push('请输入 SQL。')
  if (input.sql.length > 20000) errors.push('SQL 不能超过 20000 字符。')
  if ((input.name?.trim().length || 0) > 100) errors.push('任务名称不能超过 100 字符。')
  const scanned = scanSql(input.sql)
  if (scanned.reliable && scanned.statements.length > 1)
    errors.push('一次只能扫描一条 SQL，请拆分多语句后提交。')
  return errors
}
export type CreateOperation = { key: string; payload: CreateTaskRequest }
export function createOperation(input: CreateTaskRequest): CreateOperation {
  return { key: randomUUID(), payload: { ...input } }
}
