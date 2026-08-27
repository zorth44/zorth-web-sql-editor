<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { History, Plus, Sparkles, Square, Trash2, X } from 'lucide-vue-next'
import { renderAssistantMarkdown } from '@/sql-editor/copilot-markdown'
import { toolStatusText } from '@/sql-editor/copilot-tools'
import { splitAssistantContent } from '@/sql-editor/sql-fences'
import type { AgentConversationSummary } from '@/api/ai-agent'
import type { CopilotMessage } from '@/stores/copilot'
import type { DataSourceListItem } from '@/types/contracts'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const props = withDefaults(
  defineProps<{
    available: boolean
    disabledReason: string
    dataSourceName: string
    dataSourceId?: string
    database: string
    dialect: string
    sources?: Pick<DataSourceListItem, 'id' | 'name'>[]
    messages: CopilotMessage[]
    conversations?: AgentConversationSummary[]
    conversationId?: string | null
    conversationDatasourceId?: string | null
    conversationDatabase?: string | null
    notice?: string | null
    inflight: boolean
    canInsertAndRun: boolean
  }>(),
  {
    dataSourceId: '',
    sources: () => [],
    conversations: () => [],
    conversationId: null,
    conversationDatasourceId: null,
    conversationDatabase: null,
    notice: null,
  },
)
const emit = defineEmits<{
  send: [text: string]
  cancel: []
  close: []
  insert: [sql: string, replaceSql?: string]
  'insert-and-run': [sql: string, replaceSql?: string]
  'new-conversation': []
  'open-conversation': [id: string]
  'delete-conversation': [id: string]
}>()

const draft = ref('')
const composer = ref<HTMLTextAreaElement | null>(null)
const scroller = ref<HTMLElement | null>(null)
const view = ref<'chat' | 'history'>('chat')
const pendingDeleteId = ref<string | null>(null)

const contextLabel = computed(() => {
  if (props.dataSourceName && props.database) return `${props.dataSourceName} / ${props.database}`
  if (props.dataSourceName) return props.dataSourceName
  return '未选择连接'
})

const connectionHint = computed(() => {
  if (!props.conversationId) return ''
  const sameSource =
    !props.conversationDatasourceId || props.conversationDatasourceId === props.dataSourceId
  const sameDb = !props.conversationDatabase || props.conversationDatabase === props.database
  if (sameSource && sameDb) return ''
  if (props.dataSourceName && props.database) {
    return `当前页签已换成 ${props.dataSourceName} / ${props.database}，下一条会按这个连接提问`
  }
  return '当前页签连接已切换，下一条会按新连接提问'
})

function formatTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

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

function confirmDelete(): void {
  const id = pendingDeleteId.value
  pendingDeleteId.value = null
  if (id) emit('delete-conversation', id)
}

function requestDelete(id: string): void {
  pendingDeleteId.value = id
}

function showChat(): void {
  view.value = 'chat'
}

function toggleHistory(): void {
  view.value = view.value === 'history' ? 'chat' : 'history'
}

function startNew(): void {
  showChat()
  emit('new-conversation')
}

function openItem(id: string): void {
  showChat()
  emit('open-conversation', id)
}

function sourceName(id: string | null | undefined): string {
  if (!id) return ''
  return props.sources.find((item) => item.id === id)?.name || ''
}

function connectionLabel(item: AgentConversationSummary): string {
  const name = sourceName(item.datasourceId)
  const database = item.database || ''
  if (name && database) return `${name} / ${database}`
  if (name) return name
  if (item.datasourceId && database) return `${item.datasourceId} / ${database}`
  return item.datasourceId || database
}

watch(
  () =>
    props.messages.map(
      (message) => `${message.content}:${message.tools?.length}:${message.streaming}`,
    ),
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
        class="copilot-head-btn ml-auto"
        type="button"
        title="新对话"
        data-testid="copilot-new"
        :disabled="inflight"
        @click="startNew()"
      >
        <Plus :size="12" />新对话
      </button>
      <button
        class="copilot-head-btn"
        type="button"
        title="历史对话"
        data-testid="copilot-history-toggle"
        :class="{ 'activity-btn-active': view === 'history' }"
        :aria-pressed="view === 'history'"
        @click="toggleHistory()"
      >
        <History :size="12" />历史
      </button>
      <button
        class="icon-btn"
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
    <p v-if="notice" class="copilot-notice" data-testid="copilot-notice">{{ notice }}</p>
    <p v-if="connectionHint" class="copilot-hint-bar" data-testid="copilot-connection-hint">
      {{ connectionHint }}
    </p>
    <div v-if="view === 'history'" class="copilot-history" data-testid="copilot-history">
      <ul v-if="conversations.length" class="copilot-history-list">
        <li v-for="item in conversations" :key="item.id" class="copilot-history-item">
          <button
            class="copilot-history-open"
            type="button"
            :class="{ 'copilot-history-current': item.id === conversationId }"
            :data-testid="`copilot-history-item-${item.id}`"
            @click="openItem(item.id)"
          >
            <span class="copilot-history-title">{{ item.title || '未命名对话' }}</span>
            <span v-if="connectionLabel(item)" class="copilot-history-meta">{{
              connectionLabel(item)
            }}</span>
            <span class="copilot-history-time">
              <template v-if="item.datasourceId">{{ item.datasourceId }} · </template
              >{{ formatTime(item.updatedAt) }}
            </span>
          </button>
          <button
            class="icon-btn"
            type="button"
            title="删除对话"
            :aria-label="`删除 ${item.title || '对话'}`"
            :data-testid="`copilot-history-delete-${item.id}`"
            :disabled="inflight"
            @click="requestDelete(item.id)"
          >
            <Trash2 :size="12" />
          </button>
        </li>
      </ul>
      <p v-else class="copilot-history-empty" data-testid="copilot-history-empty">
        还没有历史对话。发送成功后会出现在这里。
      </p>
    </div>
    <div
      v-if="view === 'chat'"
      ref="scroller"
      class="copilot-messages"
      data-testid="copilot-messages"
    >
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
            <div
              v-if="segment.type === 'text'"
              class="copilot-md"
              data-testid="copilot-md"
              v-html="renderAssistantMarkdown(segment.text)"
            />
            <!-- eslint-disable-line vue/no-v-html -- sanitized by renderAssistantMarkdown -->
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
    <form v-if="view === 'chat'" class="copilot-composer" @submit.prevent="submit">
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
    <ConfirmDialog
      :open="Boolean(pendingDeleteId)"
      title="删除对话"
      confirm-label="删除"
      @close="pendingDeleteId = null"
      @confirm="confirmDelete"
    >
      删除后无法恢复这条 Copilot 对话。
    </ConfirmDialog>
  </aside>
</template>
