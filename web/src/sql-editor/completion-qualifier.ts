import { quoteIdentifier, sqlLexicalContextAt } from '@/sql-editor/sql'

export type CaretCompletion =
  | { kind: 'non-code' }
  | { kind: 'unqualified' }
  | {
      kind: 'qualified'
      qualifier: string
      prefix: string
      prefixStart: number
      prefixEnd: number
    }

const SIMPLE_IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_$]*$/

export function isSimpleIdentifier(name: string): boolean {
  return SIMPLE_IDENTIFIER.test(name)
}

export function completionInsertText(name: string, quote = '`'): string {
  return isSimpleIdentifier(name) ? name : quoteIdentifier(name, quote)
}

export function resolveTableName(qualifier: string, tables: string[]): string | null {
  if (tables.includes(qualifier)) return qualifier
  const matches = tables.filter((name) => name.toLowerCase() === qualifier.toLowerCase())
  return matches.length === 1 ? (matches[0] ?? null) : null
}

export function parseCompletionCaret(
  source: string,
  offset: number,
  identifierQuote = '`',
): CaretCompletion {
  const caret = Math.max(0, Math.min(offset, source.length))
  if (sqlLexicalContextAt(source, caret, identifierQuote) === 'non-code')
    return { kind: 'non-code' }
  let prefixStart = caret
  while (prefixStart > 0 && isIdentChar(source[prefixStart - 1] || '')) prefixStart -= 1
  if (prefixStart === 0 || source[prefixStart - 1] !== '.') return { kind: 'unqualified' }
  const identifier = readIdentifierEndingAt(source, prefixStart - 1, identifierQuote)
  if (!identifier) return { kind: 'unqualified' }
  return {
    kind: 'qualified',
    qualifier: identifier,
    prefix: source.slice(prefixStart, caret),
    prefixStart,
    prefixEnd: caret,
  }
}

function isIdentChar(char: string): boolean {
  return /[A-Za-z0-9_$]/.test(char)
}

function isIdentStart(char: string): boolean {
  return /[A-Za-z_]/.test(char)
}

function readIdentifierEndingAt(
  source: string,
  endExclusive: number,
  quote: string,
): string | null {
  if (endExclusive <= 0) return null
  if (source[endExclusive - 1] === quote) return unwrapQuoted(source, endExclusive - 1, quote)
  let start = endExclusive
  while (start > 0 && isIdentChar(source[start - 1] || '')) start -= 1
  const name = source.slice(start, endExclusive)
  if (!name || !isIdentStart(name[0] || '')) return null
  return name
}

function unwrapQuoted(source: string, closingIndex: number, quote: string): string | null {
  let index = closingIndex - 1
  let name = ''
  while (index >= 0) {
    const char = source[index] || ''
    if (char === quote) {
      if (index > 0 && source[index - 1] === quote) {
        name = quote + name
        index -= 2
        continue
      }
      return name
    }
    name = char + name
    index -= 1
  }
  return null
}
