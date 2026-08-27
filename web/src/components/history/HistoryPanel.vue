<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getHistory, listHistory } from '@/api/history'
import { safeErrorMessage } from '@/api/api-error'
import { queryKeys } from '@/query/client'
import type { HistoryDetail, HistoryListParams } from '@/types/contracts'

const PAGE_SIZE = 30
const props = defineProps<{ dataSourceId: string | null }>()
const emit = defineEmits<{ open: [detail: HistoryDetail]; notice: [message: string] }>()
const inputKeyword = ref('')
const keyword = ref('')
const status = ref('')
const statementType = ref('')
const cursors = ref<string[]>([''])
const pageIndex = ref(0)
let timer: number | undefined

const statusLabel: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELLED: '已取消',
  TIMEOUT: '超时',
  RUNNING: '运行中',
}

const currentCursor = computed(() => cursors.value[pageIndex.value] || undefined)
const filters = computed(() => ({
  keyword: keyword.value,
  dataSourceId: props.dataSourceId || '',
  status: status.value,
  statementType: statementType.value,
  pageSize: PAGE_SIZE,
  pageToken: currentCursor.value || '',
}))
const listQuery = useQuery({
  queryKey: computed(() => queryKeys.history(filters.value)),
  queryFn: () => {
    const params: HistoryListParams = { keyword: keyword.value, pageSize: PAGE_SIZE }
    if (props.dataSourceId) params.dataSourceId = props.dataSourceId
    if (status.value)
      params.status = status.value as Exclude<HistoryListParams['status'], undefined>
    if (statementType.value)
      params.statementType = statementType.value as Exclude<
        HistoryListParams['statementType'],
        undefined
      >
    if (currentCursor.value) params.pageToken = currentCursor.value
    return listHistory(params)
  },
  staleTime: 30_000,
})
const items = computed(() => listQuery.data.value?.items ?? [])
const hasPager = computed(() => pageIndex.value > 0 || Boolean(listQuery.data.value?.nextPageToken))

function resetPaging(): void {
  cursors.value = ['']
  pageIndex.value = 0
}
function nextPage(): void {
  const token = listQuery.data.value?.nextPageToken
  if (!token) return
  cursors.value = [...cursors.value.slice(0, pageIndex.value + 1), token]
  pageIndex.value += 1
}
function previousPage(): void {
  if (pageIndex.value > 0) pageIndex.value -= 1
}
async function open(id: string) {
  try {
    emit('open', await getHistory(id))
  } catch (e) {
    emit('notice', safeErrorMessage(e, '历史详情加载失败'))
  }
}
function formatTime(value: string): string {
  return new Date(value).toLocaleString()
}

watch(inputKeyword, (value) => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => {
    keyword.value = value
    resetPaging()
  }, 250)
})
watch([status, statementType, () => props.dataSourceId], () => {
  resetPaging()
})
onBeforeUnmount(() => window.clearTimeout(timer))
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-panel" data-testid="history-panel">
    <div class="space-y-2 border-b border-line px-3 py-2.5">
      <strong class="text-sm">执行历史</strong>
      <input v-model="inputKeyword" class="field py-1.5 text-xs" placeholder="搜索 SQL" />
      <div class="grid grid-cols-2 gap-2">
        <select v-model="status" class="field py-1.5 text-xs" aria-label="按状态筛选">
          <option value="">全部状态</option>
          <option
            v-for="value in ['SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT']"
            :key="value"
            :value="value"
          >
            {{ statusLabel[value] }}
          </option>
        </select>
        <select v-model="statementType" class="field py-1.5 text-xs" aria-label="按语句类型筛选">
          <option value="">全部类型</option>
          <option
            v-for="value in ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'REPLACE', 'DDL', 'OTHER']"
            :key="value"
            :value="value"
          >
            {{ value }}
          </option>
        </select>
      </div>
    </div>
    <div class="min-h-0 flex-1 overflow-auto">
      <p v-if="listQuery.isError.value" class="p-3 text-xs text-danger">
        {{ safeErrorMessage(listQuery.error.value, '历史加载失败') }}
      </p>
      <button
        v-for="item in items"
        :key="item.id"
        class="block w-full border-b border-line px-3 py-2.5 text-left hover:bg-wash"
        :aria-label="`${item.statementSummary} ${item.status}`"
        @click="open(item.id)"
      >
        <span class="block truncate font-mono text-xs">{{ item.statementSummary }}</span>
        <span class="mt-1 flex items-center justify-between gap-2 text-[11px] text-muted">
          <span class="flex min-w-0 items-center gap-1.5">
            <span
              class="h-1.5 w-1.5 shrink-0 rounded-full"
              :class="{
                'bg-success': item.status === 'SUCCESS',
                'bg-danger': item.status === 'FAILED',
                'bg-muted': item.status === 'CANCELLED',
                'bg-amber-500': item.status === 'TIMEOUT',
              }"
            />
            <span>{{ statusLabel[item.status] || item.status }}</span>
            <span>·</span>
            <span class="truncate">{{ item.dataSourceName }}</span>
            <span v-if="item.database">/ {{ item.database }}</span>
          </span>
          <span class="shrink-0">{{ item.durationMs != null ? `${item.durationMs} ms` : '' }}</span>
        </span>
        <span class="mt-0.5 block text-[11px] text-muted">{{ formatTime(item.startedAt) }}</span>
      </button>
      <p
        v-if="!items.length && !listQuery.isPending.value && !listQuery.isError.value"
        class="p-4 text-center text-xs text-muted"
      >
        暂无执行历史
      </p>
    </div>
    <footer
      v-if="hasPager"
      class="flex items-center justify-between gap-2 border-t border-line px-3 py-2"
      data-testid="history-pager"
    >
      <span class="shrink-0 text-[11px] text-muted">第 {{ pageIndex + 1 }} 页</span>
      <div class="flex gap-1">
        <button
          class="btn px-2 py-1 text-xs"
          type="button"
          :disabled="pageIndex === 0 || listQuery.isFetching.value"
          @click="previousPage"
        >
          上一页
        </button>
        <button
          class="btn px-2 py-1 text-xs"
          type="button"
          :disabled="!listQuery.data.value?.nextPageToken || listQuery.isFetching.value"
          @click="nextPage"
        >
          下一页
        </button>
      </div>
    </footer>
  </section>
</template>
