import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import ScriptPanel from '@/components/scripts/ScriptPanel.vue'
import { createScript } from '@/api/scripts'
import { saveToken } from '@/auth/token-storage'

async function renderPanel() {
  const wrapper = mount(ScriptPanel)
  await flushPromises()
  return wrapper
}

describe('script panel', () => {
  beforeEach(() => saveToken('mock-token', false))

  it('lists saved scripts with updated time and opens on click', async () => {
    await createScript({ name: '月报', statement: 'select 1', dataSourceId: 'ds-orders-a' })
    const wrapper = await renderPanel()
    expect(wrapper.text()).toContain('月报')
    expect(wrapper.text()).toContain('select 1')
    await wrapper.get('[aria-label="打开脚本 月报"]').trigger('click')
    expect(wrapper.emitted('open')?.[0]).toEqual([expect.any(String)])
  })

  it('emits rename and can delete a listed script', async () => {
    await createScript({ name: '草稿', statement: 'select 2' })
    const wrapper = await renderPanel()
    await wrapper.get('[aria-label="重命名 草稿"]').trigger('click')
    expect(wrapper.emitted('rename')?.[0]?.[0]).toMatchObject({ name: '草稿' })
    await wrapper.get('[aria-label="删除 草稿"]').trigger('click')
    await wrapper.get('.btn-danger').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('deleted')?.[0]).toEqual([expect.any(String)])
    expect(wrapper.text()).toContain('暂无保存的脚本')
  })

  it('lists scripts from every connection, not only the current one', async () => {
    await createScript({ name: 'A库脚本', statement: 'select 1', dataSourceId: 'ds-orders-a' })
    await createScript({ name: '未绑定', statement: 'select 2' })
    const wrapper = await renderPanel()
    expect(wrapper.text()).toContain('A库脚本')
    expect(wrapper.text()).toContain('未绑定')
  })
})
