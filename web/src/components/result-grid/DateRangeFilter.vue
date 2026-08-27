<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Calendar } from 'lucide-vue-next'
import { dateFilterInputKind } from './column-type'
import {
  decodeDateRangeDraft,
  encodeDateRangeDraft,
  formatDateRangeLabel,
} from '@/sql-editor/table-data-filter'

const props = defineProps<{
  columnLabel: string
  jdbcType: string
  draft: string
  error?: string
  index: number
}>()
const emit = defineEmits<{
  'update:draft': [value: string]
  apply: []
}>()

const open = ref(false)
const start = ref('')
const end = ref('')
const triggerEl = ref<HTMLElement | null>(null)
const popoverEl = ref<HTMLElement | null>(null)
const popoverStyle = ref({ left: '0px', top: '0px' })

const kind = computed(() => dateFilterInputKind(props.jdbcType) ?? 'date')
const inputType = computed(() => (kind.value === 'time' ? 'time' : 'date'))
const label = computed(() => formatDateRangeLabel(props.draft))
const placeholder = computed(() => (kind.value === 'time' ? '选择时间范围' : '选择日期范围'))

function syncFromDraft(): void {
  const decoded = decodeDateRangeDraft(props.draft, kind.value)
  start.value = decoded.start
  end.value = decoded.end
}

function commit(draft: string): void {
  emit('update:draft', draft)
  emit('apply')
  open.value = false
}
function applyRange(): void {
  commit(encodeDateRangeDraft(start.value, end.value, kind.value))
}
function clearRange(): void {
  start.value = ''
  end.value = ''
  commit('')
}
function setNullish(value: 'NULL' | 'NOT NULL'): void {
  commit(value)
}

function reposition(): void {
  const trigger = triggerEl.value
  const popover = popoverEl.value
  if (!trigger || !popover) return
  const rect = trigger.getBoundingClientRect()
  const width = popover.offsetWidth
  const height = popover.offsetHeight
  let left = rect.left
  let top = rect.bottom + 4
  if (left + width > window.innerWidth - 8) left = Math.max(8, window.innerWidth - width - 8)
  if (top + height > window.innerHeight - 8) top = Math.max(8, rect.top - height - 4)
  popoverStyle.value = { left: `${left}px`, top: `${top}px` }
}

async function toggle(): Promise<void> {
  if (open.value) {
    open.value = false
    return
  }
  syncFromDraft()
  open.value = true
  await nextTick()
  reposition()
  popoverEl.value?.querySelector<HTMLInputElement>('input')?.focus()
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!open.value) return
  const target = event.target
  if (!(target instanceof Node)) return
  if (triggerEl.value?.contains(target) || popoverEl.value?.contains(target)) return
  open.value = false
}
function onKeydown(event: KeyboardEvent): void {
  if (!open.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    open.value = false
  }
}

watch(open, (value) => {
  if (value) {
    window.addEventListener('pointerdown', onDocumentPointerDown, true)
    window.addEventListener('keydown', onKeydown, true)
    window.addEventListener('resize', reposition)
    window.addEventListener('scroll', reposition, true)
    return
  }
  window.removeEventListener('pointerdown', onDocumentPointerDown, true)
  window.removeEventListener('keydown', onKeydown, true)
  window.removeEventListener('resize', reposition)
  window.removeEventListener('scroll', reposition, true)
})
watch(
  () => props.draft,
  () => {
    if (!open.value) return
    syncFromDraft()
  },
)
onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', onDocumentPointerDown, true)
  window.removeEventListener('keydown', onKeydown, true)
  window.removeEventListener('resize', reposition)
  window.removeEventListener('scroll', reposition, true)
})
</script>

<template>
  <div class="result-date-filter">
    <button
      ref="triggerEl"
      class="result-header-filter result-date-filter-trigger"
      :class="{
        'result-header-filter-error': error,
        'result-date-filter-filled': Boolean(label),
      }"
      type="button"
      :aria-label="`筛选 ${columnLabel}`"
      :aria-expanded="open"
      aria-haspopup="dialog"
      :title="error || label || '选择范围后应用到数据库'"
      :data-testid="`result-header-filter-${index}`"
      @pointerdown.stop
      @click.stop="toggle"
    >
      <span class="result-date-filter-label">{{ label || placeholder }}</span>
      <Calendar :size="11" />
    </button>
    <Teleport to="body">
      <div
        v-if="open"
        ref="popoverEl"
        class="result-date-range-popover"
        role="dialog"
        :aria-label="`筛选 ${columnLabel} 的时间范围`"
        :data-testid="`result-date-range-popover-${index}`"
        :style="popoverStyle"
        @pointerdown.stop
      >
        <label class="result-date-range-field">
          <span>开始</span>
          <input
            v-model="start"
            class="result-date-range-input"
            :type="inputType"
            step="1"
            data-testid="result-date-range-start"
            @keydown.enter.prevent="applyRange"
          />
        </label>
        <label class="result-date-range-field">
          <span>结束</span>
          <input
            v-model="end"
            class="result-date-range-input"
            :type="inputType"
            step="1"
            data-testid="result-date-range-end"
            @keydown.enter.prevent="applyRange"
          />
        </label>
        <div class="result-date-range-nulls">
          <button type="button" class="result-date-range-link" @click="setNullish('NULL')">
            为空
          </button>
          <button type="button" class="result-date-range-link" @click="setNullish('NOT NULL')">
            不为空
          </button>
        </div>
        <div class="result-date-range-actions">
          <button type="button" class="btn min-h-7 px-2 py-0.5 text-xs" @click="clearRange">
            清空
          </button>
          <button
            type="button"
            class="btn-primary min-h-7 px-2.5 py-0.5 text-xs"
            data-testid="result-date-range-apply"
            @click="applyRange"
          >
            应用
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>
