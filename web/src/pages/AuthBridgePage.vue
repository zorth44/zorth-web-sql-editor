<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { appEnv } from '@/env'
import { clearToken, saveToken } from '@/auth/token-storage'
import { BRIDGE_ACCEPTED, BRIDGE_FAILED, BRIDGE_READY, isBridgePayload } from '@/auth/bridge'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const state = ref<'no-opener' | 'waiting' | 'validating' | 'failed'>('waiting')
let accepted = false

function reply(
  source: MessageEventSource,
  origin: string,
  payload: typeof BRIDGE_ACCEPTED | typeof BRIDGE_FAILED,
) {
  ;(source as Window).postMessage(payload, origin)
}

function announceReady(): void {
  if (!window.opener) {
    state.value = 'no-opener'
    return
  }
  for (const origin of appEnv.bridgeAllowedOrigins) {
    window.opener.postMessage(BRIDGE_READY, origin)
  }
}

async function receive(event: MessageEvent): Promise<void> {
  if (accepted || state.value === 'validating') return
  if (
    event.source !== window.opener ||
    !appEnv.bridgeAllowedOrigins.has(event.origin) ||
    !isBridgePayload(event.data)
  )
    return
  const source = event.source
  if (!source) return
  state.value = 'validating'
  auth.clearAuth()
  saveToken(event.data.token, false)
  try {
    await auth.validateSession({ force: true })
    accepted = true
    reply(source, event.origin, BRIDGE_ACCEPTED)
    await router.replace('/sql-editor')
  } catch {
    clearToken()
    auth.clearAuth()
    reply(source, event.origin, BRIDGE_FAILED)
    state.value = 'failed'
  }
}

onMounted(() => {
  window.addEventListener('message', receive)
  announceReady()
})
onBeforeUnmount(() => window.removeEventListener('message', receive))
</script>
<template>
  <main class="grid min-h-screen place-items-center bg-canvas p-8">
    <section class="panel w-full max-w-lg p-10 text-center" role="status" aria-live="polite">
      <h1 class="text-2xl font-semibold">连接 Bddf SQL Editor</h1>
      <p v-if="state === 'no-opener'" class="mt-4 text-muted">
        未检测到原系统窗口。请从门户首页点击「SQL Editor」，不要直接打开或刷新本页。
      </p>
      <p v-else-if="state === 'waiting'" class="mt-4 text-muted">已通知原系统，正在等待登录凭据…</p>
      <p v-else-if="state === 'validating'" class="mt-4 text-muted">正在验证会话…</p>
      <div v-else class="mt-4">
        <p class="text-danger" role="alert">桥接认证失败，请从原系统重新打开。</p>
        <RouterLink class="btn mt-6" to="/login">返回登录</RouterLink>
      </div>
    </section>
  </main>
</template>
