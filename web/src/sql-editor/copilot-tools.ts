import { randomUUID } from '@/uuid'

export interface CopilotToolCall {
  id: string
  toolName: string
  status: 'STARTED' | 'SUCCESS' | 'FAILURE'
}

const LABELS: Record<string, string> = {
  listTables: '列出数据表',
  getTableSchema: '读取表结构',
  checkSql: '校验 SQL',
  executeQuery: '试跑查询',
  getCurrentDate: '获取当前日期',
  calculateDaysBetween: '计算日期',
  calculate: '计算',
  getSystemInfo: '读取系统信息',
}

export function toolLabel(toolName: string): string {
  return LABELS[toolName] || toolName
}

export function toolStatusText(tool: CopilotToolCall): string {
  const label = toolLabel(tool.toolName)
  if (tool.status === 'STARTED') return `正在${label}…`
  if (tool.status === 'FAILURE') return `${label}失败`
  return `已${label}`
}

export function applyToolEvent(
  tools: CopilotToolCall[],
  toolName: string,
  status: CopilotToolCall['status'],
): CopilotToolCall[] {
  if (status === 'STARTED') {
    return [...tools, { id: randomUUID(), toolName, status }]
  }
  const next = tools.map((item) => ({ ...item }))
  for (let i = next.length - 1; i >= 0; i -= 1) {
    const current = next[i]
    if (current && current.toolName === toolName && current.status === 'STARTED') {
      next[i] = { ...current, status }
      return next
    }
  }
  return [...next, { id: randomUUID(), toolName, status }]
}
