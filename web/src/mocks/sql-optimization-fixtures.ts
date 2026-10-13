import type { OptimizationReport } from '@/sql-optimizer/contracts'

export const mixedReport: OptimizationReport = {
  verdict: '发现有证据支持的规范与性能问题，建议结合业务场景评估。',
  issues: [
    {
      id: 'policy-1',
      ruleId: 1,
      category: 'POLICY',
      priority: '重要',
      severity: null,
      evidence: [{ kind: 'sql', content: 'SELECT * FROM orders' }],
      detail: '通配符使返回列与表结构耦合。',
    },
    {
      id: 'performance-1',
      ruleId: 8,
      category: 'PERFORMANCE',
      priority: '一般',
      severity: 'HIGH',
      evidence: [{ kind: 'tool', content: 'EXPLAIN: access_type=ALL; rows=12000' }],
      detail: '本次计划显示全表扫描，请结合过滤条件评估。',
    },
  ],
  suggestions: [
    {
      id: 'suggestion-1',
      issueIds: ['policy-1', 'performance-1'],
      detail: '明确需要的列，并评估查询条件与现有索引的匹配关系。',
    },
  ],
  limitations: [{ ruleId: 17, reason: '未取得实际运行耗时，本报告不量化提速。' }],
}
export const cleanReport: OptimizationReport = {
  verdict: '本次未发现有证据支持的问题。',
  issues: [],
  suggestions: [],
  limitations: [],
}
export const limitedReport: OptimizationReport = {
  verdict: '本次证据不足，未作完整结论。',
  issues: [],
  suggestions: [],
  limitations: [{ ruleId: null, reason: '未取得执行计划，无法判断访问路径。' }],
}
