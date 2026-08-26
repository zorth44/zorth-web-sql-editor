import { describe, expect, it } from 'vitest'
import { consumeSse } from '@/sql-editor/sse'

describe('consumeSse', () => {
  it('parses named events across chunk boundaries', () => {
    const first = consumeSse('event:start\ndata:{"type":"start"}\n\nevent:del')
    expect(first.frames).toEqual([{ event: 'start', data: '{"type":"start"}' }])
    const second = consumeSse(`${first.rest}ta\ndata:{"type":"delta","content":"Hi"}\n\n`)
    expect(second.frames).toEqual([{ event: 'delta', data: '{"type":"delta","content":"Hi"}' }])
    expect(second.rest).toBe('')
  })

  it('ignores comment lines', () => {
    const result = consumeSse(': keep-alive\n\nevent:completed\ndata:{"type":"completed"}\n\n')
    expect(result.frames).toEqual([{ event: 'completed', data: '{"type":"completed"}' }])
  })
})
