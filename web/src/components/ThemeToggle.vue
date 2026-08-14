<script setup lang="ts">
import { Moon, Sun } from 'lucide-vue-next'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
</script>
<template>
  <button
    class="icon-btn h-8 w-8"
    type="button"
    :aria-label="theme.isDark ? '切换为白天模式' : '切换为黑夜模式'"
    :title="theme.isDark ? '白天模式' : '黑夜模式'"
    @click="theme.toggle()"
  >
    <Sun v-if="theme.isDark" :size="16" />
    <Moon v-else :size="16" />
  </button>
</template>
