import { appEnv } from '@/env'
import { bearerFetch } from '@/api/sql-client'
import { ApiError, isApiError } from '@/api/api-error'
import { consumeSse } from '@/sql-editor/sse'

export interface AgentRequest {
  message: string
  userText?: string
  conversationId?: string
  datasourceId: string
  database: string
}

export interface AgentResponse {
  content: string
  conversationId?: string
}

export interface AgentConversationSummary {
  id: string
  title: string
  datasourceId?: string | null
  database?: string | null
  updatedAt: string
}

export interface AgentConversationMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  tools?: { id: string; toolName: string; status: 'STARTED' | 'SUCCESS' | 'FAILURE' }[]
  createdAt: string
}

export interface AgentConversationDetail extends AgentConversationSummary {
  messages: AgentConversationMessage[]
}

export interface AgentConversationList {
  items: AgentConversationSummary[]
}

export type AgentStreamEvent =
  | { type: 'start'; conversationId?: string }
  | { type: 'delta'; content: string }
  | { type: 'tool'; toolName: string; status: 'STARTED' | 'SUCCESS' | 'FAILURE' }
  | { type: 'completed'; conversationId?: string }
  | { type: 'error'; code?: string; message?: string }

export async function runAgent(body: AgentRequest, signal?: AbortSignal): Promise<AgentResponse> {
  const response = await bearerFetch(`${appEnv.aiApiBase}/api/v1/ai/agent`, {
    method: 'POST',
    body: JSON.stringify(body),
    ...(signal ? { signal } : {}),
  })
  if (response.status === 204) return { content: '' }
  return (await response.json()) as AgentResponse
}

export async function streamAgent(
  body: AgentRequest,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
): Promise<AgentResponse> {
  try {
    return await readAgentStream(body, onEvent, signal)
  } catch (error) {
    if (isApiError(error) && error.status === 404 && error.code !== 'CONVERSATION_NOT_FOUND') {
      const response = await runAgent(body, signal)
      if (response.conversationId) onEvent({ type: 'start', conversationId: response.conversationId })
      if (response.content) onEvent({ type: 'delta', content: response.content })
      onEvent(
        response.conversationId
          ? { type: 'completed', conversationId: response.conversationId }
          : { type: 'completed' },
      )
      return response
    }
    throw error
  }
}

async function readAgentStream(
  body: AgentRequest,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
): Promise<AgentResponse> {
  const response = await bearerFetch(`${appEnv.aiApiBase}/api/v1/ai/agent/stream`, {
    method: 'POST',
    headers: { Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    ...(signal ? { signal } : {}),
  })
  if (!response.body) {
    throw new ApiError(0, {
      requestId: response.headers.get('X-Request-Id') || crypto.randomUUID(),
      code: 'AI_SERVICE_ERROR',
      message: 'Copilot 流式响应为空',
    })
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let content = ''
  let conversationId = body.conversationId

  const applyFrame = (raw: string, eventName: string): void => {
    const event = parseAgentEvent(raw, eventName)
    if (!event) return
    if (event.type === 'error') {
      throw new ApiError(event.code === 'CONVERSATION_NOT_FOUND' ? 404 : 500, {
        requestId: response.headers.get('X-Request-Id') || crypto.randomUUID(),
        code: event.code || 'AI_SERVICE_ERROR',
        message: event.message || 'Copilot 请求失败',
      })
    }
    if (event.type === 'delta') content += event.content
    if ((event.type === 'start' || event.type === 'completed') && event.conversationId) {
      conversationId = event.conversationId
    }
    onEvent(event)
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const consumed = consumeSse(buffer)
    buffer = consumed.rest
    for (const frame of consumed.frames) {
      applyFrame(frame.data, frame.event)
      await new Promise<void>((resolve) => {
        setTimeout(resolve, 0)
      })
    }
  }
  buffer += decoder.decode()
  const flushed = consumeSse(buffer.endsWith('\n\n') ? buffer : `${buffer}\n\n`)
  for (const frame of flushed.frames) applyFrame(frame.data, frame.event)
  return conversationId ? { content, conversationId } : { content }
}

function parseAgentEvent(raw: string, eventName: string): AgentStreamEvent | null {
  if (!raw.trim()) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(raw) as unknown
  } catch {
    if (eventName === 'delta') return { type: 'delta', content: raw }
    return null
  }
  if (!parsed || typeof parsed !== 'object') return null
  const data = parsed as Record<string, unknown>
  const type = typeof data.type === 'string' ? data.type : eventName
  if (type === 'start') {
    return data.conversationId && typeof data.conversationId === 'string'
      ? { type: 'start', conversationId: data.conversationId }
      : { type: 'start' }
  }
  if (type === 'delta') return { type: 'delta', content: typeof data.content === 'string' ? data.content : '' }
  if (type === 'tool') {
    const status = data.status
    if (status !== 'STARTED' && status !== 'SUCCESS' && status !== 'FAILURE') return null
    if (typeof data.toolName !== 'string' || !data.toolName) return null
    return { type: 'tool', toolName: data.toolName, status }
  }
  if (type === 'completed') {
    return data.conversationId && typeof data.conversationId === 'string'
      ? { type: 'completed', conversationId: data.conversationId }
      : { type: 'completed' }
  }
  if (type === 'error') {
    const event: { type: 'error'; code?: string; message?: string } = { type: 'error' }
    if (typeof data.code === 'string' && data.code) event.code = data.code
    if (typeof data.message === 'string' && data.message) event.message = data.message
    return event
  }
  return null
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null
}

function asTime(value: unknown): string {
  if (typeof value === 'string') return value
  if (typeof value === 'number' && Number.isFinite(value)) return new Date(value).toISOString()
  return ''
}

function parseConversationSummaries(raw: unknown): AgentConversationSummary[] {
  const wrapped = asRecord(raw)?.items
  const rows = Array.isArray(raw) ? raw : Array.isArray(wrapped) ? wrapped : []
  return rows.flatMap((row) => {
    const item = parseConversationSummary(row)
    return item ? [item] : []
  })
}

function parseConversationSummary(raw: unknown): AgentConversationSummary | null {
  const data = asRecord(raw)
  if (!data || typeof data.id !== 'string' || !data.id) return null
  if (typeof data.title !== 'string') return null
  const item: AgentConversationSummary = {
    id: data.id,
    title: data.title,
    updatedAt: asTime(data.updatedAt),
  }
  if (typeof data.datasourceId === 'string') item.datasourceId = data.datasourceId
  else if (data.datasourceId === null) item.datasourceId = null
  if (typeof data.database === 'string') item.database = data.database
  else if (data.database === null) item.database = null
  return item
}

function parseConversationDetail(raw: unknown): AgentConversationDetail | null {
  const summary = parseConversationSummary(raw)
  const data = asRecord(raw)
  if (!summary || !data) return null
  const messages = Array.isArray(data.messages)
    ? data.messages.flatMap((row) => {
        const message = parseConversationMessage(row)
        return message ? [message] : []
      })
    : []
  return { ...summary, messages }
}

function parseConversationMessage(raw: unknown): AgentConversationMessage | null {
  const data = asRecord(raw)
  if (!data || typeof data.id !== 'string' || !data.id) return null
  if (data.role !== 'user' && data.role !== 'assistant') return null
  if (typeof data.content !== 'string') return null
  const tools = parseToolSummaries(data.tools)
  return {
    id: data.id,
    role: data.role,
    content: data.content,
    createdAt: asTime(data.createdAt),
    ...(tools ? { tools } : {}),
  }
}

function parseToolSummaries(
  raw: unknown,
): AgentConversationMessage['tools'] {
  if (!Array.isArray(raw)) return undefined
  const tools = raw.flatMap((row) => {
    const data = asRecord(row)
    if (!data) return []
    const toolName =
      typeof data.toolName === 'string' && data.toolName
        ? data.toolName
        : typeof data.name === 'string' && data.name
          ? data.name
          : ''
    const status = data.status
    if (!toolName || (status !== 'STARTED' && status !== 'SUCCESS' && status !== 'FAILURE')) {
      return []
    }
    const tool: NonNullable<AgentConversationMessage['tools']>[number] = {
      id: typeof data.id === 'string' && data.id ? data.id : crypto.randomUUID(),
      toolName,
      status,
    }
    return [tool]
  })
  return tools.length ? tools : undefined
}

export async function listConversations(): Promise<AgentConversationList> {
  const response = await bearerFetch(`${appEnv.aiApiBase}/api/v1/ai/agent/conversations`)
  if (response.status === 204) return { items: [] }
  return { items: parseConversationSummaries(await response.json()) }
}

export async function getConversation(id: string): Promise<AgentConversationDetail> {
  const response = await bearerFetch(
    `${appEnv.aiApiBase}/api/v1/ai/agent/conversations/${encodeURIComponent(id)}`,
  )
  const detail = parseConversationDetail(await response.json())
  if (!detail) {
    throw new ApiError(500, {
      requestId: response.headers.get('X-Request-Id') || crypto.randomUUID(),
      code: 'AI_SERVICE_ERROR',
      message: '对话详情格式无效',
    })
  }
  return detail
}

export async function deleteConversation(id: string): Promise<void> {
  await bearerFetch(`${appEnv.aiApiBase}/api/v1/ai/agent/conversations/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}
