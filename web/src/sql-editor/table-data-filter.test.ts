import { describe, expect, it } from 'vitest'
import { columnTypeKind } from '@/components/result-grid/column-type'
import {
  buildTableDataSql,
  compileTableDataFilters,
  escapeLikeValue,
  parseTableDataFilter,
  quoteSqlLiteral,
} from '@/sql-editor/table-data-filter'

describe('table-data filter parser', () => {
  it('skips empty drafts', () => {
    expect(parseTableDataFilter('  ', 'note', 'string')).toEqual({ ok: true, predicate: null })
  })

  it('parses NULL and NOT NULL regardless of case', () => {
    expect(parseTableDataFilter('null', 'note', 'string')).toEqual({
      ok: true,
      predicate: { column: 'note', type: 'null' },
    })
    expect(parseTableDataFilter('NOT NULL', 'note', 'binary')).toEqual({
      ok: true,
      predicate: { column: 'note', type: 'not-null' },
    })
  })

  it('uses LIKE for bare string text and equality for numbers, dates, and booleans', () => {
    expect(parseTableDataFilter('FAILED', 'status', 'string')).toEqual({
      ok: true,
      predicate: { column: 'status', type: 'like', value: 'FAILED' },
    })
    expect(parseTableDataFilter('12.5', 'amount', 'number')).toEqual({
      ok: true,
      predicate: {
        column: 'amount',
        type: 'compare',
        op: '=',
        valueKind: 'number',
        value: '12.5',
      },
    })
    expect(parseTableDataFilter('2026-01-01', 'created_at', 'date')).toEqual({
      ok: true,
      predicate: {
        column: 'created_at',
        type: 'compare',
        op: '=',
        valueKind: 'string',
        value: '2026-01-01',
      },
    })
    expect(parseTableDataFilter('true', 'flag', 'boolean')).toEqual({
      ok: true,
      predicate: {
        column: 'flag',
        type: 'compare',
        op: '=',
        valueKind: 'boolean',
        value: 'TRUE',
      },
    })
  })

  it('parses comparison prefixes including != as <>', () => {
    expect(parseTableDataFilter('>=10', 'amount', 'number')).toMatchObject({
      ok: true,
      predicate: { op: '>=', value: '10' },
    })
    expect(parseTableDataFilter('!=x', 'note', 'string')).toMatchObject({
      ok: true,
      predicate: { op: '<>', valueKind: 'string', value: 'x' },
    })
  })

  it('rejects invalid number, boolean, and binary drafts', () => {
    expect(parseTableDataFilter('>abc', 'amount', 'number')).toEqual({
      ok: false,
      error: '数字列需要有效数字',
    })
    expect(parseTableDataFilter('yes', 'flag', 'boolean')).toEqual({
      ok: false,
      error: '布尔列只接受 true、false、1、0',
    })
    expect(parseTableDataFilter('abc', 'bin', 'binary')).toEqual({
      ok: false,
      error: '二进制列只支持空、NULL、NOT NULL',
    })
    expect(parseTableDataFilter('>=', 'amount', 'number')).toEqual({
      ok: false,
      error: '比较运算符后面需要值',
    })
  })
})

describe('table-data SQL builder', () => {
  it('quotes string literals by doubling quotes', () => {
    expect(quoteSqlLiteral("O'Hara")).toBe("'O''Hara'")
  })

  it('escapes LIKE wildcards with /', () => {
    expect(escapeLikeValue('100%_off/x')).toBe('100/%/_off//x')
  })

  it('omits WHERE and ORDER BY when empty', () => {
    expect(buildTableDataSql({ database: 'sales', table: 't' })).toBe('SELECT *\nFROM `sales`.`t`')
  })

  it('builds AND-combined WHERE and a single ORDER BY with identifier quoting', () => {
    const compiled = compileTableDataFilters({ status: 'FAILED', amount: '>100' }, [
      { name: 'status', jdbcType: 'VARCHAR' },
      { name: 'amount', jdbcType: 'DECIMAL' },
    ])
    expect(compiled.ok).toBe(true)
    if (!compiled.ok) return
    expect(
      buildTableDataSql({
        database: 'sales',
        table: 'order_item',
        quote: '"',
        predicates: compiled.predicates,
        sort: { column: 'created_at', dir: 'desc' },
      }),
    ).toBe(
      [
        'SELECT *',
        'FROM "sales"."order_item"',
        `WHERE "status" LIKE '%FAILED%' ESCAPE '/' AND "amount" > 100`,
        'ORDER BY "created_at" DESC',
      ].join('\n'),
    )
  })

  it('escapes LIKE metacharacters and quotes string compare values', () => {
    const like = parseTableDataFilter('a%b', 'note', columnTypeKind('VARCHAR'))
    const equals = parseTableDataFilter("=O'Hara", 'name', 'string')
    expect(like.ok && like.predicate).toBeTruthy()
    expect(equals.ok && equals.predicate).toBeTruthy()
    if (!like.ok || !like.predicate || !equals.ok || !equals.predicate) return
    expect(
      buildTableDataSql({
        database: 'sales',
        table: 't',
        predicates: [like.predicate, equals.predicate],
      }),
    ).toBe(
      [
        'SELECT *',
        'FROM `sales`.`t`',
        `WHERE \`note\` LIKE '%a/%b%' ESCAPE '/' AND \`name\` = 'O''Hara'`,
      ].join('\n'),
    )
  })

  it('returns per-column errors without compiling predicates', () => {
    const compiled = compileTableDataFilters({ amount: '>abc', flag: 'yes' }, [
      { name: 'amount', jdbcType: 'DECIMAL' },
      { name: 'flag', jdbcType: 'BOOLEAN' },
    ])
    expect(compiled).toEqual({
      ok: false,
      errors: {
        amount: '数字列需要有效数字',
        flag: '布尔列只接受 true、false、1、0',
      },
    })
  })
})
