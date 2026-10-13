import type { OptimizationReport } from './contracts'
function object(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}
function strings(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((v) => typeof v === 'string')
}
function rule(value: unknown): boolean {
  return Number.isInteger(value) && Number(value) > 0
}
export function isOptimizationReport(value: unknown): value is OptimizationReport {
  if (
    !object(value) ||
    typeof value.verdict !== 'string' ||
    !Array.isArray(value.issues) ||
    !Array.isArray(value.suggestions) ||
    !Array.isArray(value.limitations)
  )
    return false
  const ids = new Set<string>()
  for (const issue of value.issues) {
    if (
      !object(issue) ||
      typeof issue.id !== 'string' ||
      !issue.id ||
      ids.has(issue.id) ||
      !rule(issue.ruleId) ||
      !['POLICY', 'PERFORMANCE'].includes(String(issue.category)) ||
      !['重要', '一般'].includes(String(issue.priority)) ||
      ![null, 'HIGH', 'MEDIUM', 'LOW'].includes(issue.severity as string | null) ||
      typeof issue.detail !== 'string' ||
      !Array.isArray(issue.evidence)
    )
      return false
    if (
      !issue.evidence.every(
        (e) =>
          object(e) && ['sql', 'tool'].includes(String(e.kind)) && typeof e.content === 'string',
      )
    )
      return false
    ids.add(issue.id)
  }
  const suggestionIds = new Set<string>()
  for (const suggestion of value.suggestions) {
    if (
      !object(suggestion) ||
      typeof suggestion.id !== 'string' ||
      !suggestion.id ||
      suggestionIds.has(suggestion.id) ||
      !strings(suggestion.issueIds) ||
      !suggestion.issueIds.every((id) => ids.has(id)) ||
      typeof suggestion.detail !== 'string'
    )
      return false
    suggestionIds.add(suggestion.id)
  }
  return value.limitations.every(
    (l) => object(l) && (l.ruleId === null || rule(l.ruleId)) && typeof l.reason === 'string',
  )
}
