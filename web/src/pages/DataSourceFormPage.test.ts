import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'
import DataSourceFormPage from '@/pages/DataSourceFormPage.vue'
import { mockDataSources, mockSession } from '@/mocks/fixtures'
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
    expect((wrapper.get('#ds-password').element as HTMLInputElement).value).toBe(
      'replacement-secret',
    )
    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/data-sources')
    wrapper.unmount()
  })
})

describe('data-source form create experience', () => {
  beforeEach(() => queryClient.clear())

  it('picks the engine from icon cards and keeps JDBC properties collapsed', async () => {
    const { wrapper } = await renderFormPage('/data-sources/new')
    expect(wrapper.get('[data-testid="engine-type-MYSQL"]').classes()).toContain(
      'engine-type-card-selected',
    )
    expect(wrapper.get('label[for="ds-defaultDatabase"]').text()).toBe('默认数据库')
    expect((wrapper.get('#ds-defaultDatabase').element as HTMLInputElement).placeholder).toBe(
      '手工输入，可留空',
    )
    expect(wrapper.get('[data-testid="engine-type-POSTGRESQL"]').text()).toContain('PostgreSQL')
    expect(wrapper.get('[data-testid="advanced-jdbc"]').attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('[data-testid="advanced-jdbc-fields"]').isVisible()).toBe(false)
    expect(wrapper.get('#property-serverTimezone').isVisible()).toBe(false)

    await wrapper.get('#ds-engine-POSTGRESQL').setValue()
    await flushPromises()
    expect(wrapper.get('[data-testid="engine-type-POSTGRESQL"]').classes()).toContain(
      'engine-type-card-selected',
    )
    expect((wrapper.get('#ds-port').element as HTMLInputElement).value).toBe('5432')
    expect(wrapper.get('label[for="ds-defaultDatabase"]').text()).toContain('数据库名')
    expect(wrapper.get('label[for="ds-defaultDatabase"]').text()).toContain('*')
    expect((wrapper.get('#ds-defaultDatabase').element as HTMLInputElement).placeholder).toBe(
      '请输入要连接的数据库',
    )
    expect(wrapper.get('#ds-defaultDatabase').attributes('placeholder')).not.toContain('可留空')
    expect(wrapper.text()).toContain('资源树里列出的是该库下的模式')
    expect(wrapper.get('[data-testid="advanced-jdbc-fields"]').isVisible()).toBe(false)

    await wrapper.get('[data-testid="advanced-jdbc"]').trigger('click')
    expect(wrapper.get('[data-testid="advanced-jdbc"]').attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('#property-ApplicationName').isVisible()).toBe(true)
    expect(wrapper.find('#property-serverTimezone').exists()).toBe(false)
    await wrapper.get('#ds-engine-GBASE_8A').setValue()
    await flushPromises()
    expect(wrapper.get('[data-testid="engine-type-GBASE_8A"]').text()).toContain('GBase 8a')
    expect(wrapper.get('[data-testid="engine-type-GBASE_8A"]').classes()).toContain(
      'engine-type-card-selected',
    )
    expect((wrapper.get('#ds-port').element as HTMLInputElement).value).toBe('5258')
    expect(wrapper.get('label[for="ds-defaultDatabase"]').text()).toBe('默认数据库')
    expect((wrapper.get('#ds-defaultDatabase').element as HTMLInputElement).placeholder).toBe(
      '手工输入，可留空',
    )
    expect(wrapper.text()).not.toContain('资源树里列出的是该库下的模式')
    expect(wrapper.get('#property-serverTimezone').isVisible()).toBe(true)
    expect(wrapper.find('#property-ApplicationName').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders Hive from the descriptor: port 10000, optional namespace, metastore only', async () => {
    const { wrapper } = await renderFormPage('/data-sources/new')
    expect(wrapper.get('[data-testid="engine-type-HIVE"]').text()).toContain('Hive')

    await wrapper.get('#ds-engine-HIVE').setValue()
    await flushPromises()
    expect(wrapper.get('[data-testid="engine-type-HIVE"]').classes()).toContain(
      'engine-type-card-selected',
    )
    expect((wrapper.get('#ds-port').element as HTMLInputElement).value).toBe('10000')
    expect(wrapper.get('label[for="ds-defaultDatabase"]').text()).toBe('默认数据库')
    expect(wrapper.get('label[for="ds-defaultDatabase"]').text()).not.toContain('*')
    expect((wrapper.get('#ds-defaultDatabase').element as HTMLInputElement).placeholder).toBe(
      '手工输入，可留空',
    )

    await wrapper.get('[data-testid="advanced-jdbc"]').trigger('click')
    expect(wrapper.find('[id="property-hive.metastore.uris"]').exists()).toBe(true)
    expect(wrapper.find('#property-ApplicationName').exists()).toBe(false)
    expect(wrapper.find('#property-serverTimezone').exists()).toBe(false)

    await wrapper.get('#ds-engine-POSTGRESQL').setValue()
    await flushPromises()
    expect(wrapper.find('#property-ApplicationName').exists()).toBe(true)
    await wrapper.get('#ds-engine-HIVE').setValue()
    await flushPromises()
    expect(wrapper.find('#property-ApplicationName').exists()).toBe(false)
    expect(wrapper.find('[id="property-hive.metastore.uris"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders Kerberos Hive fields from the descriptor and hides host-based fields', async () => {
    const { wrapper } = await renderFormPage('/data-sources/new')
    expect(wrapper.get('[data-testid="engine-type-HIVE_KERBEROS"]').text()).toContain(
      'Kerberos Hive',
    )

    await wrapper.get('#ds-engine-HIVE_KERBEROS').setValue()
    await flushPromises()
    expect(wrapper.get('[data-testid="engine-type-HIVE_KERBEROS"]').classes()).toContain(
      'engine-type-card-selected',
    )

    expect(wrapper.get('#ds-environment').element.tagName).toBe('SELECT')
    expect(
      wrapper
        .get('#ds-environment')
        .findAll('option')
        .map((option) => option.attributes('value')),
    ).toEqual(['dev', 'func', 'pro'])
    expect(wrapper.get('#ds-keytabFile').element.tagName).toBe('INPUT')
    expect(wrapper.get('label[for="ds-keytabFile"]').text()).toContain('*')
    expect(wrapper.find('#ds-queueName').exists()).toBe(true)
    expect(wrapper.get('label[for="ds-queueName"]').text()).not.toContain('*')
    expect(wrapper.find('#ds-defaultDatabase').exists()).toBe(true)

    for (const hidden of ['ds-host', 'ds-port', 'ds-username', 'ds-password', 'ds-sslMode']) {
      expect(wrapper.find(`#${hidden}`).exists()).toBe(false)
    }
    wrapper.unmount()
  })

  it('renders Iceberg fields from the descriptor and hides host-based fields', async () => {
    const { wrapper } = await renderFormPage('/data-sources/new')
    expect(wrapper.get('[data-testid="engine-type-ICEBERG"]').text()).toContain('Iceberg')

    await wrapper.get('#ds-engine-ICEBERG').setValue()
    await flushPromises()
    expect(wrapper.get('[data-testid="engine-type-ICEBERG"]').classes()).toContain(
      'engine-type-card-selected',
    )

    expect(wrapper.get('#ds-environment').element.tagName).toBe('SELECT')
    expect(
      wrapper
        .get('#ds-environment')
        .findAll('option')
        .map((option) => option.attributes('value')),
    ).toEqual(['dev', 'func', 'pro'])
    expect(wrapper.get('#ds-keytabFile').element.tagName).toBe('INPUT')
    expect(wrapper.get('label[for="ds-keytabFile"]').text()).toContain('*')
    expect(wrapper.find('#ds-queueName').exists()).toBe(true)
    expect(wrapper.get('label[for="ds-queueName"]').text()).not.toContain('*')
    expect(wrapper.find('#ds-defaultDatabase').exists()).toBe(true)

    for (const hidden of ['ds-host', 'ds-port', 'ds-username', 'ds-password', 'ds-sslMode']) {
      expect(wrapper.find(`#${hidden}`).exists()).toBe(false)
    }
    wrapper.unmount()
  })

  it('submits Iceberg descriptor fields without host-based credentials', async () => {
    const { wrapper, router } = await renderFormPage('/data-sources/new')
    await wrapper.get('#ds-name').setValue('Iceberg 订单库')
    await wrapper.get('#ds-engine-ICEBERG').setValue()
    await flushPromises()
    await wrapper.get('#ds-environment').setValue('func')
    await wrapper.get('#ds-keytabFile').setValue('orders.keytab')
    await wrapper.get('#ds-queueName').setValue('etl')

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/data-sources')
    const created = mockDataSources().find((item) => item.name === 'Iceberg 订单库')
    expect(created?.engine).toBe('ICEBERG')
    expect(created?.passwordConfigured).toBe(false)
    expect(created?.host).toBe('')
    wrapper.unmount()
  })

  it('submits Kerberos descriptor fields without host-based credentials', async () => {
    const { wrapper, router } = await renderFormPage('/data-sources/new')
    await wrapper.get('#ds-name').setValue('Kerberos 订单库')
    await wrapper.get('#ds-engine-HIVE_KERBEROS').setValue()
    await flushPromises()
    await wrapper.get('#ds-environment').setValue('func')
    await wrapper.get('#ds-keytabFile').setValue('orders.keytab')
    await wrapper.get('#ds-queueName').setValue('etl')

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/data-sources')
    const created = mockDataSources().find((item) => item.name === 'Kerberos 订单库')
    expect(created?.engine).toBe('HIVE_KERBEROS')
    expect(created?.passwordConfigured).toBe(false)
    expect(created?.host).toBe('')
    wrapper.unmount()
  })
})
