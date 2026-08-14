import { describe, expect, it } from 'vitest'
import {
  likelyNeedsDatabase,
  selectPreview,
  selectTableData,
  splitSql,
  statementAt,
} from '@/sql-editor/sql'
describe('SQL selection', () => {
  it('ignores semicolons inside quotes and comments', () => {
    const sql = "select ';' as value; -- ; ignored\nselect `a;b` from t"
    expect(splitSql(sql).map((item) => item.text)).toEqual([
      "select ';' as value",
      '-- ; ignored\nselect `a;b` from t',
    ])
  })
  it('finds the statement around the cursor', () => {
    const sql = 'select 1;\nselect 2;'
    expect(statementAt(sql, 16)?.text).toBe('select 2')
  })
  it('quotes preview identifiers', () => {
    expect(selectPreview('sales', 'order`item')).toContain('`sales`.`order``item`')
    expect(selectPreview('sales', 'order`item')).toContain('LIMIT 100')
    expect(selectTableData('sales', 't')).toBe('SELECT *\nFROM `sales`.`t`')
  })
  it('requires a database only for object access', () => {
    expect(likelyNeedsDatabase('select 1')).toBe(false)
    expect(likelyNeedsDatabase('select * from t')).toBe(true)
    expect(likelyNeedsDatabase('update t set a=1')).toBe(true)
  })
})
