import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  deleteConversation,
  getConversation,
  listConversations,
  streamAgent,
  type AgentConversationSummary,
  type AgentRequest,
} from '@/api/ai-agent'
import { isAbortError, isApiError, safeErrorMessage } from '@/api/api-error'
import { applyToolEvent, type CopilotToolCall } from '@/sql-editor/copilot-tools'
import { randomUUID } from '@/uuid'

export interface CopilotMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  error?: string
  replaceSql?: string
  streaming?: boolean
  tools?: CopilotToolCall[]
}

function isConversationGone(error: unknown): boolean {
  return isApiError(error) && error.code === 'CONVERSATION_NOT_FOUND'
}

export const useCopilotStore = defineStore('copilot', () => {
  const open = ref(false)
  const conversationId = ref<string | null>(null)
  const messages = ref<CopilotMessage[]>([])
  const conversations = ref<AgentConversationSummary[]>([])
  const datasourceId = ref<string | null>(null)
  const database = ref<string | null>(null)
  const notice = ref<string | null>(null)
  const inflight = ref(false)
  let abort: AbortController | undefined

  function patchMessage(messageId: string, patch: (item: CopilotMessage) => CopilotMessage): void {
    messages.value = messages.value.map((item) => (item.id === messageId ? patch(item) : item))
  }

  function show(): void {
    open.value = true
  }

  function hide(): void {
    open.value = false
  }

  function toggle(): void {
    open.value = !open.value
  }

  function cancel(): void {
    abort?.abort()
    abort = undefined
    inflight.value = false
  }

  function startNew(): void {
    cancel()
    conversationId.value = null
    messages.value = []
    datasourceId.value = null
    database.value = null
    notice.value = null
  }

  function reset(): void {
    cancel()
    open.value = false
    conversationId.value = null
    messages.value = []
    conversations.value = []
    datasourceId.value = null
    database.value = null
    notice.value = null
  }

  async function loadList(): Promise<void> {
    try {
      const page = await listConversations()
      conversations.value = page.items || []
    } catch {
      /* list is best-effort; the composer still works */
    }
  }

  async function openConversation(id: string): Promise<void> {
    cancel()
    notice.value = null
    try {
      const detail = await getConversation(id)
      conversationId.value = detail.id
      datasourceId.value = detail.datasourceId || null
      database.value = detail.database || null
      messages.value = detail.messages.map((item) => ({
        id: item.id,
        role: item.role,
        content: item.content,
        ...(item.tools ? { tools: item.tools } : {}),
      }))
    } catch (error) {
      if (isConversationGone(error)) {
        startNew()
        notice.value = '该对话已不存在，已开始新对话'
        await loadList()
        return
      }
      notice.value = safeErrorMessage(error, '无法打开对话')
    }
  }

  async function removeConversation(id: string): Promise<void> {
    try {
      await deleteConversation(id)
    } catch (error) {
      if (!isConversationGone(error)) {
        notice.value = safeErrorMessage(error, '删除对话失败')
        return
      }
    }
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (conversationId.value === id) startNew()
  }

  async function streamTurn(input: {
    userText: string
    message: string
    datasourceId: string
    database: string
    assistantId: string
  }): Promise<void> {
    const body: AgentRequest = {
      message: input.message,
      userText: input.userText,
      datasourceId: input.datasourceId,
      database: input.database,
    }
    if (conversationId.value) body.conversationId = conversationId.value
    abort = new AbortController()
    const response = await streamAgent(
      body,
      (event) => {
        if ((event.type === 'start' || event.type === 'completed') && event.conversationId) {
          conversationId.value = event.conversationId
        }
        if (event.type === 'delta') {
          patchMessage(input.assistantId, (item) => ({
            ...item,
            content: item.content + event.content,
          }))
        }
        if (event.type === 'tool') {
          patchMessage(input.assistantId, (item) => ({
            ...item,
            tools: applyToolEvent(item.tools || [], event.toolName, event.status),
          }))
        }
      },
      abort.signal,
    )
    if (response.conversationId) conversationId.value = response.conversationId
    patchMessage(input.assistantId, (item) => ({
      ...item,
      content: item.content || response.content || '',
      streaming: false,
    }))
  }

  async function send(input: {
    userText: string
    message: string
    datasourceId: string
    database: string
    replaceSql?: string
  }): Promise<void> {
    if (inflight.value) return
    notice.value = null
    const assistantId = randomUUID()
    const assistant: CopilotMessage = {
      id: assistantId,
      role: 'assistant',
      content: '',
      streaming: true,
      tools: [],
    }
    if (input.replaceSql) assistant.replaceSql = input.replaceSql
    messages.value = [
      ...messages.value,
      { id: randomUUID(), role: 'user', content: input.userText },
      assistant,
    ]
    inflight.value = true
    try {
      try {
        await streamTurn({ ...input, assistantId })
      } catch (error) {
        if (isConversationGone(error) && conversationId.value) {
          conversationId.value = null
          notice.value = '该对话已不存在，已开始新对话'
          await streamTurn({ ...input, assistantId })
        } else {
          throw error
        }
      }
      datasourceId.value = input.datasourceId
      database.value = input.database
      await loadList()
    } catch (error) {
      if (isAbortError(error)) {
        messages.value = messages.value.filter((item) => item.id !== assistantId)
        return
      }
      patchMessage(assistantId, (item) => ({
        ...item,
        error: safeErrorMessage(error, 'Copilot 请求失败'),
        streaming: false,
      }))
    } finally {
      patchMessage(assistantId, (item) => ({ ...item, streaming: false }))
      abort = undefined
      inflight.value = false
    }
  }

  return {
    open,
    conversationId,
    messages,
    conversations,
    datasourceId,
    database,
    notice,
    inflight,
    show,
    hide,
    toggle,
    cancel,
    startNew,
    reset,
    loadList,
    openConversation,
    removeConversation,
    send,
  }
})
