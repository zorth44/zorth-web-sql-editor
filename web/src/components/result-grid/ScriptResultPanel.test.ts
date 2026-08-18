import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ScriptResultPanel from '@/components/result-grid/ScriptResultPanel.vue'
import type { StatementRun } from '@/stores/editor'
import type { SqlExecutionResult } from '@/types/contracts'

function resultSet(executionId: string, rowCount = 2): SqlExecutionResult {
  return {
    kind: 'RESULT_SET',
    executionId,
    columns: [{ name: 'id', label: 'id', jdbcType: 'BIGINT', typeName: 'BIGINT' }],
    rows: Array.from({ length: rowCount }, (_, index) => [String(index + 1)]),
    rowCount,
    truncated: false,
    durationMs: 7,
  }
}
function statement(overrides: Partial<StatementRun> & { position: number }): StatementRun {
  return {
    sql: 'select 1',
    status: 'SUCCESS',
    result: resultSet(`e-${overrides.position}`),
    error: null,
    ...overrides,
  }
}

function render(
  statements: StatementRun[],
  extra: {
    result?: SqlExecutionResult | null
    error?: string | null
    resultIndex?: number
    running?: boolean
    runningIndex?: number | null
  } = {},
) {
  return mount(ScriptResultPanel, {
    props: {
      statements,
      result: extra.result ?? null,
      error: extra.error ?? null,
      resultIndex: extra.resultIndex ?? 0,
      running: extra.running ?? false,
      runningIndex: extra.runningIndex ?? null,
      canExport: true,
      rowLimit: 1000,
    },
    attachTo: document.body,
  })
}

describe('script result panel', () => {
  it('keeps the plain grid for a single statement', () => {
    const statements = [statement({ position: 1 })]
    const wrapper = render(statements, { result: statements[0]?.result ?? null })
    expect(wrapper.find('[data-testid="script-summary-tab"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="result-scroll"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('opens a script on the summary and lists every statement', () => {
    const statements = [
      statement({ position: 1, sql: 'select 1' }),
      statement({ position: 2, sql: 'update t set a = 1', result: null, status: 'SUCCESS' }),
    ]
    const wrapper = render(statements, { result: statements[0]?.result ?? null })
    const summary = wrapper.get('[data-testid="script-summary"]')
    expect(wrapper.get('[data-testid="script-summary-tab"]').attributes('aria-selected')).toBe(
      'true',
    )
    expect(summary.text()).toContain('共 2 条语句')
    expect(summary.text()).toContain('SELECT')
    expect(summary.text()).toContain('UPDATE')
    expect(wrapper.text()).toContain('Result 1')
    expect(wrapper.text()).toContain('Result 2')
    wrapper.unmount()
  })

  it('switches to a statement result and reports the selection', async () => {
    const statements = [statement({ position: 1 }), statement({ position: 2 })]
    const wrapper = render(statements, { result: statements[0]?.result ?? null })
    const tabs = wrapper.findAll('[role="tab"]')
    await tabs[2]?.trigger('click')
    expect(wrapper.emitted('select')?.at(-1)).toEqual([1])
    expect(wrapper.find('[data-testid="script-summary"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="result-scroll"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('marks the failing statement and warns that nothing is rolled back', () => {
    const statements = [
      statement({ position: 1 }),
      statement({
        position: 2,
        sql: 'insert into t values (1)',
        status: 'FAILED',
        result: null,
        error: 'Duplicate entry',
      }),
      statement({ position: 3, status: 'SKIPPED', result: null }),
    ]
    const wrapper = render(statements, { error: 'Duplicate entry', resultIndex: 1 })
    const summary = wrapper.get('[data-testid="script-summary"]')
    expect(summary.text()).toContain('成功 1 条')
    expect(summary.text()).toContain('第 2 条失败后已停止')
    expect(summary.text()).toContain('已执行的语句不会回滚')
    expect(summary.text()).toContain('未执行')
    expect(summary.text()).toContain('Duplicate entry')
    wrapper.unmount()
  })

  it('does not offer a result tab for a statement that never ran', async () => {
    const statements = [
      statement({ position: 1 }),
      statement({ position: 2, status: 'FAILED', result: null, error: 'boom' }),
      statement({ position: 3, status: 'SKIPPED', result: null }),
    ]
    const wrapper = render(statements, { error: 'boom', resultIndex: 1 })
    const skipped = wrapper.get('[aria-label="第 3 条语句，未执行"]')
    expect(skipped.attributes('disabled')).toBeDefined()
    await skipped.trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
    expect(wrapper.find('[data-testid="script-summary"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('shows which statement of the script is running', () => {
    const statements = [
      statement({ position: 1 }),
      statement({ position: 2, status: 'RUNNING', result: null }),
      statement({ position: 3, status: 'PENDING', result: null }),
    ]
    const wrapper = render(statements, { running: true, runningIndex: 1 })
    expect(wrapper.get('[data-testid="script-progress"]').text()).toBe('第 2 / 3 条')
    wrapper.unmount()
  })
})
