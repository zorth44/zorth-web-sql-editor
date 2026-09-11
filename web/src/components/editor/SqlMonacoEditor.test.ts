import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { CompletionCatalog } from '@/sql-editor/completion-catalog'

vi.mock('@/components/editor/monaco-env', () => ({}))
vi.mock('monaco-editor/features/register.all', () => ({}))
vi.mock('monaco-editor/languages/definitions/mysql/register', () => ({}))
vi.mock('monaco-editor/languages/definitions/pgsql/register', () => ({}))

const fake = {
  value: 'select 1;\nselect 2;',
  selection: '',
  cursorOffset: 0,
  createdLanguage: 'mysql',
  selectionListener: (() => {}) as () => void,
  provider: undefined as
    | {
        triggerCharacters?: string[]
        provideCompletionItems: (
          model: unknown,
          position: { lineNumber: number; column: number },
        ) => Promise<{
          suggestions: { label: string; kind: number; insertText: string; range: unknown }[]
        }>
      }
    | undefined,
}

function offsetOf(
  value: string,
  marker = '|',
): { text: string; offset: number; position: { lineNumber: number; column: number } } {
  const offset = value.indexOf(marker)
  const text = value.replace(marker, '')
  const before = text.slice(0, offset)
  const lines = before.split('\n')
  return {
    text,
    offset,
    position: { lineNumber: lines.length, column: (lines.at(-1)?.length ?? 0) + 1 },
  }
}

vi.mock('monaco-editor/editor', () => ({
  KeyMod: { CtrlCmd: 1 },
  KeyCode: { KeyS: 32 },
  languages: {
    CompletionItemKind: { Field: 3, Variable: 4, Class: 5, Module: 8 },
    registerCompletionItemProvider: (_language: string, provider: (typeof fake)['provider']) => {
      fake.provider = provider
      return { dispose: () => {} }
    },
  },
  editor: {
    setTheme: () => {},
    setModelLanguage: (model: { language?: string }, language: string) => {
      if (model) model.language = language
      fake.createdLanguage = language
    },
    create: (_el: unknown, options: { language?: string }) => {
      fake.createdLanguage = options.language || 'mysql'
      return {
        getValue: () => fake.value,
        setValue: (next: string) => {
          fake.value = next
        },
        getModel: () => ({
          getValue: () => fake.value,
          getValueInRange: () => fake.selection,
          getOffsetAt: (position: { lineNumber: number; column: number }) => {
            const lines = fake.value.split('\n')
            let offset = 0
            for (let index = 0; index < position.lineNumber - 1; index += 1) {
              offset += (lines[index]?.length ?? 0) + 1
            }
            return offset + position.column - 1
          },
          getPositionAt: (offset: number) => {
            const before = fake.value.slice(0, offset)
            const lines = before.split('\n')
            return { lineNumber: lines.length, column: (lines.at(-1)?.length ?? 0) + 1 }
          },
          getWordUntilPosition: (position: { column: number }) => ({
            startColumn: position.column,
            endColumn: position.column,
          }),
          language: fake.createdLanguage,
        }),
        getSelection: () => ({}),
        getPosition: () => ({ lineNumber: 1, column: 1 }),
        onDidChangeModelContent: () => ({ dispose: () => {} }),
        onDidChangeCursorSelection: (listener: () => void) => {
          fake.selectionListener = listener
          return { dispose: () => {} }
        },
        onKeyDown: () => ({ dispose: () => {} }),
        executeEdits: () => true,
        focus: () => {},
        dispose: () => {},
      }
    },
  },
}))

function fakeModel() {
  const lines = () => fake.value.split('\n')
  return {
    getValue: () => fake.value,
    getOffsetAt: (position: { lineNumber: number; column: number }) => {
      let offset = 0
      for (let index = 0; index < position.lineNumber - 1; index += 1) {
        offset += (lines()[index]?.length ?? 0) + 1
      }
      return offset + position.column - 1
    },
    getPositionAt: (offset: number) => {
      const before = fake.value.slice(0, offset)
      const parts = before.split('\n')
      return { lineNumber: parts.length, column: (parts.at(-1)?.length ?? 0) + 1 }
    },
    getWordUntilPosition: (position: { column: number }) => ({
      startColumn: position.column,
      endColumn: position.column,
    }),
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  fake.value = 'select 1;\nselect 2;'
  fake.selection = ''
  fake.cursorOffset = 0
  fake.createdLanguage = 'mysql'
  fake.provider = undefined
})

const catalog: CompletionCatalog = {
  dataSourceId: 'ds-orders-a',
  namespace: 'orders',
  generation: 1,
  namespaces: ['orders', 'analytics'],
  tables: ['order_item', 'order_view'],
  columnsByTable: { order_item: ['id', 'amount'] },
}

async function render(props?: {
  language?: string
  catalog?: CompletionCatalog
  identifierQuote?: string
  resolveColumns?: (table: string) => Promise<string[]>
}) {
  const SqlMonacoEditor = (await import('@/components/editor/SqlMonacoEditor.vue')).default
  return mount(SqlMonacoEditor, {
    props: {
      modelValue: fake.value,
      catalog,
      identifierQuote: '`',
      ...props,
    },
    attachTo: document.body,
  })
}

describe('sql monaco editor', () => {
  it('returns the statement at the cursor for the run target', async () => {
    const wrapper = await render()
    expect(wrapper.vm.getRunnableStatement()).toBe('select 1')
    wrapper.unmount()
  })

  it('returns the whole editor text as the script run target', async () => {
    const wrapper = await render()
    expect(wrapper.vm.getRunnableScript()).toBe('select 1;\nselect 2;')
    wrapper.unmount()
  })

  it('uses the selection as the run target when there is one', async () => {
    const wrapper = await render()
    fake.selection = 'select 2'
    expect(wrapper.vm.getRunnableScript()).toBe('select 2')
    expect(wrapper.vm.getRunnableStatement()).toBe('select 2')
    wrapper.unmount()
  })

  it('reports whether a selection exists so the run button can retitle itself', async () => {
    const wrapper = await render()
    fake.selection = 'select 2'
    fake.selectionListener()
    expect(wrapper.emitted('update:hasSelection')?.at(-1)).toEqual([true])
    fake.selection = ''
    fake.selectionListener()
    expect(wrapper.emitted('update:hasSelection')?.at(-1)).toEqual([false])
    wrapper.unmount()
  })

  it('creates Monaco as mysql for MYSQL, unbound tabs, and unknown languages', async () => {
    const mysql = await render({ language: 'mysql' })
    expect(fake.createdLanguage).toBe('mysql')
    mysql.unmount()
    const unbound = await render()
    expect(fake.createdLanguage).toBe('mysql')
    unbound.unmount()
    const unknown = await render({ language: 'hive' })
    expect(fake.createdLanguage).toBe('mysql')
    unknown.unmount()
  })

  it('creates Monaco as pgsql for PostgreSQL', async () => {
    const wrapper = await render({ language: 'pgsql' })
    expect(fake.createdLanguage).toBe('pgsql')
    wrapper.unmount()
  })

  it('appends and replaces sql used by Copilot', async () => {
    const wrapper = await render()
    fake.value = 'select 1;'
    wrapper.vm.appendSql('select 2;')
    expect(fake.value).toBe('select 1;\n\nselect 2;\n')
    fake.value = 'select * from mock_error;'
    expect(wrapper.vm.replaceSql('select * from mock_error;', 'select 1')).toBe(true)
    expect(fake.value).toBe('select 1')
    wrapper.unmount()
  })

  it('registers dot as a trigger and offers only columns after a table name', async () => {
    const wrapper = await render()
    expect(fake.provider?.triggerCharacters).toEqual(['.'])
    const caret = offsetOf('select * from order_item.|')
    fake.value = caret.text
    const result = await fake.provider!.provideCompletionItems(fakeModel(), caret.position)
    expect(result.suggestions.map((item) => item.label)).toEqual(['id', 'amount'])
    expect(result.suggestions.every((item) => item.kind === 3)).toBe(true)
    wrapper.unmount()
  })

  it('offers quoted-table columns and filters by prefix after the dot', async () => {
    const wrapper = await render()
    const quoted = offsetOf('select * from `order_item`.|')
    fake.value = quoted.text
    const quotedResult = await fake.provider!.provideCompletionItems(fakeModel(), quoted.position)
    expect(quotedResult.suggestions.map((item) => item.label)).toEqual(['id', 'amount'])
    const prefix = offsetOf('select * from order_item.am|')
    fake.value = prefix.text
    const prefixResult = await fake.provider!.provideCompletionItems(fakeModel(), prefix.position)
    expect(prefixResult.suggestions.map((item) => item.label)).toEqual(['amount'])
    wrapper.unmount()
  })

  it('does not complete inside strings or comments', async () => {
    const wrapper = await render()
    for (const sql of [
      "select 'order_item.|",
      'select 1 -- order_item.|',
      'select 1 /* order_item.|',
    ]) {
      const caret = offsetOf(sql)
      fake.value = caret.text
      const result = await fake.provider!.provideCompletionItems(fakeModel(), caret.position)
      expect(result.suggestions).toEqual([])
    }
    wrapper.unmount()
  })

  it('quotes unsafe column insert text and falls back to unqualified metadata', async () => {
    const resolveColumns = vi.fn(async () => ['id', 'odd name', 'ord`ers'])
    const wrapper = await render({
      catalog: { ...catalog, columnsByTable: {} },
      resolveColumns,
    })
    const qualified = offsetOf('select * from order_item.|')
    fake.value = qualified.text
    const columns = await fake.provider!.provideCompletionItems(fakeModel(), qualified.position)
    expect(resolveColumns).toHaveBeenCalledWith('order_item')
    expect(columns.suggestions.map((item) => item.insertText)).toEqual([
      'id',
      '`odd name`',
      '`ord``ers`',
    ])
    const unbound = offsetOf('select * from |')
    fake.value = unbound.text
    const fallback = await fake.provider!.provideCompletionItems(fakeModel(), unbound.position)
    expect(fallback.suggestions.map((item) => item.label)).toEqual([
      'orders',
      'analytics',
      'order_item',
      'order_view',
    ])
    expect(fallback.suggestions.map((item) => item.kind)).toEqual([8, 8, 5, 5])
    wrapper.unmount()
  })
})
