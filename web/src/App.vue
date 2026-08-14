<script setup lang="ts">
import NotificationStack from '@/components/NotificationStack.vue'
import { useThemeStore } from '@/stores/theme'

useThemeStore()
</script>
<template><RouterView /><NotificationStack /></template>
