import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AuthBridgePage from '@/pages/AuthBridgePage.vue'
import { TOKEN_KEY } from '@/auth/token-storage'
import { queryClient } from '@/query/client'

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/auth/bridge', component: AuthBridgePage },
      { path: '/data-sources', component: { template: '<div>list</div>' } },
    ],
  })
}

describe('Token bridge receiver', () => {
  afterEach(() => {
    Object.defineProperty(window, 'opener', { configurable: true, value: null })
    queryClient.clear()
  })
  it('validates source/origin/Token and acknowledges without echoing it', async () => {
    const postMessage = vi.fn()
    const opener = { postMessage } as unknown as WindowProxy
    Object.defineProperty(window, 'opener', { configurable: true, value: opener })
    const router = testRouter()
    await router.push('/auth/bridge')
    await router.isReady()
    const wrapper = mount(AuthBridgePage, { global: { plugins: [createPinia(), router] } })
    window.dispatchEvent(
      new MessageEvent('message', {
        origin: 'http://legacy.example.test',
        source: opener,
        data: { type: 'ZORTH_SQL_AUTH_TOKEN', version: 1, token: 'bridge-token' },
      }),
    )
    await flushPromises()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('bridge-token')
    expect(postMessage).toHaveBeenCalledWith(
      { type: 'ZORTH_SQL_AUTH_ACCEPTED', version: 1 },
      { targetOrigin: 'http://legacy.example.test' },
    )
    expect(JSON.stringify(postMessage.mock.calls)).not.toContain('bridge-token')
    expect(router.currentRoute.value.path).toBe('/data-sources')
    wrapper.unmount()
  })
  it('ignores a message from a non-allow-listed origin', async () => {
    const opener = { postMessage: vi.fn() } as unknown as WindowProxy
    Object.defineProperty(window, 'opener', { configurable: true, value: opener })
    const router = testRouter()
    await router.push('/auth/bridge')
    await router.isReady()
    const wrapper = mount(AuthBridgePage, { global: { plugins: [createPinia(), router] } })
    window.dispatchEvent(
      new MessageEvent('message', {
        origin: 'http://evil.test',
        source: opener,
        data: { type: 'ZORTH_SQL_AUTH_TOKEN', version: 1, token: 'rejected' },
      }),
    )
    await flushPromises()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(wrapper.text()).toContain('正在等待')
    wrapper.unmount()
  })
})
