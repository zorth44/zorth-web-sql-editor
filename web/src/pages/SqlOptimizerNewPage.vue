<script setup lang="ts">
import { ArrowLeft } from 'lucide-vue-next'
import TaskCreateForm from '@/components/sql-optimizer/TaskCreateForm.vue'
import { useOptimizerAccess } from '@/sql-optimizer/queries'
const { allowed } = useOptimizerAccess()
</script>
<template>
  <section class="sql-optimizer-surface flex min-h-[calc(100vh-7.5rem)] flex-col">
    <RouterLink
      class="mb-5 inline-flex items-center gap-2 text-sm font-medium text-muted hover:text-brand"
      to="/sql-optimizer"
      ><ArrowLeft :size="16" />返回 SQL 优化器</RouterLink
    >
    <div class="mb-6">
      <p class="text-sm font-medium text-brand">新建扫描</p>
      <h1 class="mt-1 text-3xl font-semibold">提交一条只读 SQL</h1>
      <p class="mt-2 text-sm text-muted">提交后可以离开页面，稍后在任务中查看报告。</p>
    </div>
    <TaskCreateForm v-if="allowed" class="min-h-0 flex-1" />
    <p v-else class="panel p-8 text-muted" role="alert">当前账号没有 SQL 优化器能力。</p>
  </section>
</template>
