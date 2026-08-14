import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export const THEME_KEY = 'zorth.sql.theme'
export type ColorScheme = 'light' | 'dark'

function prefersDark(): boolean {
  return Boolean(window.matchMedia?.('(prefers-color-scheme: dark)')?.matches)
}

function readStored(): ColorScheme | null {
  const value = localStorage.getItem(THEME_KEY)
  return value === 'light' || value === 'dark' ? value : null
}

function apply(scheme: ColorScheme): void {
  document.documentElement.classList.toggle('dark', scheme === 'dark')
  document.documentElement.style.colorScheme = scheme
}

export function resolveScheme(stored = readStored()): ColorScheme {
  return stored ?? (prefersDark() ? 'dark' : 'light')
}

export const useThemeStore = defineStore('theme', () => {
  const scheme = ref<ColorScheme>(resolveScheme())
  apply(scheme.value)

  const media = window.matchMedia?.('(prefers-color-scheme: dark)')
  media?.addEventListener?.('change', (event) => {
    if (readStored()) return
    const next: ColorScheme = event.matches ? 'dark' : 'light'
    scheme.value = next
    apply(next)
  })

  function setScheme(next: ColorScheme): void {
    scheme.value = next
    localStorage.setItem(THEME_KEY, next)
    apply(next)
  }

  function toggle(): void {
    setScheme(scheme.value === 'dark' ? 'light' : 'dark')
  }

  return {
    scheme,
    isDark: computed(() => scheme.value === 'dark'),
    setScheme,
    toggle,
  }
})
