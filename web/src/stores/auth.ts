import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginRequest, logoutRemote } from '@/api/auth-client'
import { fetchSession, isSessionValid } from '@/api/session'
import { clearToken, getToken, saveToken } from '@/auth/token-storage'
import {
  clearCredentials,
  consumeCredentials,
  retainCredentials,
} from '@/auth/transient-credentials'
import { queryClient, queryKeys } from '@/query/client'
import type { BoundAccount, Session } from '@/types/contracts'
import { useEditorStore } from '@/stores/editor'
import { useCopilotStore } from '@/stores/copilot'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<Session | null>(null)
  const accounts = ref<BoundAccount[]>([])
  const remember = ref(false)
  const bindingRequired = ref(false)

  async function validateSession(): Promise<Session> {
    const value = await queryClient.fetchQuery({
      queryKey: queryKeys.session,
      queryFn: fetchSession,
      staleTime: 5 * 60_000,
    })
    if (!isSessionValid(value)) throw new Error('登录已过期，请重新登录')
    session.value = value
    return value
  }

  async function submitLogin(
    username: string,
    password: string,
    shouldRemember: boolean,
    selectUserId?: string,
  ): Promise<'authenticated' | 'select-account' | 'binding-required'> {
    remember.value = shouldRemember
    const result = await loginRequest({
      username,
      password,
      ...(selectUserId ? { selectUserId } : {}),
    })
    if (result.kind === 'select-account') {
      retainCredentials(username, password)
      accounts.value = result.accounts
      return result.kind
    }
    clearCredentials()
    accounts.value = []
    if (result.kind === 'binding-required') {
      bindingRequired.value = true
      return result.kind
    }
    saveToken(result.token, shouldRemember)
    try {
      await validateSession()
    } catch (error) {
      clearToken()
      throw error
    }
    return result.kind
  }

  async function selectAccount(id: string): Promise<void> {
    const credentials = consumeCredentials()
    if (!credentials) throw new Error('登录凭据已失效，请重新登录')
    await submitLogin(credentials.username, credentials.password, remember.value, id)
  }

  function clearAuth(): void {
    clearToken()
    clearCredentials()
    session.value = null
    accounts.value = []
    bindingRequired.value = false
    useEditorStore().clearAll()
    useCopilotStore().reset()
    queryClient.clear()
  }

  async function logout(): Promise<void> {
    try {
      await logoutRemote()
    } catch {
      /* 本地退出不依赖远端成功 */
    } finally {
      clearAuth()
    }
  }

  function cancelLoginBranch(): void {
    clearCredentials()
    accounts.value = []
    bindingRequired.value = false
  }
  return {
    session,
    accounts,
    remember,
    bindingRequired,
    validateSession,
    submitLogin,
    selectAccount,
    clearAuth,
    logout,
    cancelLoginBranch,
    hasToken: () => Boolean(getToken()),
  }
})
