import '@testing-library/jest-dom/vitest'
import { Blob as NodeBlob } from 'node:buffer'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '@/mocks/server'
import { resetMockData } from '@/mocks/fixtures'
import { resetAgentConversations, resetMockHistory, resetMockScripts } from '@/mocks/handlers'
import { resetOptimizationMock } from '@/mocks/sql-optimization-handlers'

// jsdom 26's Blob lacks text(), arrayBuffer(), and stream(). Use Node's
// implementation alongside Node's fetch/Response for binary API tests.
Object.defineProperty(globalThis, 'Blob', { configurable: true, writable: true, value: NodeBlob })

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  resetMockData()
  resetAgentConversations()
  resetMockHistory()
  resetMockScripts()
  resetOptimizationMock()
  localStorage.clear()
  sessionStorage.clear()
  document.documentElement.classList.remove('dark')
  document.documentElement.style.colorScheme = ''
})
afterAll(() => server.close())
