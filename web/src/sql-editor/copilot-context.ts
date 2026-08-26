export const COPILOT_FIX_PROMPT =
  '请修复下面这条失败的 SQL，给出改正后的完整语句。失败语句和错误已在上下文块中。'

export const MESSAGE_LIMIT = 10_000
export const SQL_CONTEXT_LIMIT = 4_000
export const ERROR_CONTEXT_LIMIT = 1_000

export interface CopilotContextInput {
  userText: string
  dialect: string
  dataSourceName: string
  database: string
  currentSql: string
  failedSql?: string
  failedError?: string
}

function clip(value: string, limit: number): string {
  if (value.length <= limit) return value
  return `${value.slice(0, limit)}\n…`
}

export function buildCopilotMessage(input: CopilotContextInput): string {
  const lines = [
    '【编辑器上下文】',
    `方言: ${input.dialect}`,
    `数据源: ${input.dataSourceName}`,
    `NAMESPACE: ${input.database}`,
  ]
  if (input.currentSql.trim()) lines.push(`当前 SQL:\n${clip(input.currentSql, SQL_CONTEXT_LIMIT)}`)
  if (input.failedSql?.trim()) lines.push(`失败语句:\n${clip(input.failedSql, SQL_CONTEXT_LIMIT)}`)
  if (input.failedError?.trim())
    lines.push(`错误:\n${clip(input.failedError, ERROR_CONTEXT_LIMIT)}`)
  lines.push(
    '【交付要求】',
    '- 给出的 SQL 必须放在 sql 代码块里。',
    '- 可以只读试跑验证，但回答以 SQL 为主，不要把结果行当最终产物。',
    '',
    '【用户】',
    input.userText,
  )
  const message = lines.join('\n')
  return message.length <= MESSAGE_LIMIT ? message : `${message.slice(0, MESSAGE_LIMIT - 1)}…`
}
