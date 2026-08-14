<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { Plus, Search, FlaskConical, Pencil, Trash2 } from 'lucide-vue-next'
import { deleteDataSource, listDataSources, testSavedDataSource } from '@/api/data-sources'
import { safeErrorMessage } from '@/api/api-error'
import { canManageDataSources } from '@/api/session'
import { dataSourceInUse } from '@/data-sources/api-errors'
import { queryClient, queryKeys } from '@/query/client'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import type { DataSourceListItem } from '@/types/contracts'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import LoadingState from '@/components/LoadingState.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const auth = useAuthStore()
const notices = useNotificationsStore()
const inputKeyword = ref('')
const keyword = ref('')
const pageSize = ref(20)
const cursors = ref<string[]>([''])
const pageIndex = ref(0)
const selected = ref<DataSourceListItem | null>(null)
const confirmName = ref('')
const deleteMessage = ref('')
let debounceTimer: number | undefined
const currentCursor = computed(() => cursors.value[pageIndex.value] || undefined)
const manageable = computed(() => canManageDataSources(auth.session || undefined))
const listQuery = useQuery({
  queryKey: computed(() =>
    queryKeys.dataSourceList(keyword.value, pageSize.value, currentCursor.value),
  ),
  queryFn: () =>
    listDataSources({
      keyword: keyword.value,
      pageSize: pageSize.value,
      ...(currentCursor.value ? { pageToken: currentCursor.value } : {}),
    }),
  staleTime: 30_000,
})

watch(inputKeyword, (value) => {
  window.clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(() => {
    keyword.value = value.trim()
    cursors.value = ['']
    pageIndex.value = 0
  }, 350)
})
watch(pageSize, () => {
  cursors.value = ['']
  pageIndex.value = 0
})
onBeforeUnmount(() => window.clearTimeout(debounceTimer))
function nextPage(): void {
  const token = listQuery.data.value?.nextPageToken
  if (!token) return
  cursors.value = [...cursors.value.slice(0, pageIndex.value + 1), token]
  pageIndex.value += 1
}
function previousPage(): void {
  if (pageIndex.value > 0) pageIndex.value -= 1
}

const testMutation = useMutation({
  mutationFn: (id: string) => testSavedDataSource(id),
  retry: false,
  onSuccess: async (result, id) => {
    notices.push(
      result.status === 'SUCCESS' ? 'success' : 'error',
      `${result.message}${result.serverVersion ? ` · MySQL ${result.serverVersion}` : ''} · ${result.durationMs}ms`,
    )
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.dataSourceLists() }),
      queryClient.invalidateQueries({ queryKey: queryKeys.dataSourceDetail(id) }),
    ])
  },
  onError: (error) => notices.push('error', safeErrorMessage(error)),
})

const deleteMutation = useMutation({
  mutationFn: (item: DataSourceListItem) => deleteDataSource(item.id, item.version),
  retry: false,
  onSuccess: async (_value, item) => {
    notices.push('success', `已删除“${item.name}”`)
    resetDeleteDialog()
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.dataSourceLists() }),
      queryClient.removeQueries({ queryKey: queryKeys.dataSourceDetail(item.id) }),
    ])
  },
  onError: (error) => {
    const inUse = dataSourceInUse(error)
    deleteMessage.value = inUse
      ? `该数据源有 ${inUse.runningTaskCount} 个任务正在执行，暂时无法删除。`
      : safeErrorMessage(error)
  },
})
function openDelete(item: DataSourceListItem): void {
  selected.value = item
  confirmName.value = ''
  deleteMessage.value = ''
}
function resetDeleteDialog(): void {
  selected.value = null
  confirmName.value = ''
  deleteMessage.value = ''
}
function closeDelete(): void {
  if (!deleteMutation.isPending.value) resetDeleteDialog()
}
function formatTime(value: string | null): string {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(value),
      )
    : '—'
}
</script>
<template>
  <section>
    <div class="mb-6 flex items-end justify-between">
      <div>
        <p class="text-sm font-medium text-brand">连接管理</p>
        <h1 class="mt-1 text-3xl font-semibold">数据源</h1>
        <p class="mt-2 text-sm text-muted">
          当前产品：{{ auth.session?.product.name }}。列表由服务端按产品范围返回。
        </p>
      </div>
      <RouterLink v-if="manageable" class="btn-primary" to="/data-sources/new"
        ><Plus :size="17" />新增数据源</RouterLink
      >
    </div>
    <div v-if="!manageable" class="panel p-8 text-center text-muted" role="alert">
      当前账号没有数据源管理能力。
    </div>
    <template v-else
      ><div class="panel mb-5 flex items-center gap-4 p-4">
        <label class="relative flex-1"
          ><span class="sr-only">按名称或 Host 搜索</span
          ><Search class="absolute left-3 top-1/2 -translate-y-1/2 text-muted" :size="18" /><input
            v-model="inputKeyword"
            class="field pl-10"
            placeholder="搜索名称或 Host" /></label
        ><label class="flex items-center gap-2 text-sm text-muted"
          >每页<select v-model.number="pageSize" class="field w-24">
            <option :value="2">2</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select></label
        >
      </div>
      <LoadingState v-if="listQuery.isPending.value" label="正在加载数据源…" /><ErrorState
        v-else-if="listQuery.isError.value"
        :message="safeErrorMessage(listQuery.error.value)"
        retryable
        @retry="listQuery.refetch()"
      />
      <div v-else class="panel overflow-hidden">
        <EmptyState
          v-if="!listQuery.data.value?.items.length"
          title="没有找到数据源"
          :description="keyword ? '请调整搜索关键词。' : '创建第一个数据源，开始配置数据库连接。'"
          ><RouterLink v-if="!keyword" class="btn-primary" to="/data-sources/new"
            >新增数据源</RouterLink
          ></EmptyState
        >
        <div v-else class="overflow-x-auto">
          <table class="w-full border-collapse text-left text-sm">
            <thead class="bg-subtle text-xs uppercase tracking-wide text-muted">
              <tr>
                <th class="px-5 py-3">名称</th>
                <th class="px-5 py-3">连接</th>
                <th class="px-5 py-3">默认数据库</th>
                <th class="px-5 py-3">最近测试</th>
                <th class="px-5 py-3">更新信息</th>
                <th class="px-5 py-3 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-line">
              <tr
                v-for="item in listQuery.data.value.items"
                :key="item.id"
                :data-testid="`data-source-${item.id}`"
                class="hover:bg-wash"
              >
                <td class="px-5 py-4">
                  <strong class="block">{{ item.name }}</strong
                  ><span class="text-xs text-muted">MySQL · {{ item.id }}</span>
                </td>
                <td class="px-5 py-4">
                  <span class="block">{{ item.host }}:{{ item.port }}</span
                  ><span class="text-xs text-muted">{{ item.username }}</span>
                </td>
                <td class="px-5 py-4">{{ item.defaultDatabase || '—' }}</td>
                <td class="px-5 py-4">
                  <StatusBadge :status="item.lastTestStatus" /><span
                    class="mt-1 block text-xs text-muted"
                    >{{ formatTime(item.lastTestAt) }}</span
                  >
                </td>
                <td class="px-5 py-4">
                  <span class="block">{{ formatTime(item.updatedAt) }}</span
                  ><span class="text-xs text-muted">{{ item.updatedByName }}</span>
                </td>
                <td class="px-5 py-4">
                  <div class="flex justify-end gap-2">
                    <button
                      class="btn px-3"
                      type="button"
                      :disabled="testMutation.isPending.value"
                      :aria-label="`测试 ${item.name}`"
                      @click="testMutation.mutate(item.id)"
                    >
                      <FlaskConical :size="16" />测试</button
                    ><RouterLink
                      class="btn px-3"
                      :to="`/data-sources/${encodeURIComponent(item.id)}/edit`"
                      :aria-label="`编辑 ${item.name}`"
                      ><Pencil :size="16" />编辑</RouterLink
                    ><button
                      class="btn px-3 text-danger"
                      type="button"
                      :aria-label="`删除 ${item.name}`"
                      @click="openDelete(item)"
                    >
                      <Trash2 :size="16" />删除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer
          v-if="listQuery.data.value?.items.length"
          class="flex items-center justify-between border-t border-line px-5 py-4"
        >
          <span class="text-sm text-muted">第 {{ pageIndex + 1 }} 页</span>
          <div class="flex gap-2">
            <button
              class="btn"
              type="button"
              :disabled="pageIndex === 0 || listQuery.isFetching.value"
              @click="previousPage"
            >
              上一页</button
            ><button
              class="btn"
              type="button"
              :disabled="!listQuery.data.value?.nextPageToken || listQuery.isFetching.value"
              @click="nextPage"
            >
              下一页
            </button>
          </div>
        </footer>
      </div></template
    ><ConfirmDialog
      :open="Boolean(selected)"
      title="确认删除数据源"
      :busy="deleteMutation.isPending.value"
      :confirm-disabled="!selected || confirmName !== selected.name"
      @close="closeDelete"
      @confirm="selected && confirmName === selected.name && deleteMutation.mutate(selected)"
      ><template v-if="selected"
        ><p class="text-sm leading-6 text-muted">
          将删除 <strong class="text-ink">{{ selected.name }}</strong
          >（{{ selected.host }}:{{ selected.port }}）。即使存在同名数据源，也只会删除 ID 为
          <code>{{ selected.id }}</code> 的记录。
        </p>
        <label class="label mt-4" for="confirm-name">输入完整名称以确认</label
        ><input id="confirm-name" v-model="confirmName" class="field" autocomplete="off" />
        <p
          v-if="deleteMessage"
          class="mt-3 rounded-lg bg-danger-soft p-3 text-sm text-danger"
          role="alert"
        >
          {{ deleteMessage }}
        </p>
        <p v-if="confirmName !== selected.name" class="mt-2 text-xs text-muted">
          名称完全匹配后才能确认。
        </p></template
      ></ConfirmDialog
    >
  </section>
</template>
