<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Pencil, Trash2 } from 'lucide-vue-next'
import { deleteScript, listScripts } from '@/api/scripts'
import { safeErrorMessage } from '@/api/api-error'
import type { ScriptSummary } from '@/types/contracts'

const emit = defineEmits<{
  open: [id: string]
  rename: [item: ScriptSummary]
  deleted: [id: string]
  notice: [message: string]
}>()
const keyword = ref('')
const items = ref<ScriptSummary[]>([])
const next = ref<string | null>(null)
const loading = ref(false)
const error = ref('')
const pendingDelete = ref<ScriptSummary | null>(null)
let timer: number | undefined

async function load(append = false) {
  loading.value = true
  error.value = ''
  try {
    const page = await listScripts({
      keyword: keyword.value,
      pageSize: 30,
      ...(append && next.value ? { pageToken: next.value } : {}),
    })
    items.value = append ? [...items.value, ...page.items] : page.items
    next.value = page.nextPageToken
  } catch (e) {
    error.value = safeErrorMessage(e, '脚本加载失败')
  } finally {
    loading.value = false
  }
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString()
}

watch(keyword, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => void load(), 250)
})
onMounted(() => load())

defineExpose({ reload: () => load() })
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-panel" data-testid="script-panel">
    <div class="space-y-2 border-b border-line px-3 py-2.5">
      <strong class="text-sm">脚本</strong>
      <input v-model="keyword" class="field py-1.5 text-xs" placeholder="搜索名称或 SQL" />
    </div>
    <div class="min-h-0 flex-1 overflow-auto">
      <p v-if="error" class="p-3 text-xs text-danger">{{ error }}</p>
      <div
        v-for="item in items"
        :key="item.id"
        class="flex items-start gap-1 border-b border-line px-2 py-2.5 hover:bg-wash"
      >
        <button
          class="min-w-0 flex-1 px-1 text-left"
          :aria-label="`打开脚本 ${item.name}`"
          @click="emit('open', item.id)"
        >
          <span class="block truncate text-sm">{{ item.name }}</span>
          <span class="mt-0.5 block truncate font-mono text-[11px] text-muted">{{
            item.statementSummary
          }}</span>
          <span class="mt-0.5 flex justify-between gap-2 text-[11px] text-muted">
            <span class="truncate">
              <template v-if="item.dataSourceName">{{ item.dataSourceName }}</template>
              <template v-if="item.database"> / {{ item.database }}</template>
              <template v-if="!item.dataSourceName">未绑定连接</template>
            </span>
            <span class="shrink-0">{{ formatTime(item.updatedAt) }}</span>
          </span>
        </button>
        <button
          class="icon-btn mt-0.5"
          title="重命名"
          :aria-label="`重命名 ${item.name}`"
          @click="emit('rename', item)"
        >
          <Pencil :size="12" />
        </button>
        <button
          class="icon-btn mt-0.5"
          title="删除"
          :aria-label="`删除 ${item.name}`"
          @click="pendingDelete = item"
        >
          <Trash2 :size="12" />
        </button>
      </div>
      <button
        v-if="next"
        class="btn m-3 w-[calc(100%-1.5rem)]"
        :disabled="loading"
        @click="load(true)"
      >
        加载更多
      </button>
      <p v-if="!items.length && !loading" class="p-4 text-center text-xs text-muted">
        暂无保存的脚本
      </p>
    </div>
    <div v-if="pendingDelete" class="border-t border-line p-3 text-xs">
      <p>删除「{{ pendingDelete.name }}」？此操作不可恢复。</p>
      <div class="mt-2 flex justify-end gap-2">
        <button class="btn px-2 py-1" @click="pendingDelete = null">取消</button>
        <button
          class="btn-danger px-2 py-1"
          @click="
            pendingDelete &&
            deleteScript(pendingDelete.id, pendingDelete.version)
              .then(() => {
                const id = pendingDelete!.id
                pendingDelete = null
                emit('deleted', id)
                return load()
              })
              .catch((e) => emit('notice', safeErrorMessage(e, '删除脚本失败')))
          "
        >
          删除
        </button>
      </div>
    </div>
  </section>
</template>
