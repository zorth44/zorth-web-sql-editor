import { describe, expect, it, vi } from 'vitest'
import { isAbortError } from '@/api/api-error'
import {
  canPickSaveFile,
  pickExportWritable,
  suggestedExportFilename,
} from '@/sql-editor/csv-export'

describe('csv export helpers', () => {
  it('sanitizes suggested download names', () => {
    expect(suggestedExportFilename('my source', 'public schema')).toBe(
      'my-source-public-schema.csv',
    )
    expect(suggestedExportFilename('orders', null)).toBe('orders-database.csv')
    expect(suggestedExportFilename('a/b', 'x y')).toBe('a-b-x-y.csv')
    expect(suggestedExportFilename('', null)).toBe('export-database.csv')
  })

  it('detects File System Access support', () => {
    expect(canPickSaveFile({})).toBe(false)
    expect(canPickSaveFile({ showSaveFilePicker: vi.fn() })).toBe(true)
  })

  it('treats picker abort as cancel without throwing', async () => {
    const picker = vi.fn().mockRejectedValue(new DOMException('cancel', 'AbortError'))
    await expect(
      pickExportWritable({ showSaveFilePicker: picker }, 'orders-orders.csv'),
    ).resolves.toBeNull()
    expect(picker).toHaveBeenCalledWith(
      expect.objectContaining({ suggestedName: 'orders-orders.csv' }),
    )
  })

  it('returns the writable after the user picks a file', async () => {
    const writable = new WritableStream<Uint8Array>()
    const picker = vi.fn().mockResolvedValue({
      createWritable: async () => writable,
    })
    await expect(pickExportWritable({ showSaveFilePicker: picker }, 'a.csv')).resolves.toBe(
      writable,
    )
  })

  it('recognizes abort errors from the picker', () => {
    expect(isAbortError(new DOMException('The user aborted a request.', 'AbortError'))).toBe(true)
  })
})
