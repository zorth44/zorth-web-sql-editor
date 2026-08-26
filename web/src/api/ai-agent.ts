import { appEnv } from '@/env'
import { bearerFetch } from '@/api/sql-client'
import { ApiError, isApiError } from '@/api/api-error'
import { consumeSse } from '@/sql-editor/sse'

export interface AgentRequest {
  message: string
  conversationId?: string
  datasourceId: string
  database: string
}

export interface AgentResponse {
  content: string
  conversationId?: string
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
    if (isApiError(error) && error.status === 404) {
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
      throw new ApiError(500, {
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
