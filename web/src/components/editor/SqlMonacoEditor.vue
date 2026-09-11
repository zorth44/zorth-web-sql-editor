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
import { EMPTY_COMPLETION_CATALOG, type CompletionCatalog } from '@/sql-editor/completion-catalog'
import {
  completionInsertText,
  parseCompletionCaret,
  resolveTableName,
} from '@/sql-editor/completion-qualifier'

const props = defineProps<{
  modelValue: string
  catalog?: CompletionCatalog
  identifierQuote?: string
  resolveColumns?: (table: string) => Promise<string[]>
  language?: string
}>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:hasSelection': [value: boolean]
  notice: [message: string]
}>()
const theme = useThemeStore()
const root = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | undefined
let completion: monaco.IDisposable | undefined
const latest = {
  catalog: props.catalog || EMPTY_COMPLETION_CATALOG,
  quote: props.identifierQuote || '`',
  resolveColumns: props.resolveColumns,
}

watch(
  () => [props.catalog, props.identifierQuote, props.resolveColumns] as const,
  () => {
    latest.catalog = props.catalog || EMPTY_COMPLETION_CATALOG
    latest.quote = props.identifierQuote || '`'
    latest.resolveColumns = props.resolveColumns
  },
)

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
function kindFor(kind: 'namespace' | 'table' | 'column'): monaco.languages.CompletionItemKind {
  if (kind === 'namespace') return monaco.languages.CompletionItemKind.Module
  if (kind === 'table') return monaco.languages.CompletionItemKind.Class
  return monaco.languages.CompletionItemKind.Field
}
function rangeAt(
  model: monaco.editor.ITextModel,
  startOffset: number,
  endOffset: number,
): monaco.IRange {
  const start = model.getPositionAt(startOffset)
  const end = model.getPositionAt(endOffset)
  return {
    startLineNumber: start.lineNumber,
    startColumn: start.column,
    endLineNumber: end.lineNumber,
    endColumn: end.column,
  }
}
function items(
  labels: string[],
  kind: 'namespace' | 'table' | 'column',
  range: monaco.IRange,
  quote: string,
): monaco.languages.CompletionItem[] {
  return labels.map((label) => ({
    label,
    kind: kindFor(kind),
    insertText: kind === 'column' ? completionInsertText(label, quote) : label,
    range,
  }))
}
function prefixMatches(name: string, prefix: string): boolean {
  if (!prefix) return true
  return name.toLowerCase().startsWith(prefix.toLowerCase())
}
async function complete(
  model: monaco.editor.ITextModel,
  position: monaco.Position,
): Promise<monaco.languages.CompletionList> {
  const catalog = latest.catalog
  const quote = latest.quote
  const offset = model.getOffsetAt(position)
  const caret = parseCompletionCaret(model.getValue(), offset, quote)
  if (caret.kind === 'non-code') return { suggestions: [] }
  if (caret.kind === 'qualified') {
    const table = resolveTableName(caret.qualifier, catalog.tables)
    if (!table) return { suggestions: [] }
    const cached = catalog.columnsByTable[table]
    const columns = cached || (await latest.resolveColumns?.(table)) || []
    const range = rangeAt(model, caret.prefixStart, caret.prefixEnd)
    return {
      suggestions: items(
        columns.filter((name) => prefixMatches(name, caret.prefix)),
        'column',
        range,
        quote,
      ),
    }
  }
  const word = model.getWordUntilPosition(position)
  const range = {
    startLineNumber: position.lineNumber,
    endLineNumber: position.lineNumber,
    startColumn: word.startColumn,
    endColumn: word.endColumn,
  }
  const cachedColumns = Object.values(catalog.columnsByTable).flat()
  return {
    suggestions: [
      ...items(catalog.namespaces, 'namespace', range, quote),
      ...items(catalog.tables, 'table', range, quote),
      ...items(Array.from(new Set(cachedColumns)), 'column', range, quote),
    ],
  }
}
function installCompletion(): void {
  completion?.dispose()
  completion = monaco.languages.registerCompletionItemProvider(resolvedLanguage(), {
    triggerCharacters: ['.'],
    provideCompletionItems(model, position) {
      return complete(model, position)
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
