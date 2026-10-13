<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { listOptimizationTasks } from '@/api/sql-optimization'
import { isApiError } from '@/api/api-error'
import { useRouter } from 'vue-router'
import {
  activeTask,
  formatTaskTime,
  optimizerKey,
  queryErrorMessage,
  relativeTaskTime,
  statusLabels,
  useOptimizerAccess,
  usePageVisibility,
} from '@/sql-optimizer/queries'
import type { TaskListItem, TaskStatus } from '@/sql-optimizer/contracts'
const router = useRouter()
const { allowed, scope } = useOptimizerAccess()
const filters: { value: TaskStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  ...Object.entries(statusLabels).map(([value, label]) => ({
    value: value as TaskStatus,
    label,
  })),
]
const statusTone: Record<TaskStatus, string> = {
  QUEUED: 'bg-warning-soft text-warning',
  RUNNING: 'bg-brand-soft text-brand',
  SUCCEEDED: 'bg-success-soft text-success',
  FAILED: 'bg-danger-soft text-danger',
}
const visible = usePageVisibility()
const status = ref<TaskStatus | ''>('')
const pageSize = ref(20)
const cursors = ref<string[]>([''])
const pageIndex = ref(0)
const cursor = computed(() => cursors.value[pageIndex.value] || '')
watch([status, pageSize], () => {
  cursors.value = ['']
  pageIndex.value = 0
})
const query = useQuery({
  queryKey: computed(() =>
    optimizerKey(...scope.value, 'list', status.value, pageSize.value, cursor.value),
  ),
  queryFn: ({ signal }) =>
    listOptimizationTasks(
      {
        ...(status.value ? { status: status.value } : {}),
        pageSize: pageSize.value,
        ...(cursor.value ? { pageToken: cursor.value } : {}),
      },
      signal,
    ),
  enabled: allowed,
  refetchInterval: (q) =>
    visible.value && q.state.data?.items.some((t) => activeTask(t.status)) ? 5000 : false,
  refetchIntervalInBackground: false,
  refetchOnWindowFocus: 'always',
})
const data = computed(() =>
  isApiError(query.error.value) && [403, 404].includes(query.error.value.status)
    ? undefined
    : query.data.value,
)
watch(visible, (value) => {
  if (value && allowed.value) void query.refetch()
})
function openTask(task: TaskListItem) {
  void router.push(`/sql-optimizer/tasks/${encodeURIComponent(task.id)}`)
}
function next() {
  const token = data.value?.nextPageToken
  if (!token || query.isFetching.value) return
  cursors.value = [...cursors.value.slice(0, pageIndex.value + 1), token]
  pageIndex.value += 1
}
</script>
<template>
  <section class="panel overflow-hidden">
    <header class="flex flex-wrap items-center gap-3 border-b border-line p-4">
      <div class="flex flex-wrap gap-1" role="group" aria-label="任务状态">
        <button
          v-for="item in filters"
          :key="item.label"
          class="rounded-lg px-3 py-1.5 text-sm"
          :class="
            status === item.value
              ? 'bg-brand-soft font-medium text-brand'
              : 'text-muted hover:bg-wash'
          "
          :aria-pressed="status === item.value"
          type="button"
          @click="status = item.value"
        >
          {{ item.label }}
        </button>
      </div>
      <button
        class="btn ml-auto"
        type="button"
        :disabled="query.isFetching.value"
        @click="query.refetch()"
      >
        刷新列表
      </button>
    </header>
    <div v-if="query.isError.value" class="p-4 text-sm text-danger" role="alert">
      {{ queryErrorMessage(query.error.value) }}
      <span v-if="data">最近已知任务状态保留；请刷新列表。</span>
    </div>
    <p v-if="query.isPending.value" class="p-8 text-muted" role="status">正在加载任务…</p>
    <div v-else-if="data && !data.items.length" class="p-8 text-center text-muted">
      <p>暂无扫描任务。提交一条 SQL 开始分析。</p>
      <RouterLink class="btn-primary mt-4" to="/sql-optimizer/new">新建扫描</RouterLink>
    </div>
    <div v-if="data?.items.length" class="overflow-x-auto">
      <table class="w-full text-left text-sm">
        <thead class="bg-subtle text-xs text-muted">
          <tr>
            <th class="p-4">任务</th>
            <th class="p-4">SQL</th>
            <th class="p-4">数据源 / 命名空间</th>
            <th class="p-4">状态</th>
            <th class="p-4">时间</th>
            <th class="p-4">计数</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-line">
          <tr
            v-for="task in data.items"
            :key="task.id"
            class="cursor-pointer hover:bg-wash"
            :class="activeTask(task.status) ? 'border-l-2 border-l-brand' : ''"
            tabindex="0"
            @click="openTask(task)"
            @keydown.enter="openTask(task)"
          >
            <td class="p-4 font-medium">
              <RouterLink
                :to="`/sql-optimizer/tasks/${encodeURIComponent(task.id)}`"
                class="text-ink"
                @click.stop
                >{{ task.name }}</RouterLink
              >
            </td>
            <td class="max-w-xs p-4">
              <p class="truncate font-mono text-xs text-muted">{{ task.sqlPreview }}</p>
            </td>
            <td class="p-4">
              {{ task.datasourceName }}
              <p class="mt-1 text-xs text-muted">{{ task.database }}</p>
            </td>
            <td class="whitespace-nowrap p-4">
              <span
                class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold"
                :class="statusTone[task.status]"
              >
                <span
                  v-if="task.status === 'RUNNING'"
                  class="h-1.5 w-1.5 animate-pulse rounded-full bg-brand"
                  aria-hidden="true"
                />
                {{ statusLabels[task.status] }}
              </span>
            </td>
            <td class="p-4 text-xs">
              <time :datetime="task.createdAt" :title="formatTaskTime(task.createdAt)">{{
                relativeTaskTime(task.createdAt)
              }}</time>
              <p v-if="task.completedAt" class="mt-1 text-muted">
                <time :datetime="task.completedAt" :title="formatTaskTime(task.completedAt)">{{
                  relativeTaskTime(task.completedAt)
                }}</time>
              </p>
            </td>
            <td class="p-4 text-xs">
              <template v-if="task.status === 'SUCCEEDED' && task.summary">
                <span class="mr-2">规范 {{ task.summary.policyIssueCount }}</span>
                <span class="mr-2">性能 {{ task.summary.performanceIssueCount }}</span>
                <span class="text-muted">缺口 {{ task.summary.limitationCount }}</span>
              </template>
              <span v-else>—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer class="flex flex-wrap items-center justify-end gap-3 border-t border-line p-4">
      <label class="mr-auto text-sm text-muted"
        >每页<select v-model.number="pageSize" class="field ml-2 w-20" aria-label="每页任务">
          <option :value="2">2</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select></label
      >
      <button
        class="btn"
        type="button"
        :disabled="pageIndex === 0 || query.isFetching.value"
        @click="pageIndex -= 1"
      >
        上一页</button
      ><button
        class="btn"
        type="button"
        :disabled="!data?.nextPageToken || query.isFetching.value"
        @click="next"
      >
        下一页
      </button>
    </footer>
  </section>
</template>
