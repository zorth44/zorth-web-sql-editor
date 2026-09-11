import { describe, expect, it } from 'vitest'
import { ColumnCompletionCache, NestedBindingMap } from '@/sql-editor/completion-catalog'

describe('nested binding map', () => {
  it('keeps identities with colons and dots from colliding', () => {
    const store = new NestedBindingMap<string>()
    store.set('ds:a', 'sales.raw', 'one')
    store.set('ds', 'a:sales.raw', 'two')
    expect(store.get('ds:a', 'sales.raw')).toBe('one')
    expect(store.get('ds', 'a:sales.raw')).toBe('two')
  })
})

describe('column completion cache', () => {
  it('coalesces in-flight loads for the same binding and table', async () => {
    const cache = new ColumnCompletionCache()
    let loads = 0
    let finish!: (value: string[]) => void
    const loader = () => {
      loads += 1
      return new Promise<string[]>((resolve) => {
        finish = resolve
      })
    }
    const first = cache.resolve(cache.generation, 'ds', 'sales', 'orders', loader)
    const second = cache.resolve(cache.generation, 'ds', 'sales', 'orders', loader)
    expect(loads).toBe(1)
    finish(['id', 'amount'])
    await expect(first).resolves.toEqual(['id', 'amount'])
    await expect(second).resolves.toEqual(['id', 'amount'])
    await expect(cache.resolve(cache.generation, 'ds', 'sales', 'orders', loader)).resolves.toEqual(
      ['id', 'amount'],
    )
    expect(loads).toBe(1)
  })

  it('drops in-flight results after invalidation', async () => {
    const cache = new ColumnCompletionCache()
    let finish!: (value: string[]) => void
    const pending = cache.resolve(cache.generation, 'ds', 'sales', 'orders', () => {
      return new Promise<string[]>((resolve) => {
        finish = resolve
      })
    })
    const generation = cache.invalidate()
    finish(['stale'])
    await expect(pending).resolves.toEqual([])
    expect(cache.get('ds', 'sales', 'orders')).toBeUndefined()
    expect(generation).toBe(1)
  })

  it('does not publish a late put from an older generation', () => {
    const cache = new ColumnCompletionCache()
    const old = cache.generation
    cache.invalidate()
    expect(cache.put(old, 'ds', 'sales', 'orders', ['stale'])).toBe(false)
    expect(cache.get('ds', 'sales', 'orders')).toBeUndefined()
  })

  it('isolates columns by data source and namespace', async () => {
    const cache = new ColumnCompletionCache()
    await cache.resolve(cache.generation, 'ds-a', 'sales', 'orders', async () => ['a'])
    await cache.resolve(cache.generation, 'ds-b', 'sales', 'orders', async () => ['b'])
    expect(cache.columnsByTable('ds-a', 'sales')).toEqual({ orders: ['a'] })
    expect(cache.columnsByTable('ds-b', 'sales')).toEqual({ orders: ['b'] })
  })

  it('returns an empty list when the loader fails', async () => {
    const cache = new ColumnCompletionCache()
    await expect(
      cache.resolve(cache.generation, 'ds', 'sales', 'orders', async () => {
        throw new Error('nope')
      }),
    ).resolves.toEqual([])
  })
})
