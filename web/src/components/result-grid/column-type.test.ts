import { describe, expect, it } from 'vitest'
import { columnTypeGlyph, columnTypeKind, defaultColumnWidth } from './column-type'
import { clampRowLimit, DEFAULT_ROW_LIMIT } from './limits'

describe('result column types', () => {
  it('maps jdbc types to CloudBeaver-style glyphs', () => {
    expect(columnTypeKind('BIGINT')).toBe('number')
    expect(columnTypeGlyph('number')).toBe('123')
    expect(columnTypeKind('VARCHAR')).toBe('string')
    expect(columnTypeGlyph('string')).toBe('A-Z')
    expect(columnTypeKind('TIMESTAMP')).toBe('date')
    expect(columnTypeKind('BLOB')).toBe('binary')
    expect(columnTypeKind('BOOLEAN')).toBe('boolean')
  })

  it('sizes headers from the column label', () => {
    expect(defaultColumnWidth('id')).toBe(120)
    expect(defaultColumnWidth('a'.repeat(40))).toBe(280)
  })
})

describe('row limit', () => {
  it('clamps to the backend-allowed range', () => {
    expect(clampRowLimit(Number.NaN)).toBe(DEFAULT_ROW_LIMIT)
    expect(clampRowLimit(0)).toBe(1)
    expect(clampRowLimit(200)).toBe(200)
    expect(clampRowLimit(999999)).toBe(100000)
  })
})
