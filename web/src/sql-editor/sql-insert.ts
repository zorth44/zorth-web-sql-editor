export function appendSqlText(current: string, sql: string): string {
  const next = sql.endsWith('\n') ? sql : `${sql}\n`
  const trimmed = current.trimEnd()
  if (!trimmed) return next
  return `${trimmed}\n\n${next}`
}

export function replaceSqlOnce(
  current: string,
  target: string,
  replacement: string,
): { text: string; replaced: boolean } {
  if (!target) return { text: appendSqlText(current, replacement), replaced: false }
  const index = current.indexOf(target)
  if (index < 0) return { text: appendSqlText(current, replacement), replaced: false }
  return {
    text: `${current.slice(0, index)}${replacement}${current.slice(index + target.length)}`,
    replaced: true,
  }
}
