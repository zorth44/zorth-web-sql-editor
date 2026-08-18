import type { SqlExecutionResult } from '@/types/contracts'
import {
  MAX_SCRIPT_STATEMENTS,
  likelyNeedsDatabase,
  needsSessionAffinity,
  scanSql,
} from '@/sql-editor/sql'

export type ScriptPlan =
  | { ok: false; message: string }
  | { ok: true; statements: string[]; warning?: string }

/**
 * Decides what a run action should send. Splitting happens on the client because
 * target connections keep `allowMultiQueries=false`, so every statement travels
 * as its own single-statement request.
 */
export function planScript(text: string, context: { hasDatabase: boolean }): ScriptPlan {
  const script = text.trim()
  if (!script) return { ok: false, message: '请输入要执行的 SQL' }
  const scan = scanSql(script)
  // An unreliable split ended inside a string, identifier, or comment. Send the
  // text untouched so the backend scanner produces the authoritative error.
  const statements = scan.reliable ? scan.statements.map((item) => item.text) : [script]
  if (!statements.length) return { ok: false, message: '请输入要执行的 SQL' }
  if (statements.length > MAX_SCRIPT_STATEMENTS) {
    return {
      ok: false,
      message: `一次最多执行 ${MAX_SCRIPT_STATEMENTS} 条语句，请拆分脚本后重试`,
    }
  }
  if (!context.hasDatabase && statements.some(likelyNeedsDatabase)) {
    return { ok: false, message: '请在左侧导航选择数据库' }
  }
  if (statements.length > 1 && statements.some(needsSessionAffinity)) {
    return {
      ok: true,
      statements,
      warning: '脚本中的 SET、USE、CREATE TEMPORARY 只在所在语句生效，不会延续到后续语句',
    }
  }
  return { ok: true, statements }
}

export interface ScriptStore {
  beginScript(id: string, sqls: string[]): void
  startStatement(id: string, index: number, executionId: string): AbortController
  finishStatement(id: string, index: number, result?: SqlExecutionResult, error?: string): void
  endScript(id: string): void
  isCancelled(id: string): boolean
}
export interface ScriptRequest {
  tabId: string
  statements: string[]
  execute: (sql: string, executionId: string, signal: AbortSignal) => Promise<SqlExecutionResult>
  newExecutionId: () => string
  describeError: (error: unknown) => string
}
export interface ScriptOutcome {
  executed: number
  failed: boolean
  sawDdl: boolean
}

/** Runs statements one at a time, stopping at the first failure or cancellation. */
export async function runScript(
  store: ScriptStore,
  request: ScriptRequest,
): Promise<ScriptOutcome> {
  const outcome: ScriptOutcome = { executed: 0, failed: false, sawDdl: false }
  store.beginScript(request.tabId, request.statements)
  try {
    for (const [index, sql] of request.statements.entries()) {
      if (store.isCancelled(request.tabId)) break
      const executionId = request.newExecutionId()
      const controller = store.startStatement(request.tabId, index, executionId)
      try {
        const result = await request.execute(sql, executionId, controller.signal)
        store.finishStatement(request.tabId, index, result)
        outcome.executed += 1
        if (result.kind === 'DDL') outcome.sawDdl = true
      } catch (error) {
        store.finishStatement(request.tabId, index, undefined, request.describeError(error))
        outcome.failed = true
        break
      }
    }
  } finally {
    store.endScript(request.tabId)
  }
  return outcome
}
