const FENCE = /```([^\n`]*)\n([\s\S]*?)```/g
const SQL_START =
  /^(SELECT|INSERT|UPDATE|DELETE|WITH|CREATE|ALTER|DROP|REPLACE|SHOW|DESCRIBE|DESC|EXPLAIN|USE|SET|TRUNCATE|MERGE|CALL|BEGIN|COMMIT|ROLLBACK)\b/i

export type AssistantSegment = { type: 'text'; text: string } | { type: 'sql'; sql: string }

export function looksLikeSql(text: string): boolean {
  return SQL_START.test(text.trim())
}

function isSqlLanguage(language: string): boolean {
  return (
    language === 'sql' || language === 'mysql' || language === 'pgsql' || language === 'postgresql'
  )
}

export function extractSqlFences(markdown: string): string[] {
  return splitAssistantContent(markdown)
    .filter((segment): segment is { type: 'sql'; sql: string } => segment.type === 'sql')
    .map((segment) => segment.sql)
}

export function splitAssistantContent(markdown: string): AssistantSegment[] {
  const segments: AssistantSegment[] = []
  let last = 0
  for (const match of markdown.matchAll(FENCE)) {
    const index = match.index ?? 0
    const before = markdown.slice(last, index).trim()
    if (before) segments.push({ type: 'text', text: before })
    const language = match[1]?.trim().toLowerCase() || ''
    const body = (match[2] || '').trim()
    if (body && (isSqlLanguage(language) || (!language && looksLikeSql(body)))) {
      segments.push({ type: 'sql', sql: body })
    } else if (body) {
      segments.push({ type: 'text', text: match[0] || '' })
    }
    last = index + match[0].length
  }
  const rest = markdown.slice(last).trim()
  if (rest) segments.push({ type: 'text', text: rest })
  if (!segments.length && markdown.trim()) segments.push({ type: 'text', text: markdown.trim() })
  return segments
}
