import { describe, expect, it } from 'vitest'
import { applyToolEvent, toolStatusText } from '@/sql-editor/copilot-tools'

describe('copilot tools', () => {
  it('pairs started and completed calls of the same tool', () => {
    const started = applyToolEvent([], 'listTables', 'STARTED')
    const done = applyToolEvent(started, 'listTables', 'SUCCESS')
    expect(done).toHaveLength(1)
    expect(done[0]?.status).toBe('SUCCESS')
    expect(toolStatusText(done[0]!)).toBe('已列出数据表')
  })

  it('keeps a second call of the same tool as its own row', () => {
    let tools = applyToolEvent([], 'checkSql', 'STARTED')
    tools = applyToolEvent(tools, 'checkSql', 'SUCCESS')
    tools = applyToolEvent(tools, 'checkSql', 'STARTED')
    expect(tools.map((item) => item.status)).toEqual(['SUCCESS', 'STARTED'])
  })
})
