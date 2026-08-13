<script setup lang="ts">
defineProps<{ message: string; retryable?: boolean }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <div class="panel p-8 text-center" role="alert">
    <p class="mb-4 text-danger">{{ message }}</p>
    <button v-if="retryable" class="btn" type="button" @click="$emit('retry')">重试</button>
  </div>
</template>
