<script setup lang="ts">
import TaskCreateForm from '@/components/sql-optimizer/TaskCreateForm.vue'
import TaskList from '@/components/sql-optimizer/TaskList.vue'
import { useOptimizerAccess } from '@/sql-optimizer/queries'
const { allowed } = useOptimizerAccess()
</script>
<template>
  <section class="sql-optimizer-surface">
    <header class="mb-6">
      <p class="text-sm font-medium text-brand">异步 SQL 分析</p>
      <h1 class="mt-1 text-3xl font-semibold">SQL 优化器</h1>
      <p class="mt-2 text-sm text-muted">查看组织规则与实际取证支持的问题、建议和证据缺口。</p>
    </header>
    <template v-if="allowed"><TaskCreateForm /><TaskList /></template>
    <p v-else class="panel p-8 text-muted" role="alert">当前账号没有 SQL 优化器能力。</p>
  </section>
</template>
