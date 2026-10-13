<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { getOptimizationTask, retryOptimizationTask } from '@/api/sql-optimization'
import { isApiError, safeErrorMessage } from '@/api/api-error'
import { randomUUID } from '@/uuid'
import { queryClient } from '@/query/client'
import {
  activeTask,
  formatTaskTime,
  optimizerKey,
  queryErrorMessage,
  statusLabels,
  useOptimizerAccess,
  usePageVisibility,
} from '@/sql-optimizer/queries'
import OptimizationReport from '@/components/sql-optimizer/OptimizationReport.vue'
const route = useRoute()
const router = useRouter()
const { allowed, scope } = useOptimizerAccess()
const visible = usePageVisibility()
const id = computed(() => String(route.params.taskId || ''))
const query = useQuery({
  queryKey: computed(() => optimizerKey(...scope.value, 'detail', id.value)),
  queryFn: ({ signal }) => getOptimizationTask(id.value, signal),
  enabled: computed(() => allowed.value && Boolean(id.value)),
  refetchInterval: (q) =>
    visible.value && q.state.data && activeTask(q.state.data.status) ? 5000 : false,
  refetchIntervalInBackground: false,
  refetchOnWindowFocus: 'always',
})
watch(visible, (value) => {
  if (value && allowed.value) void query.refetch()
})
const denied = computed(
  () => isApiError(query.error.value) && [403, 404].includes(query.error.value.status),
)
const task = computed(() => (denied.value ? undefined : query.data.value))
const terminal = computed(() => Boolean(task.value && !activeTask(task.value.status)))
const retryKey = ref<string | null>(null)
const retryMessage = ref('')
const uncertain = ref(false)
watch(id, () => {
  retryKey.value = null
  retryMessage.value = ''
  uncertain.value = false
})
const retryMutation = useMutation({
  mutationFn: (op: { id: string; key: string }) => retryOptimizationTask(op.id, op.key),
  retry: false,
  onSuccess: async (accepted) => {
    retryKey.value = null
    await queryClient.invalidateQueries({ queryKey: optimizerKey(...scope.value, 'list') })
    await router.push(`/sql-optimizer/tasks/${encodeURIComponent(accepted.id)}`)
  },
  onError: (error) => {
    uncertain.value = isApiError(error) && (error.status === 0 || error.status >= 500)
    retryMessage.value = uncertain.value
      ? '尚未确认重扫是否提交成功。可使用原请求找回新任务，原报告保持不变。'
      : safeErrorMessage(error)
  },
})
function retry() {
  if (!allowed.value || !terminal.value || retryMutation.isPending.value) return
  retryKey.value ||= randomUUID()
  retryMutation.mutate({ id: id.value, key: retryKey.value })
}
</script>
<template>
  <section class="space-y-5">
    <RouterLink to="/sql-optimizer" class="text-sm text-brand">← 返回 SQL 优化器</RouterLink>
    <p v-if="!allowed" class="panel p-8" role="alert">当前账号没有 SQL 优化器能力。</p>
    <template v-else>
      <div v-if="query.isError.value" class="panel p-5 text-danger" role="alert">
        {{ queryErrorMessage(query.error.value)
        }}<span v-if="task"> 最近已知状态保留，查询错误不代表任务失败。</span
        ><button class="btn ml-3" type="button" @click="query.refetch()">刷新任务</button>
      </div>
      <p v-if="query.isPending.value" class="panel p-8" role="status">正在加载任务…</p>
      <template v-if="task">
        <header class="panel p-5">
          <div class="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h1 class="text-2xl font-semibold">{{ task.name }}</h1>
              <p class="mt-2 text-sm text-muted">{{ task.datasourceName }} · {{ task.database }}</p>
            </div>
            <span class="rounded-lg bg-brand-soft px-4 py-2 font-medium text-brand" role="status">{{
              statusLabels[task.status]
            }}</span>
          </div>
          <p class="mt-4 text-xs text-muted">
            创建：{{ formatTaskTime(task.createdAt) }} · 开始：{{
              formatTaskTime(task.startedAt)
            }}
            · 完成：{{ formatTaskTime(task.completedAt) }}
          </p>
          <p v-if="task.sourceTaskId" class="mt-3 text-sm">
            重扫来源：<RouterLink
              class="text-brand"
              :to="`/sql-optimizer/tasks/${encodeURIComponent(task.sourceTaskId)}`"
              >原任务</RouterLink
            >
          </p>
          <p v-if="activeTask(task.status)" class="mt-4 text-sm text-muted">
            任务已提交，可稍后在扫描任务中查看报告。离开页面无需保持连接。
          </p>
          <div class="mt-4 flex flex-wrap gap-3">
            <button
              class="btn"
              type="button"
              :disabled="query.isFetching.value"
              @click="query.refetch()"
            >
              刷新任务</button
            ><button
              v-if="terminal"
              class="btn-primary"
              type="button"
              :disabled="retryMutation.isPending.value"
              @click="retry"
            >
              {{
                retryMutation.isPending.value
                  ? '正在提交…'
                  : uncertain
                    ? '用原请求找回重扫任务'
                    : '再次扫描'
              }}
            </button>
          </div>
          <p v-if="retryMessage" class="mt-3 text-sm text-danger" role="alert">
            {{ retryMessage }}
          </p>
        </header>
        <section class="panel p-5">
          <h2 class="mb-3 text-lg font-semibold">只读 SQL 快照</h2>
          <pre
            class="overflow-x-auto whitespace-pre-wrap break-all rounded-lg bg-subtle p-4 text-sm"
            >{{ task.sql }}</pre
          >
        </section>
        <div v-if="task.status === 'FAILED'" class="panel p-5" role="alert">
          <h2 class="text-lg font-semibold text-danger">扫描失败</h2>
          <p class="mt-3">{{ task.error?.message || '任务未能完成，请稍后重扫。' }}</p>
          <p
            v-if="
              task.error?.code === 'CREDENTIAL_EXPIRED' ||
              task.error?.code === 'CREDENTIAL_UNAVAILABLE'
            "
            class="mt-3 text-sm text-muted"
          >
            请重新登录后再次扫描。退出登录可能影响尚未完成的任务。
          </p>
          <p v-if="task.error?.requestId" class="mt-3 text-xs text-muted">
            请求编号：{{ task.error.requestId }}
          </p>
        </div>
        <OptimizationReport
          v-if="task.status === 'SUCCEEDED'"
          :report="task.report"
          :schema-version="task.reportSchemaVersion"
        />
      </template>
    </template>
  </section>
</template>
