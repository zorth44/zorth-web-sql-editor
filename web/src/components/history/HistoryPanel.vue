<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { getHistory, listHistory } from '@/api/history'
import { safeErrorMessage } from '@/api/api-error'
import type { HistoryDetail, HistoryListParams, HistorySummary } from '@/types/contracts'

const props = defineProps<{ dataSourceId: string | null }>()
const emit = defineEmits<{ open: [detail: HistoryDetail]; notice: [message: string] }>()
const keyword = ref('')
const status = ref('')
const statementType = ref('')
const items = ref<HistorySummary[]>([])
const next = ref<string | null>(null)
const loading = ref(false)
const error = ref('')
let timer: number | undefined

const statusLabel: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELLED: '已取消',
  TIMEOUT: '超时',
  RUNNING: '运行中',
}

async function load(append = false) {
  loading.value = true
  error.value = ''
  try {
    const params: HistoryListParams = { keyword: keyword.value, pageSize: 30 }
    if (props.dataSourceId) params.dataSourceId = props.dataSourceId
    if (status.value)
      params.status = status.value as Exclude<HistoryListParams['status'], undefined>
    if (statementType.value)
      params.statementType = statementType.value as Exclude<
        HistoryListParams['statementType'],
        undefined
      >
    if (append && next.value) params.pageToken = next.value
    const page = await listHistory(params)
    items.value = append ? [...items.value, ...page.items] : page.items
    next.value = page.nextPageToken
  } catch (e) {
    error.value = safeErrorMessage(e, '历史加载失败')
  } finally {
    loading.value = false
  }
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
watch([keyword, status, statementType, () => props.dataSourceId], () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => void load(), 250)
})
onMounted(() => load())
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-white">
    <div class="space-y-2 border-b border-line px-3 py-2.5">
      <strong class="text-sm">执行历史</strong>
      <input v-model="keyword" class="field py-1.5 text-xs" placeholder="搜索 SQL" />
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
      <p v-if="error" class="p-3 text-xs text-danger">{{ error }}</p>
      <button
        v-for="item in items"
        :key="item.id"
        class="block w-full border-b border-line px-3 py-2.5 text-left hover:bg-slate-50"
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
                'bg-slate-400': item.status === 'CANCELLED',
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
      <button
        v-if="next"
        class="btn m-3 w-[calc(100%-1.5rem)]"
        :disabled="loading"
        @click="load(true)"
      >
        加载更多
      </button>
      <p v-if="!items.length && !loading" class="p-4 text-center text-xs text-muted">
        暂无执行历史
      </p>
    </div>
  </section>
</template>
