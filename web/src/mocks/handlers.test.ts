import { describe, expect, it } from 'vitest'
import { createDataSource, testCreateForm, updateDataSource } from '@/api/data-sources'
import { saveToken } from '@/auth/token-storage'
import { emptyDataSourceForm } from '@/data-sources/model'
import { mysqlEngineDescriptor } from '@/mocks/engines'
import { mockDataSources } from '@/mocks/fixtures'
import { queryClient } from '@/query/client'

function secretForm() {
  const base = emptyDataSourceForm(mysqlEngineDescriptor)
  return {
    ...base,
    name: '保密测试库',
    connection: {
      ...base.connection,
      host: 'mysql.internal',
      username: 'tester',
      password: 'must-not-survive',
    },
    properties: { serverTimezone: 'Asia/Shanghai' },
  }
}

describe('data-source MSW credential confidentiality', () => {
  it('never copies request passwords into responses, mock state, or storage', async () => {
    saveToken('mock-token', false)
    const mutation = queryClient.getMutationCache().build(queryClient, {
      mutationFn: () => createDataSource(secretForm(), mysqlEngineDescriptor),
    })
    const created = await mutation.execute(undefined)
    const updated = await updateDataSource(
      created.id,
      secretForm(),
      created.version,
      mysqlEngineDescriptor,
    )
    const tested = await testCreateForm(secretForm(), mysqlEngineDescriptor)
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
