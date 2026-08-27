export type ColumnTypeKind = 'number' | 'string' | 'date' | 'binary' | 'boolean' | 'other'
export type DateFilterInputKind = 'date' | 'time' | 'datetime'

const NUMBER_TYPES = new Set([
  'TINYINT',
  'SMALLINT',
  'INTEGER',
  'BIGINT',
  'FLOAT',
  'REAL',
  'DOUBLE',
  'NUMERIC',
  'DECIMAL',
  'BIT',
  'YEAR',
])
const STRING_TYPES = new Set([
  'CHAR',
  'VARCHAR',
  'LONGVARCHAR',
  'NCHAR',
  'NVARCHAR',
  'LONGNVARCHAR',
  'CLOB',
  'NCLOB',
  'SQLXML',
  'JSON',
])
const DATE_TYPES = new Set([
  'DATE',
  'TIME',
  'TIMESTAMP',
  'TIME_WITH_TIMEZONE',
  'TIMESTAMP_WITH_TIMEZONE',
])
const BINARY_TYPES = new Set(['BINARY', 'VARBINARY', 'LONGVARBINARY', 'BLOB'])
const BOOLEAN_TYPES = new Set(['BOOLEAN', 'BOOL'])

export function columnTypeKind(jdbcType: string): ColumnTypeKind {
  const type = jdbcType.trim().toUpperCase()
  if (BOOLEAN_TYPES.has(type)) return 'boolean'
  if (NUMBER_TYPES.has(type)) return 'number'
  if (STRING_TYPES.has(type)) return 'string'
  if (DATE_TYPES.has(type)) return 'date'
  if (BINARY_TYPES.has(type)) return 'binary'
  return 'other'
}

export function dateFilterInputKind(jdbcType: string): DateFilterInputKind | null {
  const type = jdbcType.trim().toUpperCase()
  if (type === 'TIME' || type === 'TIME_WITH_TIMEZONE') return 'time'
  if (DATE_TYPES.has(type)) return 'date'
  return null
}

export function isTimestampJdbcType(jdbcType: string): boolean {
  const type = jdbcType.trim().toUpperCase()
  return type === 'TIMESTAMP' || type === 'TIMESTAMP_WITH_TIMEZONE'
}

export function columnTypeGlyph(kind: ColumnTypeKind): string {
  switch (kind) {
    case 'number':
      return '123'
    case 'string':
      return 'A-Z'
    case 'date':
      return 'DATE'
    case 'binary':
      return 'BIN'
    case 'boolean':
      return '0/1'
    default:
      return '?'
  }
}

export function defaultColumnWidth(label: string): number {
  return Math.max(120, Math.min(280, 36 + label.length * 8))
}
