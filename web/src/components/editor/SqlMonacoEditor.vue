<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import '@/components/editor/monaco-env'
import * as monaco from 'monaco-editor/editor'
import { useThemeStore } from '@/stores/theme'
import 'monaco-editor/features/register.all'
import 'monaco-editor/languages/definitions/mysql/register'
import 'monaco-editor/languages/definitions/pgsql/register'
import {
  MYSQL_EDITOR_LANGUAGE,
  PG_EDITOR_LANGUAGE,
  formatterLanguageFor,
} from '@/data-sources/catalog'
import { format } from 'sql-formatter'
import { statementAt } from '@/sql-editor/sql'
import { appendSqlText, replaceSqlOnce } from '@/sql-editor/sql-insert'

const props = defineProps<{ modelValue: string; suggestions?: string[]; language?: string }>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:hasSelection': [value: boolean]
  notice: [message: string]
}>()
const theme = useThemeStore()
const root = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | undefined
let completion: monaco.IDisposable | undefined

function monacoTheme(): string {
  return theme.scheme === 'dark' ? 'vs-dark' : 'vs'
}
function resolvedLanguage(): string {
  return props.language === PG_EDITOR_LANGUAGE || props.language === MYSQL_EDITOR_LANGUAGE
    ? props.language
    : MYSQL_EDITOR_LANGUAGE
}

function selectedText(): string {
  if (!editor) return ''
  const model = editor.getModel()
  const selection = editor.getSelection()
  if (!model || !selection) return ''
  return model.getValueInRange(selection).trim()
}
function currentStatement(): string {
  if (!editor) return ''
  const selected = selectedText()
  if (selected) return selected
  const model = editor.getModel()
  const position = editor.getPosition()
  if (!model || !position) return ''
  return statementAt(model.getValue(), model.getOffsetAt(position))?.text || ''
}
/** The selection when there is one, otherwise the whole editor. */
function runnableScript(): string {
  if (!editor) return ''
  return selectedText() || (editor.getValue() || '').trim()
}
function installCompletion(): void {
  completion?.dispose()
  completion = monaco.languages.registerCompletionItemProvider(resolvedLanguage(), {
    provideCompletionItems(model, position) {
      const range = model.getWordUntilPosition(position)
      return {
        suggestions: (props.suggestions || []).map((label) => ({
          label,
          kind: monaco.languages.CompletionItemKind.Field,
          insertText: label,
          range: {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: range.startColumn,
            endColumn: range.endColumn,
          },
        })),
      }
    },
  })
}
function getRunnableStatement(): string {
  return currentStatement()
}
function getRunnableScript(): string {
  return runnableScript()
}
function formatSql(): void {
  if (!editor) return
  try {
    editor.setValue(
      format(editor.getValue(), { language: formatterLanguageFor(resolvedLanguage()) }),
    )
  } catch {
    emit('notice', '当前 SQL 无法格式化')
  }
}
function insertAtCursor(sql: string): void {
  if (!editor) return
  const model = editor.getModel()
  const position = editor.getPosition()
  if (!model || !position) return
  const offset = model.getOffsetAt(position)
  const needsNewline = offset > 0 && model.getValue().charAt(offset - 1) !== '\n'
  editor.executeEdits('zorth-insert', [
    {
      range: {
        startLineNumber: position.lineNumber,
        startColumn: position.column,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      },
      text: `${needsNewline ? '\n' : ''}${sql}${sql.endsWith('\n') ? '' : '\n'}`,
      forceMoveMarkers: true,
    },
  ])
  editor.focus()
}
function getCopilotSql(): string {
  if (!editor) return props.modelValue
  return selectedText() || editor.getValue() || ''
}
function appendSql(sql: string): void {
  if (!editor) return
  editor.setValue(appendSqlText(editor.getValue(), sql))
  editor.focus()
}
function replaceSql(target: string, sql: string): boolean {
  if (!editor) return false
  const result = replaceSqlOnce(editor.getValue(), target, sql)
  editor.setValue(result.text)
  editor.focus()
  return result.replaced
}
function focus(): void {
  editor?.focus()
}

onMounted(() => {
  if (!root.value) return
  editor = monaco.editor.create(root.value, {
    value: props.modelValue,
    language: resolvedLanguage(),
    theme: monacoTheme(),
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 13,
    lineHeight: 21,
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    tabSize: 2,
    padding: { top: 10, bottom: 8 },
    wordWrap: 'off',
    scrollBeyondLastLine: false,
    renderLineHighlight: 'line',
    matchBrackets: 'always',
    folding: true,
    smoothScrolling: true,
    cursorBlinking: 'smooth',
    overviewRulerLanes: 0,
    hideCursorInOverviewRuler: true,
    scrollbar: { verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
    quickSuggestions: { other: true, comments: false, strings: false },
    suggestOnTriggerCharacters: true,
  })
  editor.onDidChangeModelContent(() => emit('update:modelValue', editor?.getValue() || ''))
  editor.onDidChangeCursorSelection(() => emit('update:hasSelection', Boolean(selectedText())))
  editor.onKeyDown((event) => {
    if (event.equals(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS)) event.preventDefault()
  })
  installCompletion()
})
watch(
  () => props.modelValue,
  (value) => {
    if (editor && editor.getValue() !== value) editor.setValue(value)
  },
)
watch(() => props.suggestions, installCompletion, { deep: true })
watch(
  () => props.language,
  () => {
    const model = editor?.getModel()
    if (model) monaco.editor.setModelLanguage(model, resolvedLanguage())
    installCompletion()
  },
)
watch(
  () => theme.scheme,
  () => monaco.editor.setTheme(monacoTheme()),
)
onBeforeUnmount(() => {
  completion?.dispose()
  editor?.dispose()
})
defineExpose({
  getRunnableStatement,
  getRunnableScript,
  getCopilotSql,
  formatSql,
  insertAtCursor,
  appendSql,
  replaceSql,
  focus,
})
</script>
<template>
  <div ref="root" class="h-full min-h-[180px] w-full" aria-label="SQL 编辑器" />
</template>
