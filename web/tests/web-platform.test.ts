import { describe, expect, it } from 'vitest'

describe('binary response test environment', () => {
  const content = '订单,"\'=SUM(1,2)"\r\n'
  const type = 'text/csv;charset=utf-8'

  it.each([
    ['Blob constructor', () => new Blob([content], { type })],
    ['Response.blob()', () => new Response(content, { headers: { 'Content-Type': type } }).blob()],
  ] as const)('%s preserves UTF-8 content and supports binary reads', async (_, createBlob) => {
    const blob = await createBlob()
    const bytes = new TextEncoder().encode(content)

    expect(blob).toBeInstanceOf(Blob)
    expect(blob.type).toBe(type)
    expect(blob.size).toBe(bytes.byteLength)
    expect(await blob.text()).toBe(content)
    expect(Array.from(new Uint8Array(await blob.arrayBuffer()))).toEqual(Array.from(bytes))
    expect(await new Response(blob.stream()).text()).toBe(content)
  })
})
