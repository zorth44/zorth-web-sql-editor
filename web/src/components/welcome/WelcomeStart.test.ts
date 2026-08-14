import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import WelcomeStart from '@/components/welcome/WelcomeStart.vue'

async function render() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/data-sources', component: { template: '<div>sources</div>' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  return mount(WelcomeStart, { global: { plugins: [router] } })
}

describe('welcome start', () => {
  it('offers SQL editor and data-source entry points', async () => {
    const wrapper = await render()
    expect(wrapper.get('[data-testid="welcome-start"]').text()).toContain('Zorth SQL Editor')
    expect(wrapper.get('[data-testid="welcome-open-sql"]').text()).toContain('打开 SQL 编辑器')
    expect(wrapper.get('[data-testid="welcome-data-sources"]').attributes('href')).toBe(
      '/data-sources',
    )
    await wrapper.get('[data-testid="welcome-open-sql"]').trigger('click')
    expect(wrapper.emitted('open-sql')).toHaveLength(1)
    wrapper.unmount()
  })
})
