import { describe, expect, it } from 'vitest'
import { login } from '@/api/auth-client'

describe('authorization response projection', () => {
  it('projects a token branch and drops raw legacy password fields', async () => {
    const result = await login({ username: 'normal', password: 'ldap-secret' })
    expect(result).toEqual({ kind: 'authenticated', token: 'mock-token' })
    const serialized = JSON.stringify(result)
    expect(serialized).not.toContain('ldapUser')
    expect(serialized).not.toContain('pwd')
    expect(serialized).not.toContain('sensitive')
    expect(JSON.stringify(localStorage)).not.toContain('ldap-secret')
  })
  it('sanitizes account selection and binding branches', async () => {
    expect(await login({ username: 'multi', password: 'secret' })).toEqual({
      kind: 'select-account',
      accounts: [
        { id: '1001', username: 'zhangsan', displayName: '张三' },
        { id: '1002', username: 'lisi', displayName: '李四' },
      ],
    })
    expect(await login({ username: 'bind', password: 'secret' })).toEqual({
      kind: 'binding-required',
    })
  })
  it('treats AjaxResult business code as failure', async () => {
    await expect(login({ username: 'failed', password: 'secret' })).rejects.toThrow(
      '用户名或密码错误',
    )
  })
})
