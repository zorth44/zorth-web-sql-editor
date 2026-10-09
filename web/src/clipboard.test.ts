import { afterEach, describe, expect, it, vi } from 'vitest'
import { copyText } from '@/clipboard'

function setClipboard(value: unknown): void {
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value })
}

function setExecCommand(value: unknown): void {
  Object.defineProperty(document, 'execCommand', { configurable: true, value })
}

describe('copyText', () => {
  afterEach(() => {
    setClipboard(undefined)
    setExecCommand(undefined)
  })

  it('uses the async clipboard when the page has one', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    setClipboard({ writeText })
    await expect(copyText('hello')).resolves.toBe(true)
    expect(writeText).toHaveBeenCalledWith('hello')
  })

  it('copies through a temporary selection when the page has no async clipboard', async () => {
    setClipboard(undefined)
    const execCommand = vi.fn().mockReturnValue(true)
    setExecCommand(execCommand)
    await expect(copyText('hello')).resolves.toBe(true)
    expect(execCommand).toHaveBeenCalledWith('copy')
    expect(document.querySelector('textarea')).toBeNull()
  })

  it('falls back to the selection copy when the async clipboard rejects', async () => {
    setClipboard({ writeText: vi.fn().mockRejectedValue(new Error('NotAllowedError')) })
    const execCommand = vi.fn().mockReturnValue(true)
    setExecCommand(execCommand)
    await expect(copyText('hello')).resolves.toBe(true)
    expect(execCommand).toHaveBeenCalledWith('copy')
  })

  it('reports failure when neither path can copy', async () => {
    setClipboard(undefined)
    setExecCommand(() => {
      throw new Error('not implemented')
    })
    await expect(copyText('hello')).resolves.toBe(false)
    expect(document.querySelector('textarea')).toBeNull()
  })
})
