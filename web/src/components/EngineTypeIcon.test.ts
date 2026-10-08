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
    for (const engine of [
      'MYSQL',
      'POSTGRESQL',
      'GBASE_8A',
      'HIVE',
      'HIVE_KERBEROS',
      'ICEBERG',
    ] as const) {
      const wrapper = mount(EngineTypeIcon, { props: { engine, variant: 'tree' } })
      expect(wrapper.attributes('data-engine-icon')).toBe(engine)
      expect(wrapper.attributes('data-engine-variant')).toBe('tree')
      expect(wrapper.classes()).toContain('tree-engine-icon')
      expect(wrapper.find('img').exists()).toBe(true)
      wrapper.unmount()
    }
  })

  it('renders a Hive logo in the card variant', () => {
    const wrapper = mount(EngineTypeIcon, { props: { engine: 'HIVE' } })
    expect(wrapper.attributes('data-engine-icon')).toBe('HIVE')
    expect(wrapper.attributes('data-engine-variant')).toBe('card')
    expect(wrapper.classes()).toContain('engine-type-icon')
    expect(wrapper.find('img').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders a Kerberos Hive mark in the tree variant', () => {
    const wrapper = mount(EngineTypeIcon, { props: { engine: 'HIVE_KERBEROS', variant: 'tree' } })
    expect(wrapper.attributes('data-engine-icon')).toBe('HIVE_KERBEROS')
    expect(wrapper.classes()).toContain('tree-engine-icon')
    expect(wrapper.find('img').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders a Kerberos Hive logo in the card variant', () => {
    const wrapper = mount(EngineTypeIcon, { props: { engine: 'HIVE_KERBEROS' } })
    expect(wrapper.attributes('data-engine-icon')).toBe('HIVE_KERBEROS')
    expect(wrapper.attributes('data-engine-variant')).toBe('card')
    expect(wrapper.classes()).toContain('engine-type-icon')
    expect(wrapper.find('img').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders an Iceberg mark in the tree variant', () => {
    const wrapper = mount(EngineTypeIcon, { props: { engine: 'ICEBERG', variant: 'tree' } })
    expect(wrapper.attributes('data-engine-icon')).toBe('ICEBERG')
    expect(wrapper.classes()).toContain('tree-engine-icon')
    expect(wrapper.find('img').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders an Iceberg logo in the card variant', () => {
    const wrapper = mount(EngineTypeIcon, { props: { engine: 'ICEBERG' } })
    expect(wrapper.attributes('data-engine-icon')).toBe('ICEBERG')
    expect(wrapper.attributes('data-engine-variant')).toBe('card')
    expect(wrapper.classes()).toContain('engine-type-icon')
    expect(wrapper.find('img').exists()).toBe(true)
    wrapper.unmount()
  })
})
