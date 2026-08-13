<script setup lang="ts">
defineProps<{ label?: string }>()
</script>
<template>
  <div class="flex min-h-40 items-center justify-center gap-3 text-muted" role="status">
    <span
      class="h-5 w-5 animate-spin rounded-full border-2 border-brand border-r-transparent"
      aria-hidden="true"
    /><span>{{ label || '正在加载…' }}</span>
  </div>
</template>
