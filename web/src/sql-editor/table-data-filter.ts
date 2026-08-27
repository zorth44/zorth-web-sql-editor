import {
  columnTypeKind,
  dateFilterInputKind,
  isTimestampJdbcType,
  type ColumnTypeKind,
  type DateFilterInputKind,
} from '@/components/result-grid/column-type'
import { quoteIdentifier, selectTableData } from '@/sql-editor/sql'

export type TableDataSortDir = 'asc' | 'desc'
export type TableDataSort = { column: string; dir: TableDataSortDir }
export type CompareOp = '=' | '<>' | '>' | '<' | '>=' | '<='

export type TableDataPredicate =
  | { column: string; type: 'null' }
  | { column: string; type: 'not-null' }
  | { column: string; type: 'like'; value: string }
  | {
      column: string
      type: 'compare'
      op: CompareOp
      valueKind: 'number' | 'boolean' | 'string'
      value: string
    }
  | { column: string; type: 'between'; start: string; end: string }

export type FilterParseResult =
  | { ok: true; predicate: TableDataPredicate | null }
  | { ok: false; error: string }

const COMPARE_PREFIXES = ['>=', '<=', '<>', '!=', '=', '>', '<'] as const
const NUMBER_VALUE = /^-?\d+(\.\d+)?$/
const LIKE_ESCAPE = '/'
const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/
const RANGE_SPLIT = /\s*(?:\.\.|~|～)\s*/

export function quoteSqlLiteral(value: string): string {
  return `'${value.split("'").join("''")}'`
}

export function escapeLikeValue(value: string): string {
  return value.replace(/[/_%]/g, (char) => `${LIKE_ESCAPE}${char}`)
}

export function toDateFilterSqlValue(raw: string, kind: DateFilterInputKind): string {
  const text = raw.trim()
  if (kind === 'date') return text.slice(0, 10)
  if (kind === 'time') return /^\d{2}:\d{2}$/.test(text) ? `${text}:00` : text
  const normalized = text.replace('T', ' ')
  if (DATE_ONLY.test(normalized)) return `${normalized} 00:00:00`
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(normalized)) return `${normalized}:00`
  return normalized
}

export function toDateFilterInputValue(sql: string, kind: DateFilterInputKind): string {
  const text = sql.trim()
  if (!text) return ''
  if (kind === 'date') return text.slice(0, 10)
  if (kind === 'time') return text.length >= 8 ? text.slice(0, 8) : text
  if (DATE_ONLY.test(text)) return `${text}T00:00`
  const withT = text.replace(' ', 'T')
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(withT)) return withT
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(withT)) return withT.slice(0, 19)
  return withT
}

export function encodeDateRangeDraft(
  startInput: string,
  endInput: string,
  kind: DateFilterInputKind,
): string {
  const startSql = startInput.trim() ? toDateFilterSqlValue(startInput, kind) : ''
  const endSql = endInput.trim() ? toDateFilterSqlValue(endInput, kind) : ''
  if (!startSql && !endSql) return ''
  if (startSql && endSql) {
    return startSql <= endSql ? `${startSql}..${endSql}` : `${endSql}..${startSql}`
  }
  if (startSql) return `>=${startSql}`
  return `<=${endSql}`
}

export function decodeDateRangeDraft(
  draft: string,
  kind: DateFilterInputKind,
): { start: string; end: string } {
  const text = draft.trim()
  if (!text || /^null$/i.test(text) || /^not\s+null$/i.test(text)) return { start: '', end: '' }
  const range = splitDateRange(text)
  if (range) {
    return {
      start: toDateFilterInputValue(range.start, kind),
      end: toDateFilterInputValue(range.end, kind),
    }
  }
  for (const prefix of COMPARE_PREFIXES) {
    if (!text.startsWith(prefix)) continue
    const raw = text.slice(prefix.length).trim()
    const input = toDateFilterInputValue(raw, kind)
    if (prefix === '<=' || prefix === '<') return { start: '', end: input }
    if (prefix === '<>' || prefix === '!=') return { start: '', end: '' }
    return { start: input, end: prefix === '=' ? input : '' }
  }
  const input = toDateFilterInputValue(text, kind)
  return { start: input, end: input }
}

export function formatDateRangeLabel(draft: string): string {
  const text = draft.trim()
  if (!text) return ''
  if (/^null$/i.test(text)) return '为空'
  if (/^not\s+null$/i.test(text)) return '不为空'
  const range = splitDateRange(text)
  if (range) {
    if (range.start && range.end) return `${range.start} ~ ${range.end}`
    if (range.start) return `${range.start} ~`
    if (range.end) return `~ ${range.end}`
  }
  if (text.startsWith('>=')) return `${text.slice(2).trim()} ~`
  if (text.startsWith('<=')) return `~ ${text.slice(2).trim()}`
  return text
}

export function parseTableDataFilter(
  draft: string,
  column: string,
  typeKind: ColumnTypeKind,
  jdbcType?: string,
): FilterParseResult {
  const text = draft.trim()
  if (!text) return { ok: true, predicate: null }

  if (/^null$/i.test(text)) return { ok: true, predicate: { column, type: 'null' } }
  if (/^not\s+null$/i.test(text)) return { ok: true, predicate: { column, type: 'not-null' } }

  if (typeKind === 'binary') {
    return { ok: false, error: '二进制列只支持空、NULL、NOT NULL' }
  }

  if (typeKind === 'date') {
    const range = parseDateRangePredicate(text, column, jdbcType)
    if (range) return range
  }

  let op: CompareOp | 'like' = 'like'
  let raw = text
  for (const prefix of COMPARE_PREFIXES) {
    if (text.startsWith(prefix)) {
      op = prefix === '!=' ? '<>' : prefix
      raw = text.slice(prefix.length).trim()
      break
    }
  }

  if (op === 'like') {
    if (typeKind === 'number' || typeKind === 'date' || typeKind === 'boolean') {
      op = '='
    }
  }

  if (op !== 'like' && !raw) {
    return { ok: false, error: '比较运算符后面需要值' }
  }

  if (op === 'like') {
    return { ok: true, predicate: { column, type: 'like', value: raw } }
  }

  const value = typedCompareValue(raw, typeKind, jdbcType, boundFromCompareOp(op))
  if (!value.ok) return value
  return {
    ok: true,
    predicate: { column, type: 'compare', op, valueKind: value.valueKind, value: value.value },
  }
}

export function compileTableDataFilters(
  drafts: Record<string, string>,
  columns: Array<{ name: string; jdbcType: string }>,
): { ok: true; predicates: TableDataPredicate[] } | { ok: false; errors: Record<string, string> } {
  const predicates: TableDataPredicate[] = []
  const errors: Record<string, string> = {}
  for (const column of columns) {
    const draft = drafts[column.name]
    if (draft == null || !draft.trim()) continue
    const parsed = parseTableDataFilter(
      draft,
      column.name,
      columnTypeKind(column.jdbcType),
      column.jdbcType,
    )
    if (!parsed.ok) errors[column.name] = parsed.error
    else if (parsed.predicate) predicates.push(parsed.predicate)
  }
  if (Object.keys(errors).length) return { ok: false, errors }
  return { ok: true, predicates }
}

export function buildTableDataSql(input: {
  database: string
  table: string
  quote?: string
  predicates?: TableDataPredicate[]
  sort?: TableDataSort | null
}): string {
  const quote = input.quote ?? '`'
  const parts = [selectTableData(input.database, input.table, quote)]
  const predicates = input.predicates ?? []
  if (predicates.length) {
    parts.push(`WHERE ${predicates.map((item) => predicateSql(item, quote)).join(' AND ')}`)
  }
  if (input.sort) {
    const direction = input.sort.dir === 'desc' ? 'DESC' : 'ASC'
    parts.push(`ORDER BY ${quoteIdentifier(input.sort.column, quote)} ${direction}`)
  }
  return parts.join('\n')
}

export function nextTableDataSort(
  current: TableDataSort | null,
  column: string,
): TableDataSort | null {
  if (current?.column === column && current.dir === 'asc') return { column, dir: 'desc' }
  if (current?.column === column && current.dir === 'desc') return null
  return { column, dir: 'asc' }
}

function splitDateRange(text: string): { start: string; end: string } | null {
  if (!/\.\.|~|～/.test(text)) return null
  const [start = '', end = ''] = text.split(RANGE_SPLIT)
  return { start: start.trim(), end: end.trim() }
}

function parseDateRangePredicate(
  text: string,
  column: string,
  jdbcType?: string,
): FilterParseResult | null {
  const range = splitDateRange(text)
  if (!range) return null
  const kind = dateFilterInputKind(jdbcType ?? 'DATE') ?? 'date'
  const start = range.start
    ? expandDateBound(toDateFilterSqlValue(range.start, kind), 'start', jdbcType)
    : ''
  const end = range.end
    ? expandDateBound(toDateFilterSqlValue(range.end, kind), 'end', jdbcType)
    : ''
  if (!start && !end) return { ok: true, predicate: null }
  if (start && end) {
    const [lo, hi] = start <= end ? [start, end] : [end, start]
    return { ok: true, predicate: { column, type: 'between', start: lo, end: hi } }
  }
  return {
    ok: true,
    predicate: {
      column,
      type: 'compare',
      op: start ? '>=' : '<=',
      valueKind: 'string',
      value: start || end,
    },
  }
}

function boundFromCompareOp(op: CompareOp): 'start' | 'end' {
  return op === '<=' || op === '<' ? 'end' : 'start'
}

function expandDateBound(value: string, bound: 'start' | 'end', jdbcType?: string): string {
  if (!jdbcType || !isTimestampJdbcType(jdbcType) || !DATE_ONLY.test(value)) return value
  return bound === 'end' ? `${value} 23:59:59` : `${value} 00:00:00`
}

function typedCompareValue(
  raw: string,
  typeKind: ColumnTypeKind,
  jdbcType?: string,
  bound: 'start' | 'end' = 'start',
):
  | { ok: true; valueKind: 'number' | 'boolean' | 'string'; value: string }
  | { ok: false; error: string } {
  if (typeKind === 'number') {
    if (!NUMBER_VALUE.test(raw)) return { ok: false, error: '数字列需要有效数字' }
    return { ok: true, valueKind: 'number', value: raw }
  }
  if (typeKind === 'boolean') {
    const normalized = raw.toLowerCase()
    if (
      normalized === 'true' ||
      normalized === 'false' ||
      normalized === '1' ||
      normalized === '0'
    ) {
      return {
        ok: true,
        valueKind: 'boolean',
        value: normalized === 'true' ? 'TRUE' : normalized === 'false' ? 'FALSE' : normalized,
      }
    }
    return { ok: false, error: '布尔列只接受 true、false、1、0' }
  }
  if (typeKind === 'date') {
    const kind = dateFilterInputKind(jdbcType ?? 'DATE') ?? 'date'
    return {
      ok: true,
      valueKind: 'string',
      value: expandDateBound(toDateFilterSqlValue(raw, kind), bound, jdbcType),
    }
  }
  return { ok: true, valueKind: 'string', value: raw }
}

function predicateSql(predicate: TableDataPredicate, quote: string): string {
  const column = quoteIdentifier(predicate.column, quote)
  if (predicate.type === 'null') return `${column} IS NULL`
  if (predicate.type === 'not-null') return `${column} IS NOT NULL`
  if (predicate.type === 'like') {
    return `${column} LIKE ${quoteSqlLiteral(`%${escapeLikeValue(predicate.value)}%`)} ESCAPE ${quoteSqlLiteral(LIKE_ESCAPE)}`
  }
  if (predicate.type === 'between') {
    return `(${column} >= ${quoteSqlLiteral(predicate.start)} AND ${column} <= ${quoteSqlLiteral(predicate.end)})`
  }
  return `${column} ${predicate.op} ${compareValueSql(predicate.valueKind, predicate.value)}`
}

function compareValueSql(valueKind: 'number' | 'boolean' | 'string', value: string): string {
  if (valueKind === 'number' || valueKind === 'boolean') return value
  return quoteSqlLiteral(value)
}
