import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '@/mocks/server'
import { resetMockData } from '@/mocks/fixtures'
import { resetAgentConversations, resetMockScripts } from '@/mocks/handlers'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  resetMockData()
  resetAgentConversations()
  resetMockScripts()
  localStorage.clear()
  sessionStorage.clear()
  document.documentElement.classList.remove('dark')
  document.documentElement.style.colorScheme = ''
})
afterAll(() => server.close())
