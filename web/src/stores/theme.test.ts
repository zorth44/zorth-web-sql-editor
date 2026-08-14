import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { THEME_KEY, resolveScheme, useThemeStore } from '@/stores/theme'

function stubMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: query.includes('dark') ? matches : false,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
    onchange: null,
  }))
}

describe('theme store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.documentElement.classList.remove('dark')
    document.documentElement.style.colorScheme = ''
  })

  it('follows system preference when nothing is stored', () => {
    stubMatchMedia(true)
    expect(resolveScheme(null)).toBe('dark')
    stubMatchMedia(false)
    expect(resolveScheme(null)).toBe('light')
  })

  it('toggles, persists, and applies the dark class', () => {
    stubMatchMedia(false)
    const store = useThemeStore()
    expect(store.scheme).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    store.toggle()
    expect(store.scheme).toBe('dark')
    expect(store.isDark).toBe(true)
    expect(localStorage.getItem(THEME_KEY)).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(document.documentElement.style.colorScheme).toBe('dark')
  })

  it('restores the stored scheme over system preference', () => {
    localStorage.setItem(THEME_KEY, 'dark')
    stubMatchMedia(false)
    const store = useThemeStore()
    expect(store.scheme).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })
})
