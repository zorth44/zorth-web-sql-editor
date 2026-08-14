<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { getHistory, listHistory } from '@/api/history'
import { safeErrorMessage } from '@/api/api-error'
import type { HistoryDetail, HistoryListParams, HistorySummary } from '@/types/contracts'
const props = defineProps<{ dataSourceId: string | null }>()
const emit = defineEmits<{ open: [detail: HistoryDetail]; notice: [message: string] }>()
const keyword = ref(''),
  status = ref(''),
  items = ref<HistorySummary[]>([]),
  next = ref<string | null>(null),
  loading = ref(false),
  error = ref('')
let timer: number | undefined
async function load(append = false) {
  loading.value = true
  error.value = ''
  try {
    const params: HistoryListParams = { keyword: keyword.value, pageSize: 30 }
    if (props.dataSourceId) params.dataSourceId = props.dataSourceId
    if (status.value)
      params.status = status.value as Exclude<HistoryListParams['status'], undefined>
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
watch([keyword, status, () => props.dataSourceId], () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => void load(), 250)
})
onMounted(() => load())
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-white">
    <div class="space-y-2 border-b border-line p-3">
      <strong class="text-sm">执行历史</strong
      ><input v-model="keyword" class="field py-1.5 text-xs" placeholder="搜索 SQL" /><select
        v-model="status"
        class="field py-1.5 text-xs"
      >
        <option value="">全部状态</option>
        <option v-for="value in ['SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT']" :key="value">
          {{ value }}
        </option>
      </select>
    </div>
    <div class="min-h-0 flex-1 overflow-auto">
      <p v-if="error" class="p-3 text-xs text-danger">{{ error }}</p>
      <button
        v-for="item in items"
        :key="item.id"
        class="block w-full border-b border-line p-3 text-left hover:bg-slate-50"
        @click="open(item.id)"
      >
        <span class="block truncate font-mono text-xs">{{ item.statementSummary }}</span
        ><span class="mt-1 flex justify-between text-[11px] text-muted"
          ><span>{{ item.status }} · {{ item.dataSourceName }}</span
          ><span>{{ new Date(item.startedAt).toLocaleString() }}</span></span
        ></button
      ><button
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
