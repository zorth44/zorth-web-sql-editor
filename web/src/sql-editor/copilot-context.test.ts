import { describe, expect, it } from 'vitest'
import {
  buildCopilotMessage,
  ERROR_CONTEXT_LIMIT,
  MESSAGE_LIMIT,
  SQL_CONTEXT_LIMIT,
} from '@/sql-editor/copilot-context'

describe('copilot context', () => {
  it('puts dialect, connection, current sql and delivery rules before the user text', () => {
    const message = buildCopilotMessage({
      userText: '列出订单',
      dialect: 'MySQL',
      dataSourceName: '订单测试库',
      database: 'orders',
      currentSql: 'select 1',
    })
    expect(message).toContain('方言: MySQL')
    expect(message).toContain('数据源: 订单测试库')
    expect(message).toContain('NAMESPACE: orders')
    expect(message).toContain('当前 SQL:\nselect 1')
    expect(message).toContain('必须放在 sql 代码块里')
    expect(message.endsWith('列出订单')).toBe(true)
    expect(message).not.toContain('失败语句')
  })

  it('clips oversized sql, errors, and the whole message', () => {
    const message = buildCopilotMessage({
      userText: '修一下',
      dialect: 'pgsql',
      dataSourceName: 'ds',
      database: 'public',
      currentSql: 's'.repeat(SQL_CONTEXT_LIMIT + 20),
      failedSql: 'f'.repeat(10),
      failedError: 'e'.repeat(ERROR_CONTEXT_LIMIT + 20),
    })
    expect(message).toContain('失败语句')
    expect(message).toContain('错误:')
    expect(message.length).toBeLessThanOrEqual(MESSAGE_LIMIT)
    expect(message).toContain('…')
  })
})
