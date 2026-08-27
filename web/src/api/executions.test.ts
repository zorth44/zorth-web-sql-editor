import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { saveToken } from '@/auth/token-storage'
import { exportExecution, executeSql } from '@/api/executions'
import { appEnv } from '@/env'
import { server } from '@/mocks/server'

describe('exportExecution', () => {
  it('keeps the CSV as a Blob when no writable is provided', async () => {
    saveToken('mock-token', false)
    const executionId = crypto.randomUUID()
    await executeSql({
      executionId,
      dataSourceId: 'ds-orders-a',
      database: 'orders',
      statement: 'select 1',
    })
    const csv = await exportExecution(executionId, 100)
    expect(csv.filename).toBe('mock-orders.csv')
    expect(csv.blob).toBeTruthy()
    expect(await csv.blob?.text()).toContain("'=SUM")
  })

  it('writes response chunks to a writable without assembling a Blob', async () => {
    saveToken('mock-token', false)
    const executionId = crypto.randomUUID()
    await executeSql({
      executionId,
      dataSourceId: 'ds-orders-a',
      database: 'orders',
      statement: 'select 1',
    })
    const chunks: Uint8Array[] = []
    const writable = new WritableStream<Uint8Array>({
      write(chunk) {
        chunks.push(chunk)
      },
    })
    const csv = await exportExecution(executionId, 100, undefined, writable)
    expect(csv.blob).toBeNull()
    const text = new TextDecoder().decode(
      chunks.reduce((all, chunk) => {
        const next = new Uint8Array(all.length + chunk.length)
        next.set(all)
        next.set(chunk, all.length)
        return next
      }, new Uint8Array()),
    )
    expect(text).toContain("'=SUM")
  })

  it('aborts an unused writable when the request is cancelled', async () => {
    saveToken('mock-token', false)
    server.use(
      http.post(`${appEnv.sqlApiBase}/api/v1/sql/exports`, async () => {
        await delay('infinite')
        return new HttpResponse('never')
      }),
    )
    const abort = new AbortController()
    let aborted = false
    const writable = new WritableStream<Uint8Array>({
      abort() {
        aborted = true
      },
    })
    const pending = exportExecution('exec-1', 100, abort.signal, writable)
    abort.abort()
    await expect(pending).rejects.toBeTruthy()
    expect(aborted).toBe(true)
  })

  it('aborts the writable if the download is cancelled while writing', async () => {
    saveToken('mock-token', false)
    const abort = new AbortController()
    let aborted = false
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('partial'))
      },
    })
    server.use(
      http.post(
        `${appEnv.sqlApiBase}/api/v1/sql/exports`,
        () =>
          new HttpResponse(stream, {
            headers: {
              'Content-Type': 'text/csv',
              'Content-Disposition': 'attachment; filename="x.csv"',
            },
          }),
      ),
    )
    const writable = new WritableStream<Uint8Array>({
      write() {
        abort.abort()
      },
      abort() {
        aborted = true
      },
    })
    await expect(exportExecution('x', 100, abort.signal, writable)).rejects.toBeTruthy()
    expect(aborted).toBe(true)
  })
})
