import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AuthBridgePage from '@/pages/AuthBridgePage.vue'
import { TOKEN_KEY } from '@/auth/token-storage'
import { queryClient } from '@/query/client'
import { server } from '@/mocks/server'
import { appEnv } from '@/env'
import { BRIDGE_ACCEPTED, BRIDGE_FAILED, BRIDGE_READY } from '@/auth/bridge'

const LEGACY_ORIGIN = 'http://legacy.example.test'

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/auth/bridge', component: AuthBridgePage },
      { path: '/login', component: { template: '<div>login</div>' } },
      { path: '/data-sources', component: { template: '<div>list</div>' } },
      { path: '/sql-editor', component: { template: '<div>editor</div>' } },
    ],
  })
}

function tokenEvent(source: WindowProxy, token = 'bridge-token') {
  return new MessageEvent('message', {
    origin: LEGACY_ORIGIN,
    source,
    data: { type: 'ZORTH_SQL_AUTH_TOKEN', version: 1, token },
  })
}

describe('Token bridge receiver', () => {
  afterEach(() => {
    Object.defineProperty(window, 'opener', { configurable: true, value: null })
    queryClient.clear()
  })

  it('announces READY then validates Token without echoing it', async () => {
    const postMessage = vi.fn()
    const opener = { postMessage } as unknown as WindowProxy
    Object.defineProperty(window, 'opener', { configurable: true, value: opener })
    const router = testRouter()
    await router.push('/auth/bridge')
    await router.isReady()
    const wrapper = mount(AuthBridgePage, { global: { plugins: [createPinia(), router] } })
    expect(postMessage).toHaveBeenCalledWith(BRIDGE_READY, LEGACY_ORIGIN)
    window.dispatchEvent(tokenEvent(opener))
    await flushPromises()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('bridge-token')
    expect(postMessage).toHaveBeenCalledWith(BRIDGE_ACCEPTED, LEGACY_ORIGIN)
    expect(JSON.stringify(postMessage.mock.calls)).not.toContain('bridge-token')
    expect(router.currentRoute.value.path).toBe('/sql-editor')
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
    expect(wrapper.text()).toContain('已通知原系统')
    wrapper.unmount()
  })

  it('rejects a second Token while validating or after success', async () => {
    const postMessage = vi.fn()
    const opener = { postMessage } as unknown as WindowProxy
    Object.defineProperty(window, 'opener', { configurable: true, value: opener })
    const router = testRouter()
    await router.push('/auth/bridge')
    await router.isReady()
    const wrapper = mount(AuthBridgePage, { global: { plugins: [createPinia(), router] } })
    window.dispatchEvent(tokenEvent(opener, 'first-token'))
    window.dispatchEvent(tokenEvent(opener, 'second-token'))
    await flushPromises()
    const accepted = postMessage.mock.calls.filter((call) => call[0]?.type === BRIDGE_ACCEPTED.type)
    expect(accepted).toHaveLength(1)
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('first-token')
    expect(JSON.stringify(postMessage.mock.calls)).not.toContain('first-token')
    expect(JSON.stringify(postMessage.mock.calls)).not.toContain('second-token')
    wrapper.unmount()
  })

  it('clears prior session cache and replies FAILED without Token details', async () => {
    const postMessage = vi.fn()
    const opener = { postMessage } as unknown as WindowProxy
    Object.defineProperty(window, 'opener', { configurable: true, value: opener })
    queryClient.setQueryData(['session'], {
      user: { id: 'old', username: 'old-user', displayName: 'Old' },
      product: { id: 'old-p', name: 'Old Product' },
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      capabilities: ['SQL_EXECUTE'],
    })
    server.use(
      http.get(`${appEnv.sqlApiBase}/api/v1/session`, () =>
        HttpResponse.json(
          { requestId: 'r1', code: 'UNAUTHENTICATED', message: 'expired token secret' },
          { status: 401 },
        ),
      ),
    )
    const router = testRouter()
    await router.push('/auth/bridge')
    await router.isReady()
    const wrapper = mount(AuthBridgePage, { global: { plugins: [createPinia(), router] } })
    window.dispatchEvent(tokenEvent(opener, 'expired-token'))
    await flushPromises()
    expect(postMessage).toHaveBeenCalledWith(BRIDGE_FAILED, LEGACY_ORIGIN)
    expect(JSON.stringify(postMessage.mock.calls)).not.toContain('expired-token')
    expect(JSON.stringify(postMessage.mock.calls)).not.toContain('secret')
    expect(queryClient.getQueryData(['session'])).toBeUndefined()
    expect(wrapper.text()).toContain('桥接认证失败')
    wrapper.unmount()
  })
})
