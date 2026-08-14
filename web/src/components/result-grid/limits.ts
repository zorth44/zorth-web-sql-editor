export const DEFAULT_ROW_LIMIT = 1000
export const MIN_ROW_LIMIT = 1
export const MAX_ROW_LIMIT = 100000

export function clampRowLimit(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_ROW_LIMIT
  return Math.min(MAX_ROW_LIMIT, Math.max(MIN_ROW_LIMIT, Math.trunc(value)))
}
