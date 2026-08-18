<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CircleAlert, CircleCheck, CircleMinus, ListChecks } from 'lucide-vue-next'
import ResultGrid from './ResultGrid.vue'
import { DEFAULT_ROW_LIMIT } from './limits'
import { statementKeyword } from '@/sql-editor/sql'
import type { StatementRun } from '@/stores/editor'
import type { SqlExecutionResult } from '@/types/contracts'

const props = defineProps<{
  statements: StatementRun[]
  result: SqlExecutionResult | null
  error: string | null
  resultIndex?: number
  runningIndex?: number | null
  running?: boolean
  canExport?: boolean
  exporting?: boolean
  rowLimit?: number
}>()
const emit = defineEmits<{
  select: [index: number]
  export: []
  'cancel-export': []
  'update:rowLimit': [value: number]
}>()

/** A script opens on the summary so progress and failures are visible at once. */
const showSummary = ref(props.statements.length > 1)
/** A single statement keeps the plain grid so nothing changes for one-off runs. */
const isScript = computed(() => props.statements.length > 1)
const progress = computed(() =>
  props.running && props.runningIndex !== null && props.runningIndex !== undefined
    ? `第 ${props.runningIndex + 1} / ${props.statements.length} 条`
    : '',
)
const failed = computed(() => props.statements.find((item) => item.status === 'FAILED') || null)
const succeeded = computed(
  () => props.statements.filter((item) => item.status === 'SUCCESS').length,
)

function statusLabel(status: StatementRun['status']): string {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '执行中'
  if (status === 'SKIPPED') return '未执行'
  return '等待中'
}
function outcome(statement: StatementRun): string {
  const result = statement.result
  if (!result) return statement.error ? statement.error : '—'
  if (result.kind === 'RESULT_SET') {
    return `${result.rowCount.toLocaleString()} 行${result.truncated ? '（已截断）' : ''}`
  }
  return result.affectedRows === null
    ? '执行成功'
    : `影响 ${result.affectedRows.toLocaleString()} 行`
}
function duration(statement: StatementRun): string {
  return statement.result ? `${statement.result.durationMs} ms` : '—'
}
/** Only statements that reached a terminal outcome have something to show. */
function hasOutcome(statement: StatementRun): boolean {
  return statement.status === 'SUCCESS' || statement.status === 'FAILED'
}
function select(index: number): void {
  const statement = props.statements[index]
  if (!statement || !hasOutcome(statement)) return
  showSummary.value = false
  emit('select', index)
}

watch(
  () => props.statements,
  (statements) => {
    showSummary.value = statements.length > 1
  },
)
</script>
<template>
  <section v-if="!isScript" class="flex h-full min-h-0 flex-col">
    <ResultGrid
      :result="result"
      :error="error"
      :running="Boolean(running)"
      :can-export="Boolean(canExport)"
      :exporting="Boolean(exporting)"
      :row-limit="rowLimit ?? DEFAULT_ROW_LIMIT"
      @export="emit('export')"
      @cancel-export="emit('cancel-export')"
      @update:row-limit="emit('update:rowLimit', $event)"
    >
      <template v-if="$slots.status" #status><slot name="status" /></template>
    </ResultGrid>
  </section>
  <section v-else class="flex h-full min-h-0 flex-col bg-panel">
    <div class="script-tabs" role="tablist" aria-label="脚本结果">
      <button
        class="script-tab"
        :class="{ 'script-tab-active': showSummary }"
        role="tab"
        :aria-selected="showSummary"
        data-testid="script-summary-tab"
        @click="showSummary = true"
      >
        <ListChecks :size="13" />汇总
      </button>
      <button
        v-for="(statement, index) in statements"
        :key="statement.position"
        class="script-tab"
        :class="{ 'script-tab-active': !showSummary && index === resultIndex }"
        role="tab"
        :aria-selected="!showSummary && index === resultIndex"
        :disabled="!hasOutcome(statement)"
        :title="statement.sql"
        :aria-label="`第 ${statement.position} 条语句，${statusLabel(statement.status)}`"
        @click="select(index)"
      >
        <CircleCheck v-if="statement.status === 'SUCCESS'" :size="12" class="text-success" />
        <CircleAlert v-else-if="statement.status === 'FAILED'" :size="12" class="text-danger" />
        <CircleMinus v-else :size="12" class="text-muted" />
        Result {{ statement.position }}
      </button>
      <span v-if="progress" class="script-progress" data-testid="script-progress">
        {{ progress }}
      </span>
    </div>
    <div v-if="showSummary" class="script-summary" data-testid="script-summary">
      <p class="script-summary-head">
        共 {{ statements.length }} 条语句，成功 {{ succeeded }} 条<template v-if="failed"
          >，第 {{ failed.position }} 条失败后已停止</template
        >
      </p>
      <p v-if="failed" class="script-summary-note">
        已执行的语句不会回滚，请自行确认数据状态后再重新运行。
      </p>
      <ol class="script-summary-list">
        <li
          v-for="(statement, index) in statements"
          :key="statement.position"
          class="script-summary-row"
          :class="{ 'script-summary-row-failed': statement.status === 'FAILED' }"
        >
          <button class="script-summary-open" @click="select(index)">
            <span class="script-summary-position">{{ statement.position }}</span>
            <span class="script-summary-kind">{{ statementKeyword(statement.sql) }}</span>
            <span class="script-summary-status">{{ statusLabel(statement.status) }}</span>
            <span class="script-summary-duration">{{ duration(statement) }}</span>
            <span class="script-summary-outcome">{{ outcome(statement) }}</span>
          </button>
        </li>
      </ol>
    </div>
    <ResultGrid
      v-else
      :result="result"
      :error="error"
      :running="Boolean(running) && runningIndex === resultIndex"
      :can-export="Boolean(canExport)"
      :exporting="Boolean(exporting)"
      :row-limit="rowLimit ?? DEFAULT_ROW_LIMIT"
      @export="emit('export')"
      @cancel-export="emit('cancel-export')"
      @update:row-limit="emit('update:rowLimit', $event)"
    >
      <template v-if="$slots.status" #status><slot name="status" /></template>
    </ResultGrid>
  </section>
</template>
