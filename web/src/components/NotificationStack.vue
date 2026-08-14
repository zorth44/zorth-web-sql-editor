<script setup lang="ts">
import { useNotificationsStore } from '@/stores/notifications'
const notices = useNotificationsStore()
</script>
<template>
  <div class="fixed right-5 top-5 z-50 flex w-80 flex-col gap-2" aria-live="polite">
    <button
      v-for="item in notices.items"
      :key="item.id"
      class="rounded-lg border bg-panel p-4 text-left shadow-panel"
      :class="
        item.kind === 'error'
          ? 'border-danger text-danger'
          : item.kind === 'success'
            ? 'border-success text-success'
            : 'border-line'
      "
      type="button"
      @click="notices.remove(item.id)"
    >
      {{ item.message }}
    </button>
  </div>
</template>
