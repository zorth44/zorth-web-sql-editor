import { describe, expect, it } from 'vitest'
import {
  likelyNeedsDatabase,
  needsSessionAffinity,
  scanSql,
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
  it('matches the backend scanner on delimiters inside quotes and comments', () => {
    expect(splitSql("select ';'; -- ;\n select `a;b` from t").map((item) => item.text)).toEqual([
      "select ';'",
      '-- ;\n select `a;b` from t',
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
    expect(selectTableData('sales', 't', '"')).toBe('SELECT *\nFROM "sales"."t"')
  })
  it('requires a database only for object access', () => {
    expect(likelyNeedsDatabase('select 1')).toBe(false)
    expect(likelyNeedsDatabase('select * from t')).toBe(true)
    expect(likelyNeedsDatabase('update t set a=1')).toBe(true)
  })
})

describe('script scanning', () => {
  it('splits a multi-statement script and drops empty statements', () => {
    const script = scanSql('select 1;\n\n;select 2;\n')
    expect(script.statements.map((item) => item.text)).toEqual(['select 1', 'select 2'])
    expect(script.reliable).toBe(true)
  })
  it('keeps block comments and hash comments attached to their statement', () => {
    const script = scanSql('/* one; two */ select 1;\n# ;\nselect 2')
    expect(script.statements.map((item) => item.text)).toEqual([
      '/* one; two */ select 1',
      '# ;\nselect 2',
    ])
    expect(script.reliable).toBe(true)
  })
  it('reports an unterminated string or identifier as unreliable', () => {
    expect(scanSql("select 'x").reliable).toBe(false)
    expect(scanSql('select `a').reliable).toBe(false)
    expect(scanSql('select "a').reliable).toBe(false)
    expect(scanSql('select 1 /* open').reliable).toBe(false)
  })
  it('treats a trailing line comment as terminated', () => {
    expect(scanSql('select 1; -- done').reliable).toBe(true)
  })
  it('does not split on semicolons inside dollar quotes', () => {
    const script = scanSql('select $tag$ a;b $tag$; select 2')
    expect(script.statements.map((item) => item.text)).toEqual(['select $tag$ a;b $tag$', 'select 2'])
    expect(script.reliable).toBe(true)
  })
  it('detects statements that depend on connection session state', () => {
    expect(needsSessionAffinity('SET @x = 1')).toBe(true)
    expect(needsSessionAffinity('use demo')).toBe(true)
    expect(needsSessionAffinity('create temporary table t (a int)')).toBe(true)
    expect(needsSessionAffinity('-- warm up\n  SET NAMES utf8mb4')).toBe(true)
    expect(needsSessionAffinity('select 1')).toBe(false)
    expect(needsSessionAffinity('update t set a = 1')).toBe(false)
    expect(needsSessionAffinity('create table t (a int)')).toBe(false)
  })
})
