<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>
<template>
  <div class="p-12 text-center">
    <h2 class="text-lg font-semibold">{{ title }}</h2>
    <p v-if="description" class="mt-2 text-sm text-muted">{{ description }}</p>
    <div class="mt-5"><slot /></div>
  </div>
</template>
