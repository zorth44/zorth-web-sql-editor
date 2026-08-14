<script setup lang="ts">
import { computed, ref } from 'vue'
import { Copy } from 'lucide-vue-next'
import type { BinaryValue, SqlExecutionResult } from '@/types/contracts'
const props = defineProps<{ result: SqlExecutionResult | null; error: string | null }>()
const filter = ref('')
const scroll = ref(0)
const rowHeight = 36
const viewport = 360
const rows = computed(() => (props.result?.kind === 'RESULT_SET' ? props.result.rows : []))
const filtered = computed(() => {
  const q = filter.value.toLowerCase()
  return q
    ? rows.value.filter((row) => row.some((cell) => display(cell).toLowerCase().includes(q)))
    : rows.value
})
const start = computed(() => Math.max(0, Math.floor(scroll.value / rowHeight) - 5))
const end = computed(() =>
  Math.min(filtered.value.length, start.value + Math.ceil(viewport / rowHeight) + 10),
)
const visible = computed(() => filtered.value.slice(start.value, end.value))
function binary(value: unknown): value is BinaryValue {
  return Boolean(value && typeof value === 'object' && (value as BinaryValue).binary === true)
}
function display(value: unknown): string {
  if (value === null) return 'NULL'
  if (binary(value)) return `BINARY · ${value.size} bytes`
  return String(value)
}
async function copy(text: string) {
  await navigator.clipboard.writeText(text)
}
async function copyResult() {
  if (props.result?.kind !== 'RESULT_SET') return
  const lines = [
    props.result.columns.map((c) => c.label).join('\t'),
    ...props.result.rows.map((r) => r.map(display).join('\t')),
  ]
  await copy(lines.join('\n'))
}
</script>
<template>
  <section class="flex h-full min-h-0 flex-col bg-white">
    <div class="flex h-10 items-center gap-3 border-b border-line px-3">
      <strong class="text-sm">结果 / 消息</strong
      ><template v-if="result?.kind === 'RESULT_SET'"
        ><span class="text-xs text-muted"
          >{{ result.rowCount }} 行 · {{ result.durationMs }} ms<span v-if="result.truncated">
            · 已截断</span
          ></span
        ><input
          v-model="filter"
          class="field ml-auto w-52 py-1 text-xs"
          placeholder="过滤已返回结果" /><button
          class="icon-btn"
          title="复制结果"
          @click="copyResult"
        >
          <Copy :size="14" /></button
      ></template>
    </div>
    <div v-if="error" class="m-4 rounded bg-red-50 p-4 text-sm text-danger" role="alert">
      {{ error }}
    </div>
    <div v-else-if="!result" class="grid flex-1 place-items-center text-sm text-muted">
      运行 SQL 后在这里查看结果
    </div>
    <div
      v-else-if="result.kind !== 'RESULT_SET'"
      class="m-4 rounded-lg border border-line bg-slate-50 p-5"
    >
      <p class="font-medium">{{ result.message }}</p>
      <p class="mt-2 text-sm text-muted">
        <span v-if="result.affectedRows !== null">影响 {{ result.affectedRows }} 行 · </span
        >{{ result.durationMs }} ms
      </p>
    </div>
    <div
      v-else
      class="min-h-0 flex-1 overflow-auto"
      @scroll.passive="scroll = ($event.target as HTMLElement).scrollTop"
    >
      <div class="sticky top-0 z-10 flex min-w-max bg-slate-100 text-xs font-semibold">
        <div
          v-for="column in result.columns"
          :key="column.label"
          class="w-48 shrink-0 border-b border-r border-line px-3 py-2"
          :title="`${column.jdbcType} / ${column.typeName}`"
        >
          {{ column.label }}
        </div>
      </div>
      <div :style="{ height: `${filtered.length * rowHeight}px` }" class="relative min-w-max">
        <div
          :style="{ transform: `translateY(${start * rowHeight}px)` }"
          class="absolute left-0 top-0"
        >
          <div v-for="(row, index) in visible" :key="start + index" class="flex h-9">
            <button
              v-for="(cell, col) in row"
              :key="col"
              class="w-48 shrink-0 truncate border-b border-r border-line px-3 text-left text-xs"
              :class="{ 'italic text-muted': cell === null }"
              :title="display(cell)"
              @click="copy(display(cell))"
            >
              {{ display(cell) }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
