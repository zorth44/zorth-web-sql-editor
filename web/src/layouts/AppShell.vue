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
  <div class="min-h-screen">
    <header class="border-b border-line bg-white">
      <div class="mx-auto flex h-12 max-w-[1800px] items-center px-4">
        <RouterLink to="/sql-editor" class="flex items-center gap-2 font-semibold text-ink"
          ><span class="grid h-8 w-8 place-items-center rounded-lg bg-brand text-white"
            ><Database :size="20" /></span
          >Zorth SQL Editor</RouterLink
        >
        <nav class="ml-10" aria-label="主导航">
          <RouterLink
            to="/sql-editor"
            class="rounded-lg px-3 py-2 text-sm font-medium"
            active-class="bg-teal-50 text-brand"
            ><Code2 :size="15" class="mr-1 inline" />SQL 编辑器</RouterLink
          ><RouterLink
            to="/data-sources"
            class="rounded-lg px-3 py-2 text-sm font-medium"
            active-class="bg-teal-50 text-brand"
            >数据源管理</RouterLink
          >
        </nav>
        <div class="ml-auto flex items-center gap-5">
          <div class="text-right">
            <p class="text-xs text-muted">当前产品</p>
            <p class="text-sm font-medium">{{ auth.session?.product.name }}</p>
          </div>
          <div class="h-8 w-px bg-line" />
          <div>
            <p class="text-sm font-medium">{{ auth.session?.user.displayName }}</p>
            <p class="text-xs text-muted">{{ auth.session?.user.username }}</p>
          </div>
          <button class="btn" type="button" @click="logout"><LogOut :size="16" />退出</button>
        </div>
      </div>
    </header>
    <main
      :class="workspace ? 'h-[calc(100vh-48px)] overflow-hidden' : 'mx-auto max-w-[1500px] p-6'"
    >
      <RouterView />
    </main>
  </div>
</template>
