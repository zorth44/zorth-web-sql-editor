import { appEnv } from '@/env'

export async function startDevelopmentMock(): Promise<void> {
  const isAllowedMode = import.meta.env.DEV || import.meta.env.MODE === 'test'
  if (!isAllowedMode || !appEnv.apiMockEnabled) return
  const { worker } = await import('@/mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}
