import type { Router } from 'vue-router'
import { setUnauthorizedHandler } from '@/api/sql-client'
import { safeRelativeRedirect } from '@/auth/redirect'
import { useAuthStore } from '@/stores/auth'

let redirecting = false
export function installUnauthorizedTeardown(router: Router): void {
  setUnauthorizedHandler(async () => {
    if (redirecting) return
    redirecting = true
    const current = safeRelativeRedirect(router.currentRoute.value.fullPath)
    useAuthStore().clearAuth()
    try {
      await router.replace({
        path: '/login',
        query: current === '/data-sources' ? {} : { redirect: current },
      })
    } finally {
      redirecting = false
    }
  })
}
