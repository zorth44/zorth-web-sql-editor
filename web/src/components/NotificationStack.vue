<script setup lang="ts">
import { useNotificationsStore } from '@/stores/notifications'
const notices = useNotificationsStore()
</script>
<template>
  <div class="fixed right-5 top-5 z-50 flex w-80 flex-col gap-2" aria-live="polite">
    <button
      v-for="item in notices.items"
      :key="item.id"
      class="rounded-lg border bg-white p-4 text-left shadow-panel"
      :class="
        item.kind === 'error'
          ? 'border-red-200 text-danger'
          : item.kind === 'success'
            ? 'border-emerald-200 text-success'
            : 'border-line'
      "
      type="button"
      @click="notices.remove(item.id)"
    >
      {{ item.message }}
    </button>
  </div>
</template>
