<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import '@/components/editor/monaco-env'
import * as monaco from 'monaco-editor/editor'
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
const root = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | undefined
let completion: monaco.IDisposable | undefined

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
onMounted(() => {
  if (!root.value) return
  editor = monaco.editor.create(root.value, {
    value: props.modelValue,
    language: 'mysql',
    theme: 'vs',
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 14,
    lineHeight: 22,
    padding: { top: 12 },
    wordWrap: 'off',
    scrollBeyondLastLine: false,
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
    run: () => {
      if (!editor) return
      try {
        editor.setValue(format(editor.getValue(), { language: 'mysql' }))
      } catch {
        emit('notice', '当前 SQL 无法格式化')
      }
    },
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
onBeforeUnmount(() => {
  completion?.dispose()
  editor?.dispose()
})
</script>
<template><div ref="root" class="h-full min-h-[220px] w-full" aria-label="SQL 编辑器" /></template>
