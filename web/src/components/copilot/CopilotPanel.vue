<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Sparkles, Square, X } from 'lucide-vue-next'
import { renderAssistantMarkdown } from '@/sql-editor/copilot-markdown'
import { toolStatusText } from '@/sql-editor/copilot-tools'
import { splitAssistantContent } from '@/sql-editor/sql-fences'
import type { CopilotMessage } from '@/stores/copilot'

const props = defineProps<{
  available: boolean
  disabledReason: string
  dataSourceName: string
  database: string
  dialect: string
  messages: CopilotMessage[]
  inflight: boolean
  canInsertAndRun: boolean
}>()
const emit = defineEmits<{
  send: [text: string]
  cancel: []
  close: []
  insert: [sql: string, replaceSql?: string]
  'insert-and-run': [sql: string, replaceSql?: string]
}>()

const draft = ref('')
const composer = ref<HTMLTextAreaElement | null>(null)
const scroller = ref<HTMLElement | null>(null)

const contextLabel = computed(() => {
  if (props.dataSourceName && props.database) return `${props.dataSourceName} / ${props.database}`
  if (props.dataSourceName) return props.dataSourceName
  return '未选择连接'
})

function segmentsOf(message: CopilotMessage) {
  return splitAssistantContent(message.content)
}

function hasSql(message: CopilotMessage): boolean {
  return segmentsOf(message).some((segment) => segment.type === 'sql')
}

function composingKey(event: KeyboardEvent): boolean {
  return event.isComposing || event.key === 'Process' || event.keyCode === 229
}

function clearDraft(): void {
  draft.value = ''
  if (composer.value) composer.value.value = ''
}

function scrollMessages(): void {
  const el = scroller.value
  if (el && typeof el.scrollTo === 'function') {
    el.scrollTo({ top: el.scrollHeight })
  }
}

async function submit(): Promise<void> {
  const text = draft.value.trim()
  if (!text || !props.available || props.inflight) return
  clearDraft()
  emit('send', text)
  await nextTick()
  // IME compositionend / v-model input can restore the value after keydown.
  clearDraft()
  scrollMessages()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || composingKey(event)) return
  event.preventDefault()
  void submit()
}

function onCompositionEnd(): void {
  if (props.inflight) clearDraft()
}

watch(
  () => props.messages.map((message) => `${message.content}:${message.tools?.length}:${message.streaming}`),
  async () => {
    await nextTick()
    scrollMessages()
  },
)
</script>
<template>
  <aside class="copilot-panel" data-testid="copilot-panel" aria-label="Copilot">
    <header class="copilot-head">
      <Sparkles :size="14" class="text-brand" />
      <strong>Copilot</strong>
      <button
        class="icon-btn ml-auto"
        type="button"
        title="关闭"
        aria-label="关闭 Copilot"
        @click="emit('close')"
      >
        <X :size="14" />
      </button>
    </header>
    <p
      class="copilot-context"
      data-testid="copilot-context"
      :title="`${contextLabel} · ${dialect}`"
    >
      {{ contextLabel }}
      <span v-if="dialect" class="copilot-context-sep">·</span>
      {{ dialect }}
    </p>
    <div ref="scroller" class="copilot-messages" data-testid="copilot-messages">
      <p v-if="!messages.length && !inflight" class="copilot-empty">
        用自然语言描述你想写的 SQL。回复里的代码块可以插入当前页签。
      </p>
      <div
        v-for="message in messages"
        :key="message.id"
        class="copilot-bubble"
        :class="message.role === 'user' ? 'copilot-bubble-user' : 'copilot-bubble-assistant'"
      >
        <p v-if="message.role === 'user'" class="copilot-text">{{ message.content }}</p>
        <template v-else>
          <ol v-if="message.tools?.length" class="copilot-tools" data-testid="copilot-tools">
            <li
              v-for="tool in message.tools"
              :key="tool.id"
              class="copilot-tool"
              :data-status="tool.status"
            >
              {{ toolStatusText(tool) }}
            </li>
          </ol>
          <p v-if="message.error" class="copilot-error">{{ message.error }}</p>
          <template v-for="(segment, index) in segmentsOf(message)" :key="index">
            <!-- eslint-disable-next-line vue/no-v-html -- sanitized by renderAssistantMarkdown -->
            <div
              v-if="segment.type === 'text'"
              class="copilot-md"
              data-testid="copilot-md"
              v-html="renderAssistantMarkdown(segment.text)"
            />
            <div v-else class="copilot-sql" data-testid="copilot-sql">
              <pre>{{ segment.sql }}</pre>
              <div v-if="!message.streaming" class="copilot-sql-actions">
                <button
                  class="btn-primary min-h-7 px-2.5 py-0.5 text-xs"
                  type="button"
                  data-testid="copilot-insert"
                  @click="emit('insert', segment.sql, message.replaceSql)"
                >
                  插入
                </button>
                <button
                  class="btn min-h-7 px-2.5 py-0.5 text-xs"
                  type="button"
                  :disabled="!canInsertAndRun"
                  data-testid="copilot-insert-and-run"
                  @click="emit('insert-and-run', segment.sql, message.replaceSql)"
                >
                  插入并运行
                </button>
              </div>
            </div>
          </template>
          <span v-if="message.streaming" class="copilot-caret" data-testid="copilot-caret" />
          <p
            v-if="!message.streaming && !message.error && !hasSql(message) && message.content"
            class="copilot-hint"
          >
            请让我用代码块给出 SQL。
          </p>
        </template>
      </div>
      <p
        v-if="inflight && !messages.some((item) => item.streaming)"
        class="copilot-loading"
        data-testid="copilot-loading"
      >
        正在生成 SQL…
      </p>
    </div>
    <form class="copilot-composer" @submit.prevent="submit">
      <textarea
        ref="composer"
        v-model="draft"
        class="copilot-input"
        data-testid="copilot-input"
        rows="3"
        :disabled="!available"
        :readonly="inflight"
        :placeholder="available ? '描述你想查询或修改的数据…' : disabledReason"
        :aria-label="available ? 'Copilot 输入' : disabledReason"
        @keydown="onKeydown"
        @compositionend="onCompositionEnd"
      />
      <div class="copilot-composer-actions">
        <button
          v-if="inflight"
          class="btn min-h-8 px-3 py-1 text-xs text-danger"
          type="button"
          data-testid="copilot-cancel"
          @click="emit('cancel')"
        >
          <Square :size="12" />取消
        </button>
        <button
          v-else
          class="btn-primary min-h-8 px-3 py-1 text-xs"
          type="submit"
          data-testid="copilot-send"
          :disabled="!available || !draft.trim()"
        >
          发送
        </button>
      </div>
    </form>
  </aside>
</template>
