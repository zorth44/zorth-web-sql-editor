import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'
import DataSourceFormPage from '@/pages/DataSourceFormPage.vue'
import { mockSession } from '@/mocks/fixtures'
import { queryClient } from '@/query/client'
import { useAuthStore } from '@/stores/auth'
import { saveToken } from '@/auth/token-storage'

async function renderFormPage(path: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().session = mockSession
  saveToken('mock-token', false)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/data-sources', component: { template: '<div>list</div>' } },
      { path: '/data-sources/new', component: DataSourceFormPage },
      { path: '/data-sources/:id/edit', component: DataSourceFormPage },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(DataSourceFormPage, {
    attachTo: document.body,
    global: { plugins: [pinia, [VueQueryPlugin, { queryClient }], router] },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillCreateForm(wrapper: VueWrapper, password = 'db-secret') {
  await wrapper.get('#ds-name').setValue('订单测试库')
  await wrapper.get('#ds-host').setValue('new-mysql.internal')
  await wrapper.get('#ds-username').setValue('new_user')
  await wrapper.get('#ds-password').setValue(password)
}

function buttonByText(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(label))!
}

describe('data-source form password retention', () => {
  beforeEach(() => queryClient.clear())

  it('keeps the create password after a successful connection test so save does not ask again', async () => {
    const { wrapper, router } = await renderFormPage('/data-sources/new')
    await fillCreateForm(wrapper)
    await buttonByText(wrapper, '测试连接').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('连接成功')
    expect((wrapper.get('#ds-password').element as HTMLInputElement).value).toBe('db-secret')
    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('新增数据源必须输入密码')
    expect(router.currentRoute.value.path).toBe('/data-sources')
    wrapper.unmount()
  })

  it('keeps a replacement edit password after testing so save can send it', async () => {
    const { wrapper, router } = await renderFormPage('/data-sources/ds-orders-a/edit')
    await wrapper.get('#ds-password').setValue('replacement-secret')
    await buttonByText(wrapper, '测试连接').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('连接成功')
    expect((wrapper.get('#ds-password').element as HTMLInputElement).value).toBe('replacement-secret')
    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/data-sources')
    wrapper.unmount()
  })
})
