import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { appEnv } from '@/env'
import { saveToken } from '@/auth/token-storage'
import { server } from '@/mocks/server'
import { listAllDatabases, listAllTables, listDatabases } from '@/api/metadata'

beforeEach(() => saveToken('mock-token', false))

describe('metadata paging', () => {
  it('requests databases at the backend page-size cap', async () => {
    let pageSize = ''
    server.use(
      http.get(`${appEnv.sqlApiBase}/api/v1/data-sources/:id/databases`, ({ request }) => {
        pageSize = new URL(request.url).searchParams.get('pageSize') || ''
        return HttpResponse.json({ items: [], nextPageToken: null })
      }),
    )
    await listDatabases('ds-orders-a')
    expect(pageSize).toBe('200')
  })

  it('follows database nextPageToken until the catalog is complete', async () => {
    const seenTokens: Array<string | null> = []
    server.use(
      http.get(`${appEnv.sqlApiBase}/api/v1/data-sources/:id/databases`, ({ request }) => {
        const token = new URL(request.url).searchParams.get('pageToken')
        seenTokens.push(token)
        if (!token) {
          return HttpResponse.json({
            items: [{ name: 'alpha', kind: 'NAMESPACE' }],
            nextPageToken: 'page-2',
          })
        }
        return HttpResponse.json({
          items: [{ name: 'omega', kind: 'NAMESPACE' }],
          nextPageToken: null,
        })
      }),
    )
    expect((await listAllDatabases('ds-orders-a')).map((item) => item.name)).toEqual([
      'alpha',
      'omega',
    ])
    expect(seenTokens).toEqual([null, 'page-2'])
  })

  it('follows table nextPageToken until the catalog is complete', async () => {
    server.use(
      http.get(`${appEnv.sqlApiBase}/api/v1/data-sources/:id/tables`, ({ request }) => {
        const token = new URL(request.url).searchParams.get('pageToken')
        if (!token) {
          return HttpResponse.json({
            items: [{ database: 'orders', name: 'first_table', type: 'TABLE' }],
            nextPageToken: 'page-2',
          })
        }
        return HttpResponse.json({
          items: [{ database: 'orders', name: 'last_table', type: 'TABLE' }],
          nextPageToken: null,
        })
      }),
    )
    expect((await listAllTables('ds-orders-a', 'orders')).map((item) => item.name)).toEqual([
      'first_table',
      'last_table',
    ])
  })

  it('stops when a page token repeats', async () => {
    server.use(
      http.get(`${appEnv.sqlApiBase}/api/v1/data-sources/:id/databases`, () =>
        HttpResponse.json({
          items: [{ name: 'loop-db', kind: 'NAMESPACE' }],
          nextPageToken: 'same',
        }),
      ),
    )
    expect((await listAllDatabases('ds-orders-a')).map((item) => item.name)).toEqual([
      'loop-db',
      'loop-db',
    ])
  })
})
