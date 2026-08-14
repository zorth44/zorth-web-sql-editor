<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { appEnv } from '@/env'
import { clearToken, saveToken } from '@/auth/token-storage'
import { isBridgePayload } from '@/auth/bridge'
import { useAuthStore } from '@/stores/auth'
const router = useRouter()
const auth = useAuthStore()
const state = ref<'waiting' | 'validating' | 'failed'>('waiting')

async function receive(event: MessageEvent): Promise<void> {
  if (
    event.source !== window.opener ||
    !appEnv.bridgeAllowedOrigins.has(event.origin) ||
    !isBridgePayload(event.data)
  )
    return
  const source = event.source
  if (!source) return
  state.value = 'validating'
  saveToken(event.data.token, false)
  try {
    await auth.validateSession()
    source.postMessage(
      { type: 'ZORTH_SQL_AUTH_ACCEPTED', version: 1 },
      { targetOrigin: event.origin },
    )
    await router.replace('/sql-editor')
  } catch {
    clearToken()
    auth.clearAuth()
    state.value = 'failed'
  }
}
onMounted(() => window.addEventListener('message', receive))
onBeforeUnmount(() => window.removeEventListener('message', receive))
</script>
<template>
  <main class="grid min-h-screen place-items-center bg-canvas p-8">
    <section class="panel w-full max-w-lg p-10 text-center" role="status" aria-live="polite">
      <h1 class="text-2xl font-semibold">连接 Zorth SQL Editor</h1>
      <p v-if="state === 'waiting'" class="mt-4 text-muted">正在等待原系统发送登录凭据…</p>
      <p v-else-if="state === 'validating'" class="mt-4 text-muted">正在验证会话…</p>
      <div v-else class="mt-4">
        <p class="text-danger" role="alert">桥接认证失败，请从原系统重新打开。</p>
        <RouterLink class="btn mt-6" to="/login">返回登录</RouterLink>
      </div>
    </section>
  </main>
</template>
