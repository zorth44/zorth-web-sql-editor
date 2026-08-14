<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import '@/components/editor/monaco-env'
import * as monaco from 'monaco-editor/editor'
import { useThemeStore } from '@/stores/theme'
import 'monaco-editor/features/register.all'
import 'monaco-editor/languages/definitions/mysql/register'
import { format } from 'sql-formatter'
import { splitSql, statementAt } from '@/sql-editor/sql'

const props = defineProps<{ modelValue: string; suggestions?: string[] }>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  run: [statement: string]
  'run-all': [statement: string]
  notice: [message: string]
}>()
const theme = useThemeStore()
const root = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | undefined
let completion: monaco.IDisposable | undefined

function monacoTheme(): string {
  return theme.scheme === 'dark' ? 'vs-dark' : 'vs'
}

function currentStatement(): string {
  if (!editor) return ''
  const model = editor.getModel()
  const selection = editor.getSelection()
  if (!model || !selection) return ''
  const selected = model.getValueInRange(selection).trim()
  if (selected) return selected
  const position = editor.getPosition()
  return position ? statementAt(model.getValue(), model.getOffsetAt(position))?.text || '' : ''
}
function installCompletion(): void {
  completion?.dispose()
  completion = monaco.languages.registerCompletionItemProvider('mysql', {
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
function formatSql(): void {
  if (!editor) return
  try {
    editor.setValue(format(editor.getValue(), { language: 'mysql' }))
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
function focus(): void {
  editor?.focus()
}

onMounted(() => {
  if (!root.value) return
  editor = monaco.editor.create(root.value, {
    value: props.modelValue,
    language: 'mysql',
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
  editor.addAction({
    id: 'zorth-run',
    label: '运行当前语句',
    keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter],
    run: () => emit('run', currentStatement()),
  })
  editor.addAction({
    id: 'zorth-run-all',
    label: '运行全部',
    keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter],
    run: () => {
      const value = editor?.getValue() || ''
      if (splitSql(value).length > 1) emit('notice', '暂不支持批量执行')
      else emit('run-all', value.trim())
    },
  })
  editor.addAction({
    id: 'zorth-format',
    label: '格式化 SQL',
    keybindings: [monaco.KeyMod.Shift | monaco.KeyMod.Alt | monaco.KeyCode.KeyF],
    run: formatSql,
  })
  editor.addAction({
    id: 'zorth-save',
    label: '保存工作表',
    keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS],
    run: () => emit('notice', '工作表保存暂未开放'),
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
  () => theme.scheme,
  () => monaco.editor.setTheme(monacoTheme()),
)
onBeforeUnmount(() => {
  completion?.dispose()
  editor?.dispose()
})
defineExpose({ getRunnableStatement, formatSql, insertAtCursor, focus })
</script>
<template>
  <div ref="root" class="h-full min-h-[180px] w-full" aria-label="SQL 编辑器" />
</template>
