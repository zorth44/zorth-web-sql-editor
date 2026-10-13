import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import OptimizationReport from './OptimizationReport.vue'
import { cleanReport, limitedReport, mixedReport } from '@/mocks/sql-optimization-fixtures'

describe('optimizer reports', () => {
  it('distinguishes severity and organization priority, locates linked issues', async () => {
    const scroll = vi.fn()
    HTMLElement.prototype.scrollIntoView = scroll
    const wrapper = mount(OptimizationReport, {
      props: { report: mixedReport, schemaVersion: 1 },
      attachTo: document.body,
    })
    const issues = wrapper.findAll('[data-issue-id]')
    expect(issues[0]!.text()).toContain('组织重要性：重要')
    expect(issues[0]!.text()).not.toContain('性能严重度')
    expect(issues[1]!.text()).toContain('性能严重度：HIGH')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '定位问题 policy-1')!
      .trigger('click')
    expect(scroll).toHaveBeenCalled()
    expect(document.activeElement).toBe(issues[0]!.element)
    expect(wrapper.text()).toContain('工具事实')
    expect(wrapper.text()).toContain('SQL 片段')
    wrapper.unmount()
  })
  it.each([cleanReport, limitedReport])(
    'never claims all rules passed with empty issues',
    (report) => {
      const wrapper = mount(OptimizationReport, { props: { report, schemaVersion: 1 } })
      expect(wrapper.text()).toContain('本次未发现有证据支持的问题')
      expect(wrapper.text()).toContain('证据缺口 / 未作结论')
      if (report.limitations.length) expect(wrapper.text()).toContain('不能据此判定全部规则通过')
    },
  )
  it('renders hostile content as plain text across every report field', () => {
    const report = structuredClone(mixedReport)
    const hostile = '<img src=x onerror="alert(1)"><script>boom()</script>'
    report.verdict = hostile
    report.issues[0]!.detail = hostile
    report.issues[0]!.evidence[0]!.content = hostile
    report.suggestions[0]!.detail = hostile
    report.limitations[0]!.reason = hostile
    const wrapper = mount(OptimizationReport, { props: { report, schemaVersion: 1 } })
    expect(wrapper.text().split(hostile)).toHaveLength(6)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
  })
  it.each<{ report: unknown; schemaVersion: number }>([
    { report: mixedReport, schemaVersion: 2 },
    { report: { verdict: 'bad', issues: null }, schemaVersion: 1 },
    {
      report: { ...mixedReport, suggestions: [{ id: 's', issueIds: ['missing'], detail: 'bad' }] },
      schemaVersion: 1,
    },
  ])('rejects unsupported or invalid report shapes', (props) => {
    const wrapper = mount(OptimizationReport, { props })
    expect(wrapper.get('[role="alert"]').text()).toContain('报告格式异常')
    expect(wrapper.find('[data-issue-id]').exists()).toBe(false)
  })
})
