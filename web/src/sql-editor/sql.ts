export interface SqlSegment {
  text: string
  start: number
  end: number
}
type State = 'normal' | 'single' | 'double' | 'backtick' | 'line' | 'block'

export function splitSql(source: string): SqlSegment[] {
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
  return segments
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
  return `SELECT *\nFROM ${quoteIdentifier(database)}.${quoteIdentifier(table)}\nLIMIT 100;`
}
export function likelyNeedsDatabase(sql: string): boolean {
  const normalized = sql
    .replace(/^(?:\s|\/\*[\s\S]*?\*\/|--[^\r\n]*(?:\r?\n|$)|#[^\r\n]*(?:\r?\n|$))*/g, '')
    .toUpperCase()
  return (
    /^(INSERT|UPDATE|DELETE|REPLACE|CREATE|ALTER|DROP|TRUNCATE|RENAME)\b/.test(normalized) ||
    (/^(SELECT|WITH|EXPLAIN|DESC|DESCRIBE)\b/.test(normalized) &&
      /\b(FROM|JOIN)\b/.test(normalized))
  )
}
