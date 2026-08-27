import { columnTypeKind, type ColumnTypeKind } from '@/components/result-grid/column-type'
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

export type FilterParseResult =
  | { ok: true; predicate: TableDataPredicate | null }
  | { ok: false; error: string }

const COMPARE_PREFIXES = ['>=', '<=', '<>', '!=', '=', '>', '<'] as const
const NUMBER_VALUE = /^-?\d+(\.\d+)?$/
const LIKE_ESCAPE = '/'

export function quoteSqlLiteral(value: string): string {
  return `'${value.split("'").join("''")}'`
}

export function escapeLikeValue(value: string): string {
  return value.replace(/[/_%]/g, (char) => `${LIKE_ESCAPE}${char}`)
}

export function parseTableDataFilter(
  draft: string,
  column: string,
  typeKind: ColumnTypeKind,
): FilterParseResult {
  const text = draft.trim()
  if (!text) return { ok: true, predicate: null }

  if (/^null$/i.test(text)) return { ok: true, predicate: { column, type: 'null' } }
  if (/^not\s+null$/i.test(text)) return { ok: true, predicate: { column, type: 'not-null' } }

  if (typeKind === 'binary') {
    return { ok: false, error: '二进制列只支持空、NULL、NOT NULL' }
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

  const value = typedCompareValue(raw, typeKind)
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
    const parsed = parseTableDataFilter(draft, column.name, columnTypeKind(column.jdbcType))
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

function typedCompareValue(
  raw: string,
  typeKind: ColumnTypeKind,
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
  return { ok: true, valueKind: 'string', value: raw }
}

function predicateSql(predicate: TableDataPredicate, quote: string): string {
  const column = quoteIdentifier(predicate.column, quote)
  if (predicate.type === 'null') return `${column} IS NULL`
  if (predicate.type === 'not-null') return `${column} IS NOT NULL`
  if (predicate.type === 'like') {
    return `${column} LIKE ${quoteSqlLiteral(`%${escapeLikeValue(predicate.value)}%`)} ESCAPE ${quoteSqlLiteral(LIKE_ESCAPE)}`
  }
  return `${column} ${predicate.op} ${compareValueSql(predicate.valueKind, predicate.value)}`
}

function compareValueSql(valueKind: 'number' | 'boolean' | 'string', value: string): string {
  if (valueKind === 'number' || valueKind === 'boolean') return value
  return quoteSqlLiteral(value)
}
