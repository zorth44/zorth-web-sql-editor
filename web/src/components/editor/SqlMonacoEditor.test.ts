import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/components/editor/monaco-env', () => ({}))
vi.mock('monaco-editor/features/register.all', () => ({}))
vi.mock('monaco-editor/languages/definitions/mysql/register', () => ({}))
vi.mock('monaco-editor/languages/definitions/pgsql/register', () => ({}))

type Action = { id: string; run: () => void }
const actions = new Map<string, Action>()
const fake = {
  value: 'select 1;\nselect 2;',
  selection: '',
  cursorOffset: 0,
  createdLanguage: 'mysql',
  selectionListener: (() => {}) as () => void,
}

vi.mock('monaco-editor/editor', () => ({
  KeyMod: { CtrlCmd: 1, Shift: 2, Alt: 4 },
  KeyCode: { Enter: 8, KeyF: 16, KeyS: 32 },
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
      addAction: (action: Action) => actions.set(action.id, action),
      executeEdits: () => true,
      focus: () => {},
      dispose: () => {},
    }
    },
  },
}))

beforeEach(() => {
  setActivePinia(createPinia())
  actions.clear()
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
  it('runs the statement at the cursor with the single-statement shortcut', async () => {
    const wrapper = await render()
    actions.get('zorth-run')?.run()
    expect(wrapper.emitted('run')?.at(-1)).toEqual(['select 1'])
    wrapper.unmount()
  })

  it('runs the whole editor text with the script shortcut', async () => {
    const wrapper = await render()
    actions.get('zorth-run-script')?.run()
    expect(wrapper.emitted('run-script')?.at(-1)).toEqual(['select 1;\nselect 2;'])
    expect(wrapper.emitted('notice')).toBeUndefined()
    wrapper.unmount()
  })

  it('runs only the selection when there is one', async () => {
    const wrapper = await render()
    fake.selection = 'select 2'
    actions.get('zorth-run-script')?.run()
    actions.get('zorth-run')?.run()
    expect(wrapper.emitted('run-script')?.at(-1)).toEqual(['select 2'])
    expect(wrapper.emitted('run')?.at(-1)).toEqual(['select 2'])
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
