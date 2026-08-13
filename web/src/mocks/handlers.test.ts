import { describe, expect, it } from 'vitest'
import { createDataSource, testCreateForm, updateDataSource } from '@/api/data-sources'
import { saveToken } from '@/auth/token-storage'
import { emptyDataSourceForm } from '@/data-sources/model'
import { mockDataSources } from '@/mocks/fixtures'
import { queryClient } from '@/query/client'

function secretForm() {
  return {
    ...emptyDataSourceForm(),
    name: '保密测试库',
    host: 'mysql.internal',
    username: 'tester',
    password: 'must-not-survive',
    properties: { serverTimezone: 'Asia/Shanghai' },
  }
}

describe('data-source MSW credential confidentiality', () => {
  it('never copies request passwords into responses, mock state, or storage', async () => {
    saveToken('mock-token', false)
    const mutation = queryClient.getMutationCache().build(queryClient, {
      mutationFn: () => createDataSource(secretForm()),
    })
    const created = await mutation.execute(undefined)
    const updated = await updateDataSource(created.id, secretForm(), created.version)
    const tested = await testCreateForm(secretForm())
    const observable = JSON.stringify({
      created,
      updated,
      tested,
      mutation: mutation.state,
      state: mockDataSources(),
    })

    expect(observable).not.toContain('must-not-survive')
    expect(observable).not.toContain('"password"')
    expect(localStorage.getItem('must-not-survive')).toBeNull()
    expect(sessionStorage.getItem('must-not-survive')).toBeNull()
    queryClient.clear()
  })
})
