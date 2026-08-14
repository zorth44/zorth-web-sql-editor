import { describe, expect, it } from 'vitest'
import { cellMatches, compareCells, displayCell, previewCell } from './cell-value'

describe('result cell values', () => {
  it('distinguishes null, empty string, and binary descriptors', () => {
    expect(displayCell(null)).toBe('NULL')
    expect(displayCell('')).toBe('')
    expect(displayCell({ binary: true, size: 12, base64: null })).toBe('BINARY · 12 bytes')
    expect(displayCell('9007199254740993')).toBe('9007199254740993')
  })

  it('collapses long text and compares with nulls last', () => {
    expect(previewCell('x'.repeat(90))).toMatch(/…$/)
    expect(compareCells(null, 'a')).toBeGreaterThan(0)
    expect(compareCells('2', '10')).toBeLessThan(0)
    expect(cellMatches('Hello', 'ell', 'contains')).toBe(true)
    expect(cellMatches('Hello', 'hello', 'equals')).toBe(true)
    expect(cellMatches(null, 'NULL', 'equals')).toBe(true)
  })
})
