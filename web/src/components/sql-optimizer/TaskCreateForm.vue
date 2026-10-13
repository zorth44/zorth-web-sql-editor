<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { listDataSources } from '@/api/data-sources'
import { listAllDatabases } from '@/api/metadata'
import { listEngines } from '@/api/engines'
import { createOptimizationTask } from '@/api/sql-optimization'
import { isApiError, safeErrorMessage } from '@/api/api-error'
import { editorLanguageFor, engineById, namespaceLevel } from '@/data-sources/catalog'
import SqlMonacoEditor from '@/components/editor/SqlMonacoEditor.vue'
import { optimizerKey, useOptimizerAccess } from '@/sql-optimizer/queries'
import {
  createOperation,
  validateTaskInput,
  type CreateOperation,
} from '@/sql-optimizer/submission'

const router = useRouter()
const { allowed, scope } = useOptimizerAccess()
const name = ref('')
const datasourceId = ref('')
const database = ref('')
const sql = ref('')
const message = ref('')
const uncertain = ref(false)
const operation = ref<CreateOperation | null>(null)
const payload = computed(() => ({
  name: name.value,
  datasourceId: datasourceId.value,
  database: database.value,
  sql: sql.value,
}))
const sources = useQuery({
  queryKey: computed(() => optimizerKey(...scope.value, 'sources')),
  queryFn: async () => {
    const items = []
    let token: string | undefined
    const seen = new Set<string>()
    do {
      const page = await listDataSources({
        keyword: '',
        pageSize: 100,
        ...(token ? { pageToken: token } : {}),
      })
      items.push(...page.items)
      token = page.nextPageToken || undefined
      if (token && seen.has(token)) throw new Error('数据源分页异常')
      if (token) seen.add(token)
    } while (token)
    return items
  },
  enabled: allowed,
})
const engines = useQuery({
  queryKey: computed(() => optimizerKey(...scope.value, 'engines')),
  queryFn: listEngines,
  enabled: allowed,
})
const descriptor = computed(() =>
  engineById(
    engines.data.value?.items,
    sources.data.value?.find((s) => s.id === datasourceId.value)?.engine,
  ),
)
const namespaces = useQuery({
  queryKey: computed(() => optimizerKey(...scope.value, 'namespaces', datasourceId.value)),
  queryFn: () => listAllDatabases(datasourceId.value),
  enabled: computed(() => allowed.value && Boolean(datasourceId.value)),
})
watch(datasourceId, () => {
  database.value = ''
})
watch(
  payload,
  () => {
    operation.value = null
    uncertain.value = false
    message.value = ''
  },
  { flush: 'sync' },
)
const mutation = useMutation({
  mutationFn: (op: CreateOperation) => createOptimizationTask(op.payload, op.key),
  retry: false,
  onSuccess: async (task) => {
    operation.value = null
    await router.push(`/sql-optimizer/tasks/${encodeURIComponent(task.id)}`)
  },
  onError: (error) => {
    uncertain.value = isApiError(error) && (error.status === 0 || error.status >= 500)
    message.value = uncertain.value
      ? '尚未确认是否提交成功。输入未改动时，可使用原请求找回任务。'
      : safeErrorMessage(error, '任务提交响应异常，请联系管理员核对任务。')
  },
})
function refreshOptions() {
  void sources.refetch()
  if (datasourceId.value) void namespaces.refetch()
  void engines.refetch()
}
function submit() {
  if (!allowed.value || mutation.isPending.value) return
  const errors = validateTaskInput(payload.value)
  if (errors.length) {
    message.value = errors.join(' ')
    return
  }
  operation.value ||= createOperation(payload.value)
  mutation.mutate(operation.value)
}
</script>
<template>
  <form class="panel p-5" @submit.prevent="submit">
    <h2 class="text-lg font-semibold">创建单 SQL 扫描</h2>
    <p class="mt-1 text-sm text-muted">
      提交后可离开页面，稍后在扫描任务中查看报告。仅支持一条完整只读 SQL。
    </p>
    <fieldset :disabled="mutation.isPending.value || !allowed" class="mt-5 space-y-4">
      <div class="grid gap-4 md:grid-cols-3">
        <label class="label"
          >任务名称（可选）<input v-model="name" class="field mt-2" placeholder="留空由服务端生成"
        /></label>
        <label class="label"
          >数据源<select v-model="datasourceId" class="field mt-2">
            <option value="">请选择</option>
            <option v-for="source in sources.data.value" :key="source.id" :value="source.id">
              {{ source.name }} · {{ source.id }}
            </option>
          </select></label
        >
        <label class="label"
          >{{ namespaceLevel(descriptor)?.label || '数据库 / 命名空间'
          }}<select
            v-model="database"
            class="field mt-2"
            aria-label="数据库 / 命名空间"
            :disabled="!datasourceId || namespaces.isPending.value"
          >
            <option value="">请选择</option>
            <option v-for="item in namespaces.data.value" :key="item.name" :value="item.name">
              {{ item.name }}
            </option>
          </select></label
        >
      </div>
      <p
        v-if="sources.isError.value || namespaces.isError.value || engines.isError.value"
        role="alert"
        class="text-sm text-danger"
      >
        {{ safeErrorMessage(sources.error.value || namespaces.error.value || engines.error.value) }}
        <button class="btn" type="button" @click="refreshOptions">刷新选项</button>
      </p>
      <div>
        <p class="label mb-2">
          SQL <span class="text-muted">{{ sql.length }} / 20000 字符</span>
        </p>
        <div
          class="h-64 overflow-hidden rounded-lg border border-line"
          :class="mutation.isPending.value ? 'pointer-events-none' : ''"
        >
          <SqlMonacoEditor v-model="sql" :language="editorLanguageFor(descriptor)" />
        </div>
      </div>
    </fieldset>
    <p v-if="message" class="mt-4 text-sm text-danger" role="alert">{{ message }}</p>
    <div class="mt-4 flex items-center gap-3">
      <button class="btn-primary" type="submit" :disabled="mutation.isPending.value || !allowed">
        {{
          mutation.isPending.value ? '正在提交…' : uncertain ? '用原请求找回任务' : '提交扫描'
        }}</button
      ><span class="text-xs text-muted">数据库校验与分析由服务端执行。</span>
    </div>
  </form>
</template>
