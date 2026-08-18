import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { planScript, runScript } from '@/sql-editor/script-runner'
import { MAX_SCRIPT_STATEMENTS } from '@/sql-editor/sql'
import { useEditorStore } from '@/stores/editor'
import type { SqlExecutionResult } from '@/types/contracts'

beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
})

function resultSet(executionId: string): SqlExecutionResult {
  return {
    kind: 'RESULT_SET',
    executionId,
    columns: [],
    rows: [],
    rowCount: 0,
    truncated: false,
    durationMs: 1,
  }
}
function ddl(executionId: string): SqlExecutionResult {
  return { kind: 'DDL', executionId, affectedRows: null, durationMs: 2, message: '执行成功' }
}

describe('script planning', () => {
  it('splits a script and keeps statement order', () => {
    const plan = planScript('select 1;\nselect 2;\nselect 3', { hasDatabase: true })
    expect(plan).toEqual({ ok: true, statements: ['select 1', 'select 2', 'select 3'] })
  })
  it('rejects empty text', () => {
    expect(planScript('   \n  ', { hasDatabase: true })).toEqual({
      ok: false,
      message: '请输入要执行的 SQL',
    })
  })
  it('rejects a script above the statement cap', () => {
    const plan = planScript('select 1;'.repeat(MAX_SCRIPT_STATEMENTS + 1), { hasDatabase: true })
    expect(plan.ok).toBe(false)
    expect(plan.ok === false && plan.message).toContain(String(MAX_SCRIPT_STATEMENTS))
  })
  it('requires a database when a statement touches objects', () => {
    expect(planScript('select 1;select * from t', { hasDatabase: false })).toEqual({
      ok: false,
      message: '请在左侧导航选择数据库',
    })
    expect(planScript('select 1;select 2', { hasDatabase: false }).ok).toBe(true)
  })
  it('sends the whole text as one statement when the split is unreliable', () => {
    const plan = planScript("select 1; select 'unterminated", { hasDatabase: true })
    expect(plan).toEqual({ ok: true, statements: ["select 1; select 'unterminated"] })
  })
  it('warns that session state does not carry across statements', () => {
    const plan = planScript('SET @x = 1;select @x', { hasDatabase: true })
    expect(plan.ok && plan.warning).toContain('不会延续到后续语句')
  })
  it('does not warn for a single session statement run on its own', () => {
    const plan = planScript('SET @x = 1', { hasDatabase: true })
    expect(plan.ok && plan.warning).toBeUndefined()
  })
})

describe('script execution', () => {
  it('runs statements serially and records every result', async () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    const seen: string[] = []
    const outcome = await runScript(store, {
      tabId: tab.id,
      statements: ['select 1', 'select 2'],
      newExecutionId: () => `e-${seen.length}`,
      describeError: () => 'failed',
      execute: (sql, executionId) => {
        seen.push(sql)
        // The next statement must not start before this one settles.
        expect(store.tabs[0]?.runningIndex).toBe(seen.length - 1)
        return Promise.resolve(resultSet(executionId))
      },
    })
    expect(seen).toEqual(['select 1', 'select 2'])
    expect(outcome).toEqual({ executed: 2, failed: false, sawDdl: false })
    expect(tab.statements.map((item) => item.result?.executionId)).toEqual(['e-0', 'e-1'])
    expect(tab.running).toBe(false)
  })
  it('stops at the first failure and keeps earlier results', async () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    const execute = vi
      .fn()
      .mockResolvedValueOnce(resultSet('e-0'))
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(resultSet('e-2'))
    const outcome = await runScript(store, {
      tabId: tab.id,
      statements: ['select 1', 'boom', 'select 3'],
      newExecutionId: () => crypto.randomUUID(),
      describeError: () => 'SQL 执行失败',
      execute,
    })
    expect(execute).toHaveBeenCalledTimes(2)
    expect(outcome).toEqual({ executed: 1, failed: true, sawDdl: false })
    expect(tab.statements.map((item) => item.status)).toEqual(['SUCCESS', 'FAILED', 'SKIPPED'])
    expect(tab.statements[0]?.result?.executionId).toBe('e-0')
    expect(tab.statements[1]?.error).toBe('SQL 执行失败')
  })
  it('discards queued statements once the tab is cancelled', async () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    const execute = vi.fn().mockImplementation((_sql: string, executionId: string) => {
      store.abort(tab.id)
      return Promise.resolve(resultSet(executionId))
    })
    const outcome = await runScript(store, {
      tabId: tab.id,
      statements: ['select 1', 'select 2', 'select 3'],
      newExecutionId: () => crypto.randomUUID(),
      describeError: () => 'failed',
      execute,
    })
    expect(execute).toHaveBeenCalledTimes(1)
    expect(outcome).toEqual({ executed: 1, failed: false, sawDdl: false })
    expect(tab.statements.map((item) => item.status)).toEqual(['SUCCESS', 'SKIPPED', 'SKIPPED'])
    expect(tab.running).toBe(false)
  })
  it('reports DDL so metadata can be refreshed once', async () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    const outcome = await runScript(store, {
      tabId: tab.id,
      statements: ['create table t (a int)', 'select 1'],
      newExecutionId: () => crypto.randomUUID(),
      describeError: () => 'failed',
      execute: (sql, executionId) =>
        Promise.resolve(sql.startsWith('create') ? ddl(executionId) : resultSet(executionId)),
    })
    expect(outcome.sawDdl).toBe(true)
  })
  it('releases the running state when the tab cannot start a script', async () => {
    const store = useEditorStore()
    const tab = store.createTab('ds-1', 'orders')
    store.beginScript(tab.id, ['select 1'])
    await expect(
      runScript(store, {
        tabId: tab.id,
        statements: ['select 2'],
        newExecutionId: () => crypto.randomUUID(),
        describeError: () => 'failed',
        execute: () => Promise.resolve(resultSet('x')),
      }),
    ).rejects.toThrow('当前页签正在执行')
  })
})
