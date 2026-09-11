export interface CompletionCatalog {
  dataSourceId: string | null
  namespace: string | null
  generation: number
  namespaces: string[]
  tables: string[]
  columnsByTable: Record<string, string[]>
}

export const EMPTY_COMPLETION_CATALOG: CompletionCatalog = {
  dataSourceId: null,
  namespace: null,
  generation: 0,
  namespaces: [],
  tables: [],
  columnsByTable: {},
}

export interface DirectorySnapshot {
  namespaces: string[]
  tables: string[]
}

export class NestedBindingMap<T> {
  private readonly data = new Map<string, Map<string, T>>()

  get(dataSourceId: string, namespace: string): T | undefined {
    return this.data.get(dataSourceId)?.get(namespace)
  }

  set(dataSourceId: string, namespace: string, value: T): void {
    let inner = this.data.get(dataSourceId)
    if (!inner) {
      inner = new Map()
      this.data.set(dataSourceId, inner)
    }
    inner.set(namespace, value)
  }
}

export class ColumnCompletionCache {
  generation = 0
  private columns = new NestedBindingMap<Map<string, string[]>>()
  private inflight = new NestedBindingMap<Map<string, Promise<string[]>>>()

  invalidate(): number {
    this.generation += 1
    this.columns = new NestedBindingMap()
    this.inflight = new NestedBindingMap()
    return this.generation
  }

  get(dataSourceId: string, namespace: string, table: string): string[] | undefined {
    return this.columns.get(dataSourceId, namespace)?.get(table)
  }

  columnsByTable(dataSourceId: string, namespace: string): Record<string, string[]> {
    const inner = this.columns.get(dataSourceId, namespace)
    if (!inner) return {}
    return Object.fromEntries(inner)
  }

  put(
    generation: number,
    dataSourceId: string,
    namespace: string,
    table: string,
    columns: string[],
  ): boolean {
    if (generation !== this.generation) return false
    tableMap(this.columns, dataSourceId, namespace).set(table, columns)
    return true
  }

  resolve(
    generation: number,
    dataSourceId: string,
    namespace: string,
    table: string,
    loader: () => Promise<string[]>,
  ): Promise<string[]> {
    if (generation !== this.generation) return Promise.resolve([])
    const cached = this.get(dataSourceId, namespace, table)
    if (cached) return Promise.resolve(cached)
    const pending = this.inflight.get(dataSourceId, namespace)?.get(table)
    if (pending) return pending
    const request = loader()
      .then((columns) => {
        this.put(generation, dataSourceId, namespace, table, columns)
        return generation === this.generation
          ? (this.get(dataSourceId, namespace, table) ?? [])
          : []
      })
      .catch(() => [] as string[])
      .finally(() => {
        const inflight = this.inflight.get(dataSourceId, namespace)
        if (inflight?.get(table) === request) inflight.delete(table)
      })
    tableMap(this.inflight, dataSourceId, namespace).set(table, request)
    return request
  }
}

function tableMap<T>(
  store: NestedBindingMap<Map<string, T>>,
  dataSourceId: string,
  namespace: string,
): Map<string, T> {
  const existing = store.get(dataSourceId, namespace)
  if (existing) return existing
  const created = new Map<string, T>()
  store.set(dataSourceId, namespace, created)
  return created
}
