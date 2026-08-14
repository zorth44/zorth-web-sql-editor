<script setup lang="ts">
import { LogOut, Database, Code2 } from 'lucide-vue-next'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const workspace = computed(() => Boolean(route.meta.workspace))
async function logout(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}
</script>
<template>
  <div class="flex h-screen flex-col overflow-hidden">
    <header class="flex h-12 shrink-0 items-center border-b border-line bg-white px-4">
      <RouterLink to="/sql-editor" class="flex items-center gap-2 font-semibold text-ink">
        <span class="grid h-8 w-8 place-items-center rounded-lg bg-brand text-white">
          <Database :size="18" />
        </span>
        Zorth SQL Editor
      </RouterLink>
      <nav class="ml-8 flex items-center gap-1" aria-label="主导航">
        <RouterLink
          to="/sql-editor"
          class="rounded-md px-2.5 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50"
          active-class="!bg-teal-50 !text-brand"
        >
          <Code2 :size="15" class="mr-1 inline" />SQL 编辑器
        </RouterLink>
        <RouterLink
          to="/data-sources"
          class="rounded-md px-2.5 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50"
          active-class="!bg-teal-50 !text-brand"
        >
          数据源管理
        </RouterLink>
      </nav>
      <div class="ml-auto flex items-center gap-4">
        <div class="text-right">
          <p class="text-[11px] leading-none text-muted">当前产品</p>
          <p class="mt-1 text-sm font-medium">{{ auth.session?.product.name }}</p>
        </div>
        <div class="h-7 w-px bg-line" />
        <div>
          <p class="text-sm font-medium">{{ auth.session?.user.displayName }}</p>
          <p class="text-[11px] text-muted">{{ auth.session?.user.username }}</p>
        </div>
        <button class="btn min-h-8 px-3 py-1.5 text-xs" type="button" @click="logout">
          <LogOut :size="14" />退出
        </button>
      </div>
    </header>
    <main class="min-h-0 flex-1" :class="workspace ? 'overflow-hidden' : 'overflow-y-auto'">
      <div :class="workspace ? 'h-full' : 'mx-auto max-w-[1500px] p-6'">
        <RouterView />
      </div>
    </main>
  </div>
</template>
