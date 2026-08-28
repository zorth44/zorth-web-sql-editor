import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

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
}

vi.mock('monaco-editor/editor', () => ({
  KeyMod: { CtrlCmd: 1 },
  KeyCode: { KeyS: 32 },
  languages: {
    CompletionItemKind: { Field: 3 },
    registerCompletionItemProvider: () => ({ dispose: () => {} }),
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
          getOffsetAt: () => fake.cursorOffset,
          getWordUntilPosition: () => ({ startColumn: 1, endColumn: 1 }),
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

beforeEach(() => {
  setActivePinia(createPinia())
  fake.value = 'select 1;\nselect 2;'
  fake.selection = ''
  fake.cursorOffset = 0
  fake.createdLanguage = 'mysql'
})

async function render(language?: string) {
  const SqlMonacoEditor = (await import('@/components/editor/SqlMonacoEditor.vue')).default
  return mount(SqlMonacoEditor, {
    props: language ? { modelValue: fake.value, language } : { modelValue: fake.value },
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
    const mysql = await render('mysql')
    expect(fake.createdLanguage).toBe('mysql')
    mysql.unmount()
    const unbound = await render()
    expect(fake.createdLanguage).toBe('mysql')
    unbound.unmount()
    const unknown = await render('hive')
    expect(fake.createdLanguage).toBe('mysql')
    unknown.unmount()
  })

  it('creates Monaco as pgsql for PostgreSQL', async () => {
    const wrapper = await render('pgsql')
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
})
