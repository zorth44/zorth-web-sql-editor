<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { KeyRound } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { appEnv } from '@/env'
import { safeRelativeRedirect } from '@/auth/redirect'
import { safeErrorMessage } from '@/api/api-error'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const remember = ref(false)
const pending = ref(false)
const error = ref('')
const hasAccounts = computed(() => auth.accounts.length > 0)
onBeforeUnmount(() => auth.cancelLoginBranch())

async function finishLogin(action: () => Promise<unknown>): Promise<void> {
  pending.value = true
  error.value = ''
  try {
    await action()
    if (auth.session) await router.replace(safeRelativeRedirect(route.query.redirect))
  } catch (reason) {
    error.value = safeErrorMessage(reason, reason instanceof Error ? reason.message : '登录失败')
  } finally {
    pending.value = false
    password.value = ''
    if (!hasAccounts.value) auth.cancelLoginBranch()
  }
}
async function submit(): Promise<void> {
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  await finishLogin(() => auth.submitLogin(username.value.trim(), password.value, remember.value))
}
async function selectAccount(id: string): Promise<void> {
  await finishLogin(() => auth.selectAccount(id))
}
function cancelSelection(): void {
  auth.cancelLoginBranch()
  error.value = ''
  username.value = ''
}
</script>
<template>
  <main class="grid min-h-screen grid-cols-[1.05fr_0.95fr] bg-panel">
    <section class="flex flex-col justify-between bg-slate-950 p-14 text-white">
      <div class="flex items-center gap-3 text-xl font-semibold">
        <span class="grid h-10 w-10 place-items-center rounded-xl bg-brand-fill"
          ><KeyRound :size="22" /></span
        >Zorth SQL Editor
      </div>
      <div>
        <p class="mb-4 text-sm font-semibold uppercase tracking-[0.24em] text-teal-300">
          Phase Two
        </p>
        <h1 class="max-w-xl text-5xl font-semibold leading-tight">
          浏览、执行与回看<br />你的数据库工作
        </h1>
        <p class="mt-6 max-w-lg text-lg leading-8 text-slate-300">
          在当前产品可见的数据源上编写 SQL、查看结果、导出 CSV，并回看自己的执行历史。
        </p>
      </div>
      <p class="text-sm text-slate-500">面向桌面的企业数据工作台</p>
    </section>
    <section class="relative grid place-items-center p-12">
      <div class="absolute right-6 top-6">
        <ThemeToggle />
      </div>
      <div class="w-full max-w-md">
        <template v-if="hasAccounts"
          ><h1 class="text-3xl font-semibold">选择登录账号</h1>
          <p class="mt-2 text-muted">此 LDAP 身份关联了多个账号，请选择本次使用的账号。</p>
          <div class="mt-7 space-y-3">
            <button
              v-for="account in auth.accounts"
              :key="account.id"
              class="panel flex w-full items-center justify-between p-4 text-left hover:border-brand"
              type="button"
              :disabled="pending"
              @click="selectAccount(account.id)"
            >
              <span
                ><strong class="block">{{ account.displayName }}</strong
                ><span class="text-sm text-muted">{{ account.username }}</span></span
              ><span aria-hidden="true">→</span>
            </button>
          </div>
          <button class="btn mt-5" type="button" :disabled="pending" @click="cancelSelection">
            返回重新登录
          </button></template
        ><template v-else-if="auth.bindingRequired"
          ><h1 class="text-3xl font-semibold">需要绑定账号</h1>
          <p class="mt-3 leading-7 text-muted">
            请先前往原系统完成本地账号绑定，然后返回这里重试。
          </p>
          <a class="btn-primary mt-7" :href="appEnv.legacyPortalUrl">前往原系统绑定</a
          ><button class="btn ml-3 mt-7" type="button" @click="auth.cancelLoginBranch">
            返回
          </button></template
        >
        <form v-else @submit.prevent="submit">
          <h1 class="text-3xl font-semibold">欢迎回来</h1>
          <p class="mt-2 text-muted">使用 EHR / LDAP 账号登录</p>
          <div class="mt-8">
            <label class="label" for="username">用户名</label
            ><input
              id="username"
              v-model="username"
              class="field"
              autocomplete="username"
              maxlength="128"
              required
            />
          </div>
          <div class="mt-5">
            <label class="label" for="password">密码</label
            ><input
              id="password"
              v-model="password"
              class="field"
              type="password"
              autocomplete="current-password"
              required
            />
          </div>
          <label class="mt-5 flex cursor-pointer items-center gap-2 text-sm"
            ><input v-model="remember" type="checkbox" />在此设备上记住我</label
          >
          <p
            v-if="error"
            class="mt-5 rounded-lg bg-danger-soft p-3 text-sm text-danger"
            role="alert"
          >
            {{ error }}
          </p>
          <button class="btn-primary mt-7 w-full" type="submit" :disabled="pending">
            {{ pending ? '正在验证…' : '登录' }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>
