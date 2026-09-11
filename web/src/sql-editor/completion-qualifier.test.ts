import { describe, expect, it } from 'vitest'
import { quoteIdentifier } from '@/sql-editor/sql'
import {
  completionInsertText,
  parseCompletionCaret,
  resolveTableName,
} from '@/sql-editor/completion-qualifier'

describe('completion qualifier parsing', () => {
  it('parses an unquoted table qualifier and empty prefix', () => {
    const sql = 'select * from orders.'
    expect(parseCompletionCaret(sql, sql.length, '`')).toEqual({
      kind: 'qualified',
      qualifier: 'orders',
      prefix: '',
      prefixStart: sql.length,
      prefixEnd: sql.length,
    })
  })

  it('keeps only the identifier immediately before the dot', () => {
    const sql = 'select * from sales.orders.'
    expect(parseCompletionCaret(sql, sql.length, '`')).toMatchObject({
      kind: 'qualified',
      qualifier: 'orders',
      prefix: '',
    })
  })

  it('unwraps backtick and doubled-backtick qualifiers', () => {
    expect(
      parseCompletionCaret('select * from `orders`.', 'select * from `orders`.'.length, '`'),
    ).toMatchObject({
      kind: 'qualified',
      qualifier: 'orders',
    })
    const sql = 'select * from `ord``ers`.'
    expect(parseCompletionCaret(sql, sql.length, '`')).toMatchObject({
      kind: 'qualified',
      qualifier: 'ord`ers',
    })
  })

  it('unwraps double-quoted qualifiers when the engine quote is a double quote', () => {
    const sql = 'select * from "orders".'
    expect(parseCompletionCaret(sql, sql.length, '"')).toMatchObject({
      kind: 'qualified',
      qualifier: 'orders',
    })
    const doubled = 'select * from "ord""ers".'
    expect(parseCompletionCaret(doubled, doubled.length, '"')).toMatchObject({
      kind: 'qualified',
      qualifier: 'ord"ers',
    })
  })

  it('captures a column prefix after the dot for replacement', () => {
    const sql = 'select orders.id'
    const caret = sql.length
    expect(parseCompletionCaret(sql, caret, '`')).toEqual({
      kind: 'qualified',
      qualifier: 'orders',
      prefix: 'id',
      prefixStart: sql.indexOf('id'),
      prefixEnd: caret,
    })
  })

  it('returns unqualified when there is no table-dot form', () => {
    expect(parseCompletionCaret('select * from ord', 17, '`')).toEqual({ kind: 'unqualified' })
    expect(parseCompletionCaret('select 1.', 9, '`')).toEqual({ kind: 'unqualified' })
  })

  it('ignores qualifier-like text in strings, line comments, block comments, and dollar quotes', () => {
    expect(parseCompletionCaret("select 'orders.", "select 'orders.".length, '`')).toEqual({
      kind: 'non-code',
    })
    expect(parseCompletionCaret('select "orders.', 'select "orders.'.length, '`')).toEqual({
      kind: 'non-code',
    })
    expect(parseCompletionCaret('select 1 -- orders.', 'select 1 -- orders.'.length, '`')).toEqual({
      kind: 'non-code',
    })
    expect(parseCompletionCaret('select 1 # orders.', 'select 1 # orders.'.length, '`')).toEqual({
      kind: 'non-code',
    })
    expect(parseCompletionCaret('select 1 /* orders.', 'select 1 /* orders.'.length, '`')).toEqual({
      kind: 'non-code',
    })
    expect(
      parseCompletionCaret('select $tag$ orders.', 'select $tag$ orders.'.length, '`'),
    ).toEqual({
      kind: 'non-code',
    })
  })

  it('treats double quotes as identifiers in PostgreSQL code context', () => {
    const sql = 'select * from "orders".'
    expect(parseCompletionCaret(sql, sql.length, '"')).toMatchObject({
      kind: 'qualified',
      qualifier: 'orders',
    })
  })
})

describe('table name resolution', () => {
  it('prefers an exact match over case-insensitive siblings', () => {
    expect(resolveTableName('Orders', ['orders', 'Orders', 'ORDERS'])).toBe('Orders')
  })

  it('resolves a unique case-insensitive match', () => {
    expect(resolveTableName('ORDERS', ['orders'])).toBe('orders')
  })

  it('rejects ambiguous case-insensitive matches and unknown aliases', () => {
    expect(resolveTableName('orders', ['Orders', 'ORDERS'])).toBeNull()
    expect(resolveTableName('o', ['orders'])).toBeNull()
  })
})

describe('completion insert text', () => {
  it('inserts simple identifiers as-is', () => {
    expect(completionInsertText('amount', '`')).toBe('amount')
    expect(completionInsertText('id_1', '"')).toBe('id_1')
  })

  it('doubles engine quotes inside unsafe names and inserts no extra SQL', () => {
    expect(completionInsertText('ord`ers', '`')).toBe(quoteIdentifier('ord`ers', '`'))
    expect(completionInsertText('ord`ers', '`')).toBe('`ord``ers`')
    expect(completionInsertText('ord"ers', '"')).toBe('"ord""ers"')
    expect(completionInsertText('select', '`')).toBe('select')
    expect(completionInsertText('my col', '`')).toBe('`my col`')
    expect(completionInsertText('ord`ers; drop', '`')).toBe('`ord``ers; drop`')
  })
})
