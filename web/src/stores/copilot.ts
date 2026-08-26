import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { streamAgent } from '@/api/ai-agent'
import { isAbortError, safeErrorMessage } from '@/api/api-error'
import { applyToolEvent, type CopilotToolCall } from '@/sql-editor/copilot-tools'

export interface CopilotMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  error?: string
  replaceSql?: string
  streaming?: boolean
  tools?: CopilotToolCall[]
}

interface CopilotThread {
  conversationId: string
  messages: CopilotMessage[]
}

export const useCopilotStore = defineStore('copilot', () => {
  const open = ref(false)
  const threads = ref<Record<string, CopilotThread>>({})
  const inflightTabId = ref<string | null>(null)
  let abort: AbortController | undefined

  const inflight = computed(() => inflightTabId.value !== null)

  function threadOf(tabId: string): CopilotThread {
    if (!threads.value[tabId]) {
      threads.value = {
        ...threads.value,
        [tabId]: { conversationId: tabId, messages: [] },
      }
    }
    return threads.value[tabId]!
  }

  function messagesOf(tabId: string | null): CopilotMessage[] {
    if (!tabId) return []
    return threads.value[tabId]?.messages || []
  }

  function replaceThread(tabId: string, thread: CopilotThread): void {
    threads.value = { ...threads.value, [tabId]: thread }
  }

  function patchMessage(
    tabId: string,
    messageId: string,
    patch: (item: CopilotMessage) => CopilotMessage,
  ): void {
    const thread = threads.value[tabId]
    if (!thread) return
    replaceThread(tabId, {
      ...thread,
      messages: thread.messages.map((item) => (item.id === messageId ? patch(item) : item)),
    })
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
    inflightTabId.value = null
  }

  function retain(tabIds: string[]): void {
    const keep = new Set(tabIds)
    const next: Record<string, CopilotThread> = {}
    for (const [id, thread] of Object.entries(threads.value)) {
      if (keep.has(id)) next[id] = thread
    }
    threads.value = next
    if (inflightTabId.value && !keep.has(inflightTabId.value)) cancel()
  }

  async function send(input: {
    tabId: string
    userText: string
    message: string
    datasourceId: string
    database: string
    replaceSql?: string
  }): Promise<void> {
    if (inflightTabId.value) return
    const thread = threadOf(input.tabId)
    const assistantId = crypto.randomUUID()
    const assistant: CopilotMessage = {
      id: assistantId,
      role: 'assistant',
      content: '',
      streaming: true,
      tools: [],
    }
    if (input.replaceSql) assistant.replaceSql = input.replaceSql
    replaceThread(input.tabId, {
      ...thread,
      messages: [
        ...thread.messages,
        {
          id: crypto.randomUUID(),
          role: 'user',
          content: input.userText,
        },
        assistant,
      ],
    })
    inflightTabId.value = input.tabId
    abort = new AbortController()
    try {
      const response = await streamAgent(
        {
          message: input.message,
          conversationId: threads.value[input.tabId]?.conversationId || input.tabId,
          datasourceId: input.datasourceId,
          database: input.database,
        },
        (event) => {
          if ((event.type === 'start' || event.type === 'completed') && event.conversationId) {
            const current = threads.value[input.tabId]
            if (current) replaceThread(input.tabId, { ...current, conversationId: event.conversationId })
          }
          if (event.type === 'delta') {
            patchMessage(input.tabId, assistantId, (item) => ({
              ...item,
              content: item.content + event.content,
            }))
          }
          if (event.type === 'tool') {
            patchMessage(input.tabId, assistantId, (item) => ({
              ...item,
              tools: applyToolEvent(item.tools || [], event.toolName, event.status),
            }))
          }
        },
        abort.signal,
      )
      if (response.conversationId) {
        const current = threads.value[input.tabId]
        if (current) replaceThread(input.tabId, { ...current, conversationId: response.conversationId })
      }
      patchMessage(input.tabId, assistantId, (item) => ({
        ...item,
        content: item.content || response.content || '',
        streaming: false,
      }))
    } catch (error) {
      if (isAbortError(error)) {
        const current = threads.value[input.tabId]
        if (current) {
          replaceThread(input.tabId, {
            ...current,
            messages: current.messages.filter((item) => item.id !== assistantId),
          })
        }
        return
      }
      patchMessage(input.tabId, assistantId, (item) => ({
        ...item,
        error: safeErrorMessage(error, 'Copilot 请求失败'),
        streaming: false,
      }))
    } finally {
      patchMessage(input.tabId, assistantId, (item) => ({ ...item, streaming: false }))
      abort = undefined
      if (inflightTabId.value === input.tabId) inflightTabId.value = null
    }
  }

  return {
    open,
    inflightTabId,
    inflight,
    threadOf,
    messagesOf,
    show,
    hide,
    toggle,
    cancel,
    retain,
    send,
  }
})
