export const MAX_SCRIPT_STATEMENTS = 200

export interface SqlSegment {
  text: string
  start: number
  end: number
}
export interface SqlScript {
  statements: SqlSegment[]
  /**
   * False when the scanner ended inside a string, quoted identifier, or comment.
   * The split cannot be trusted, so the caller sends the whole text and lets the
   * backend scanner produce the authoritative error.
   */
  reliable: boolean
}
type State = 'normal' | 'single' | 'double' | 'backtick' | 'line' | 'block'

export function splitSql(source: string): SqlSegment[] {
  return scanSql(source).statements
}

export function scanSql(source: string): SqlScript {
  const segments: SqlSegment[] = []
  let state: State = 'normal'
  let start = 0
  const add = (end: number) => {
    const raw = source.slice(start, end)
    const left = raw.search(/\S/)
    if (left < 0) return
    const right = raw.search(/\s*$/)
    segments.push({ text: raw.trim(), start: start + left, end: start + right })
  }
  for (let i = 0; i < source.length; i += 1) {
    const char = source[i]
    const next = source[i + 1]
    if (state === 'normal') {
      if (char === "'") state = 'single'
      else if (char === '"') state = 'double'
      else if (char === '`') state = 'backtick'
      else if (char === '#') state = 'line'
      else if (char === '-' && next === '-' && /\s|$/.test(source[i + 2] || '')) {
        state = 'line'
        i += 1
      } else if (char === '/' && next === '*') {
        state = 'block'
        i += 1
      } else if (char === ';') {
        add(i)
        start = i + 1
      }
    } else if (state === 'single') {
      if (char === '\\') i += 1
      else if (char === "'" && next === "'") i += 1
      else if (char === "'") state = 'normal'
    } else if (state === 'double') {
      if (char === '\\') i += 1
      else if (char === '"' && next === '"') i += 1
      else if (char === '"') state = 'normal'
    } else if (state === 'backtick') {
      if (char === '`' && next === '`') i += 1
      else if (char === '`') state = 'normal'
    } else if (state === 'line' && (char === '\n' || char === '\r')) state = 'normal'
    else if (state === 'block' && char === '*' && next === '/') {
      state = 'normal'
      i += 1
    }
  }
  add(source.length)
  // A line comment running to the end of input is well formed; the other
  // non-normal states mean an opener was never closed.
  return { statements: segments, reliable: state === 'normal' || state === 'line' }
}

export function statementAt(source: string, offset: number): SqlSegment | null {
  const segments = splitSql(source)
  return (
    segments.find((item) => offset >= item.start && offset <= item.end) ||
    segments.find((item) => offset < item.start) ||
    segments.at(-1) ||
    null
  )
}
export function quoteIdentifier(value: string): string {
  return `\`${value.replaceAll('`', '``')}\``
}
export function selectPreview(database: string, table: string): string {
  return `${selectTableData(database, table)}\nLIMIT 100;`
}
export function selectTableData(database: string, table: string): string {
  return `SELECT *\nFROM ${quoteIdentifier(database)}.${quoteIdentifier(table)}`
}
function stripLeadingNoise(sql: string): string {
  return sql
    .replace(/^(?:\s|\/\*[\s\S]*?\*\/|--[^\r\n]*(?:\r?\n|$)|#[^\r\n]*(?:\r?\n|$))*/g, '')
    .toUpperCase()
}
/** Leading keyword of a statement, used to label it in the script summary. */
export function statementKeyword(sql: string): string {
  return /^[A-Z]+/.exec(stripLeadingNoise(sql))?.[0] || 'SQL'
}
/**
 * Statements whose effect lives on the connection session. Each script statement
 * borrows its own pooled connection, so these never reach the statements after them.
 */
export function needsSessionAffinity(sql: string): boolean {
  const normalized = stripLeadingNoise(sql)
  return (
    /^SET\b/.test(normalized) ||
    /^USE\b/.test(normalized) ||
    /^CREATE\s+TEMPORARY\b/.test(normalized)
  )
}
export function likelyNeedsDatabase(sql: string): boolean {
  const normalized = stripLeadingNoise(sql)
  return (
    /^(INSERT|UPDATE|DELETE|REPLACE|CREATE|ALTER|DROP|TRUNCATE|RENAME)\b/.test(normalized) ||
    (/^(SELECT|WITH|EXPLAIN|DESC|DESCRIBE)\b/.test(normalized) &&
      /\b(FROM|JOIN)\b/.test(normalized))
  )
}
