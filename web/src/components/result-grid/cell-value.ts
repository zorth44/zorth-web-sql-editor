import type { BinaryValue, SqlCellValue } from '@/types/contracts'

export function isBinary(value: unknown): value is BinaryValue {
  return Boolean(value && typeof value === 'object' && (value as BinaryValue).binary === true)
}

export function displayCell(value: SqlCellValue): string {
  if (value === null) return 'NULL'
  if (isBinary(value)) return `BINARY · ${value.size} bytes`
  return String(value)
}

export function isLongText(value: SqlCellValue): boolean {
  return typeof value === 'string' && value.length > 80
}

export function previewCell(value: SqlCellValue, max = 80): string {
  const text = displayCell(value)
  return text.length > max ? `${text.slice(0, max)}…` : text
}

export function compareCells(a: SqlCellValue, b: SqlCellValue): number {
  if (a === null && b === null) return 0
  if (a === null) return 1
  if (b === null) return -1
  if (isBinary(a) || isBinary(b)) {
    const as = isBinary(a) ? a.size : Number.NaN
    const bs = isBinary(b) ? b.size : Number.NaN
    if (Number.isFinite(as) && Number.isFinite(bs) && as !== bs) return as - bs
  }
  return displayCell(a).localeCompare(displayCell(b), undefined, {
    numeric: true,
    sensitivity: 'base',
  })
}

export function cellMatches(
  value: SqlCellValue,
  query: string,
  mode: 'contains' | 'equals',
): boolean {
  const text = displayCell(value)
  if (mode === 'equals') return text.toLowerCase() === query.toLowerCase()
  return text.toLowerCase().includes(query.toLowerCase())
}
