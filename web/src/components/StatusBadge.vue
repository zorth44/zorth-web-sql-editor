<script setup lang="ts">
defineProps<{ status: 'SUCCESS' | 'FAILED' | null }>()
</script>
<template>
  <span
    v-if="status"
    class="inline-flex rounded-full px-2.5 py-1 text-xs font-semibold"
    :class="status === 'SUCCESS' ? 'bg-success-soft text-success' : 'bg-danger-soft text-danger'"
    ><span aria-hidden="true">{{ status === 'SUCCESS' ? '✓' : '!' }}&nbsp;</span
    >{{ status === 'SUCCESS' ? '成功' : '失败' }}</span
  ><span v-else class="text-sm text-muted">未测试</span>
</template>
