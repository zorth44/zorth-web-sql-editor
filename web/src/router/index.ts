import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { getToken } from '@/auth/token-storage'
import { safeRelativeRedirect } from '@/auth/redirect'
import { useAuthStore } from '@/stores/auth'
import AppShell from '@/layouts/AppShell.vue'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/pages/LoginPage.vue'),
      meta: { public: true },
    },
    {
      path: '/auth/bridge',
      name: 'auth-bridge',
      component: () => import('@/pages/AuthBridgePage.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/data-sources' },
        {
          path: 'data-sources',
          name: 'data-sources',
          component: () => import('@/pages/DataSourcesPage.vue'),
        },
        {
          path: 'data-sources/new',
          name: 'data-source-new',
          component: () => import('@/pages/DataSourceFormPage.vue'),
        },
        {
          path: 'data-sources/:id/edit',
          name: 'data-source-edit',
          component: () => import('@/pages/DataSourceFormPage.vue'),
          props: true,
        },
        { path: 'sql-editor', redirect: '/data-sources' },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/pages/NotFoundPage.vue'),
      meta: { public: true },
    },
  ],
})

function loginRedirect(to: RouteLocationNormalized) {
  return { path: '/login', query: { redirect: safeRelativeRedirect(to.fullPath) } }
}
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (to.name === 'login' && getToken()) {
      try {
        await auth.validateSession()
        return safeRelativeRedirect(to.query.redirect)
      } catch {
        auth.clearAuth()
      }
    }
    return true
  }
  if (!getToken()) return loginRedirect(to)
  try {
    if (!auth.session) await auth.validateSession()
    return true
  } catch {
    auth.clearAuth()
    return loginRedirect(to)
  }
})
