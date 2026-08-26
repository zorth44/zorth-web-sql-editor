import { describe, expect, it } from 'vitest'
import { extractSqlFences, looksLikeSql, splitAssistantContent } from '@/sql-editor/sql-fences'

describe('sql fences', () => {
  it('extracts sql fences and unlabeled sql-like fences', () => {
    const markdown = [
      '先看订单表：',
      '```sql',
      'SELECT id FROM order_item;',
      '```',
      '也可以：',
      '```',
      'select amount from order_item',
      '```',
      '```js',
      'console.log(1)',
      '```',
    ].join('\n')
    expect(extractSqlFences(markdown)).toEqual([
      'SELECT id FROM order_item;',
      'select amount from order_item',
    ])
  })

  it('splits assistant content into text and sql segments', () => {
    const segments = splitAssistantContent('说明\n```mysql\nSELECT 1;\n```\n收尾')
    expect(segments).toEqual([
      { type: 'text', text: '说明' },
      { type: 'sql', sql: 'SELECT 1;' },
      { type: 'text', text: '收尾' },
    ])
  })

  it('does not treat prose as sql', () => {
    expect(looksLikeSql('订单表里没有 amount 字段')).toBe(false)
    expect(extractSqlFences('没有代码块')).toEqual([])
  })
})
