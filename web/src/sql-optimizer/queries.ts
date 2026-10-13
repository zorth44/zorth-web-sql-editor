import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { isApiError, safeErrorMessage } from '@/api/api-error'
import type { TaskStatus } from './contracts'

export const statusLabels: Record<TaskStatus, string> = {
  QUEUED: '排队中',
  RUNNING: '分析中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
}
export function activeTask(status: TaskStatus): boolean {
  return status === 'QUEUED' || status === 'RUNNING'
}
export function optimizerKey(
  userId: string,
  productId: string,
  ...parts: unknown[]
): readonly unknown[] {
  return ['sql-optimization', userId, productId, ...parts]
}
export function useOptimizerAccess() {
  const auth = useAuthStore()
  const allowed = computed(() => Boolean(auth.session?.capabilities.includes('SQL_OPTIMIZE')))
  const scope = computed(
    () => [auth.session?.user.id || '', auth.session?.product.id || ''] as const,
  )
  return { allowed, scope }
}
export function usePageVisibility() {
  const visible = ref(document.visibilityState !== 'hidden')
  function update() {
    visible.value = document.visibilityState !== 'hidden'
  }
  onMounted(() => document.addEventListener('visibilitychange', update))
  onBeforeUnmount(() => document.removeEventListener('visibilitychange', update))
  return visible
}
export function queryErrorMessage(error: unknown): string {
  if (isApiError(error) && error.status === 404) return '任务不存在或不可见。'
  if (isApiError(error) && error.status === 403) return '当前账号没有 SQL 优化器能力。'
  return safeErrorMessage(error, '暂时无法查询任务，请刷新重试。')
}
export function formatTaskTime(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN')
}
export function relativeTaskTime(value: string | null, now = Date.now()): string {
  if (!value) return '—'
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) return '—'
  const seconds = Math.round((time - now) / 1000)
  const abs = Math.abs(seconds)
  const rtf = new Intl.RelativeTimeFormat('zh-CN', { numeric: 'auto' })
  if (abs < 60) return rtf.format(seconds, 'second')
  const minutes = Math.round(seconds / 60)
  if (Math.abs(minutes) < 60) return rtf.format(minutes, 'minute')
  const hours = Math.round(seconds / 3600)
  if (Math.abs(hours) < 24) return rtf.format(hours, 'hour')
  const days = Math.round(seconds / 86400)
  if (Math.abs(days) < 30) return rtf.format(days, 'day')
  return formatTaskTime(value)
}
