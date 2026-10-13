<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { listOptimizationTasks } from '@/api/sql-optimization'
import type { TaskStatus } from '@/sql-optimizer/contracts'
import {
  activeTask,
  formatTaskTime,
  optimizerKey,
  queryErrorMessage,
  statusLabels,
  useOptimizerAccess,
  usePageVisibility,
} from '@/sql-optimizer/queries'
const { allowed, scope } = useOptimizerAccess()
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
watch(visible, (value) => {
  if (value && allowed.value) void query.refetch()
})
function next() {
  const token = query.data.value?.nextPageToken
  if (!token || query.isFetching.value) return
  cursors.value = [...cursors.value.slice(0, pageIndex.value + 1), token]
  pageIndex.value += 1
}
</script>
<template>
  <section class="panel mt-6 overflow-hidden">
    <header class="flex flex-wrap items-center gap-4 border-b border-line p-5">
      <h2 class="mr-auto text-lg font-semibold">我的扫描任务</h2>
      <label class="text-sm"
        >状态<select v-model="status" class="field ml-2 w-28" aria-label="任务状态">
          <option value="">全部</option>
          <option v-for="(label, key) in statusLabels" :key="key" :value="key">{{ label }}</option>
        </select></label
      >
      <label class="text-sm"
        >每页<select v-model.number="pageSize" class="field ml-2 w-20" aria-label="每页任务">
          <option :value="2">2</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select></label
      >
      <button class="btn" type="button" :disabled="query.isFetching.value" @click="query.refetch()">
        刷新列表
      </button>
    </header>
    <div v-if="query.isError.value" class="p-4 text-sm text-danger" role="alert">
      {{ queryErrorMessage(query.error.value) }} 最近已知任务状态保留；请刷新列表。
    </div>
    <p v-if="query.isPending.value" class="p-8 text-muted" role="status">正在加载任务…</p>
    <p v-else-if="query.data.value && !query.data.value.items.length" class="p-8 text-muted">
      暂无扫描任务。提交一条 SQL 开始分析。
    </p>
    <div v-if="query.data.value?.items.length" class="overflow-x-auto">
      <table class="w-full text-left text-sm">
        <thead class="bg-subtle text-xs text-muted">
          <tr>
            <th class="p-4">任务 / SQL 摘要</th>
            <th class="p-4">数据源 / 命名空间</th>
            <th class="p-4">状态</th>
            <th class="p-4">创建 / 完成时间</th>
            <th class="p-4">报告计数</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-line">
          <tr v-for="task in query.data.value.items" :key="task.id" class="hover:bg-wash">
            <td class="max-w-sm p-4">
              <RouterLink
                :to="`/sql-optimizer/tasks/${encodeURIComponent(task.id)}`"
                class="font-medium text-brand"
                >{{ task.name }}</RouterLink
              >
              <p class="mt-2 break-all font-mono text-xs text-muted">{{ task.sqlPreview }}</p>
            </td>
            <td class="p-4">
              {{ task.datasourceName }}
              <p class="mt-1 text-xs text-muted">{{ task.database }}</p>
            </td>
            <td class="whitespace-nowrap p-4">{{ statusLabels[task.status] }}</td>
            <td class="p-4 text-xs">
              {{ formatTaskTime(task.createdAt) }}
              <p class="mt-1 text-muted">{{ formatTaskTime(task.completedAt) }}</p>
            </td>
            <td class="p-4 text-xs">
              <template v-if="task.status === 'SUCCEEDED' && task.summary"
                >规范 {{ task.summary.policyIssueCount }} · 性能
                {{ task.summary.performanceIssueCount }}
                <p class="mt-1 text-muted">证据缺口 {{ task.summary.limitationCount }}</p></template
              ><span v-else>—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer class="flex justify-end gap-2 border-t border-line p-4">
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
        :disabled="!query.data.value?.nextPageToken || query.isFetching.value"
        @click="next"
      >
        下一页
      </button>
    </footer>
  </section>
</template>
