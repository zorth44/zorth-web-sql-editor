<script setup lang="ts">
import { Database, Monitor } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'

defineEmits<{ 'open-sql': [] }>()
</script>
<template>
  <div class="welcome-start" data-testid="welcome-start">
    <Database class="welcome-watermark" :size="420" stroke-width="1" aria-hidden="true" />
    <div class="welcome-copy">
      <h1 class="welcome-title">Zorth SQL Editor</h1>
      <h2 class="welcome-heading">开始</h2>
      <div class="welcome-tiles">
        <button
          type="button"
          class="welcome-tile"
          data-testid="welcome-open-sql"
          @click="$emit('open-sql')"
        >
          <span class="welcome-tile-icon">
            <Monitor :size="28" />
          </span>
          <span class="welcome-tile-text">
            <strong>SQL</strong>
            <span>打开 SQL 编辑器</span>
          </span>
        </button>
        <RouterLink to="/data-sources" class="welcome-tile" data-testid="welcome-data-sources">
          <span class="welcome-tile-icon">
            <Database :size="28" />
          </span>
          <span class="welcome-tile-text">
            <strong>数据源</strong>
            <span>管理数据源连接</span>
          </span>
        </RouterLink>
      </div>
      <p class="welcome-hint">未选择数据源和数据库时无法执行 SQL。请先在左侧资源树中选择连接。</p>
    </div>
  </div>
</template>
