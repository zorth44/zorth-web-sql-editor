import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EngineTypeIcon from '@/components/EngineTypeIcon.vue'

describe('EngineTypeIcon', () => {
  it('renders card logos by default', () => {
    const wrapper = mount(EngineTypeIcon, { props: { engine: 'MYSQL' } })
    expect(wrapper.attributes('data-engine-icon')).toBe('MYSQL')
    expect(wrapper.attributes('data-engine-variant')).toBe('card')
    expect(wrapper.classes()).toContain('engine-type-icon')
    wrapper.unmount()
  })

  it('renders compact tree marks for each known engine', () => {
    for (const engine of ['MYSQL', 'POSTGRESQL', 'GBASE_8A'] as const) {
      const wrapper = mount(EngineTypeIcon, { props: { engine, variant: 'tree' } })
      expect(wrapper.attributes('data-engine-icon')).toBe(engine)
      expect(wrapper.attributes('data-engine-variant')).toBe('tree')
      expect(wrapper.classes()).toContain('tree-engine-icon')
      expect(wrapper.find('img').exists()).toBe(true)
      wrapper.unmount()
    }
  })
})
