<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import TaskList from '@/components/sql-optimizer/TaskList.vue'
import { useOptimizerAccess } from '@/sql-optimizer/queries'
const { allowed } = useOptimizerAccess()
</script>
<template>
  <section class="sql-optimizer-surface">
    <div class="mb-6 flex items-end justify-between gap-4">
      <div>
        <p class="text-sm font-medium text-brand">扫描任务</p>
        <h1 class="mt-1 text-3xl font-semibold">SQL 优化器</h1>
        <p class="mt-2 text-sm text-muted">提交扫描后可以离开页面，稍后在任务中查看报告。</p>
      </div>
      <RouterLink v-if="allowed" class="btn-primary shrink-0" to="/sql-optimizer/new"
        ><Plus :size="17" />新建扫描</RouterLink
      >
    </div>
    <TaskList v-if="allowed" />
    <p v-else class="panel p-8 text-muted" role="alert">当前账号没有 SQL 优化器能力。</p>
  </section>
</template>
