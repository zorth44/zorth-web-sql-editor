import { beforeEach, describe, expect, it } from 'vitest'
import { saveToken } from '@/auth/token-storage'
import { executeSql, exportExecution } from '@/api/executions'
import { getHistory, listHistory } from '@/api/history'
import { getTableDetail, listDatabases, listTables } from '@/api/metadata'

beforeEach(() => saveToken('mock-token', false))
describe('phase-two API contracts', () => {
  it('uses frozen metadata shapes', async () => {
    const databases = await listDatabases('ds-orders-a')
    expect(databases.items.map((item) => item.name)).toContain('orders')
    expect(databases.items.every((item) => item.kind === 'NAMESPACE')).toBe(true)
    expect((await listTables('ds-orders-a', 'orders')).items[0]?.type).toBe('TABLE')
    expect(
      (await getTableDetail('ds-orders-a', 'orders', 'order_item')).primaryKey?.columns,
    ).toEqual(['id'])
    expect((await getTableDetail('ds-orders-a', 'orders', 'order_item')).ddl).toContain(
      'CREATE TABLE `order_item`',
    )
  })
  it('executes, exports, and reopens current-user history', async () => {
    const executionId = crypto.randomUUID()
    const result = await executeSql({
      executionId,
      dataSourceId: 'ds-orders-a',
      database: 'orders',
      statement: 'select 1',
    })
    expect(result.kind).toBe('RESULT_SET')
    const csv = await exportExecution(executionId, 100)
    expect(csv.filename).toBe('mock-orders.csv')
    expect(await csv.blob.text()).toContain("'=SUM")
    const list = await listHistory({ keyword: 'select 1' })
    expect(list.items[0]?.id).toBe(executionId)
    expect((await getHistory(executionId)).connectionAvailable).toBe(true)
  })
})
