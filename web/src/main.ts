import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from '@/App.vue'
import { router } from '@/router'
import { queryClient } from '@/query/client'
import { installUnauthorizedTeardown } from '@/auth/teardown'
import '@/styles.css'

async function bootstrap(): Promise<void> {
  if (import.meta.env.DEV || import.meta.env.MODE === 'test') {
    const { startDevelopmentMock } = await import('@/mocks/start')
    await startDevelopmentMock()
  }
  const app = createApp(App)
  app.use(createPinia())
  app.use(VueQueryPlugin, { queryClient })
  app.use(router)
  installUnauthorizedTeardown(router)
  await router.isReady()
  app.mount('#app')
}
void bootstrap()
