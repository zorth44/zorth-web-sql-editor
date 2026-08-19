<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ArrowLeft, FlaskConical, Save } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import {
  createDataSource,
  getDataSource,
  testCreateForm,
  testEditForm,
  updateDataSource,
} from '@/api/data-sources'
import { listEngines } from '@/api/engines'
import { isApiError, safeErrorMessage } from '@/api/api-error'
import { canManageDataSources } from '@/api/session'
import { apiFieldErrors, versionConflict } from '@/data-sources/api-errors'
import { detailToForm, emptyDataSourceForm, type DataSourceFormModel } from '@/data-sources/model'
import { defaultsFromDescriptor, engineById } from '@/data-sources/catalog'
import {
  hasFormErrors,
  mapFieldErrors,
  validateDataSourceForm,
  type FormErrors,
} from '@/data-sources/validation'
import { queryClient, queryKeys } from '@/query/client'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import type { ConnectionTestResult, VersionConflictDetails } from '@/types/contracts'
import DataSourceForm from '@/components/DataSourceForm.vue'
import ErrorState from '@/components/ErrorState.vue'
import LoadingState from '@/components/LoadingState.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notices = useNotificationsStore()
const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))
const edit = computed(() => Boolean(id.value))
const form = ref<DataSourceFormModel>(emptyDataSourceForm())
const errors = reactive<FormErrors>({})
const summaryErrors = ref<string[]>([])
const testResult = ref<ConnectionTestResult | null>(null)
const conflict = ref<VersionConflictDetails | null>(null)
const detailQuery = useQuery({
  queryKey: computed(() => queryKeys.dataSourceDetail(id.value)),
  queryFn: () => getDataSource(id.value),
  enabled: edit,
  staleTime: 0,
  retry: (count, error) =>
    count < 1 && (!isApiError(error) || error.status === 0 || error.status >= 500),
})
const enginesQuery = useQuery({
  queryKey: queryKeys.engines(),
  queryFn: listEngines,
  staleTime: 60_000,
})
const engines = computed(() => enginesQuery.data.value?.items || [])
const descriptor = computed(
  () => engineById(engines.value, form.value.engine) || engines.value[0] || null,
)
watch(
  () => detailQuery.data.value,
  (detail) => {
    if (detail) form.value = detailToForm(detail)
  },
  { immediate: true },
)
watch(
  () => enginesQuery.data.value,
  (catalog) => {
    if (edit.value || !catalog?.items.length) return
    const selected = engineById(catalog.items, form.value.engine) || catalog.items[0]
    if (!selected) return
    form.value = { ...form.value, ...defaultsFromDescriptor(selected) }
  },
)
watch(
  () => form.value.engine,
  (engineId, previous) => {
    if (!previous || engineId === previous) return
    const selected = engineById(engines.value, engineId)
    if (selected) form.value = { ...form.value, ...defaultsFromDescriptor(selected) }
  },
)
const allowed = computed(() => canManageDataSources(auth.session || undefined))
const busy = computed(() => saveMutation.isPending.value || testMutation.isPending.value)

function clearErrors(): void {
  Object.keys(errors).forEach((key) => delete errors[key as keyof FormErrors])
  summaryErrors.value = []
  conflict.value = null
}
function validate(): boolean {
  clearErrors()
  Object.assign(errors, validateDataSourceForm(form.value, edit.value ? 'edit' : 'create', descriptor.value || undefined))
  return !hasFormErrors(errors)
}
function handleApiError(error: unknown): void {
  const backend = apiFieldErrors(error)
  if (backend.length) {
    const mapped = mapFieldErrors(backend)
    Object.assign(errors, mapped.fields)
    summaryErrors.value = mapped.summary
    return
  }
  conflict.value = versionConflict(error)
  if (!conflict.value) summaryErrors.value = [safeErrorMessage(error)]
}
function scrubPassword(): void {
  form.value.password = ''
}

const saveMutation = useMutation({
  mutationFn: () =>
    edit.value
      ? updateDataSource(
          id.value,
          form.value,
          detailQuery.data.value!.version,
          descriptor.value || undefined,
        )
      : createDataSource(form.value, descriptor.value || undefined),
  retry: false,
  onSuccess: async () => {
    scrubPassword()
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.dataSourceLists() }),
      ...(edit.value
        ? [queryClient.invalidateQueries({ queryKey: queryKeys.dataSourceDetail(id.value) })]
        : []),
    ])
    notices.push('success', edit.value ? '数据源已更新' : '数据源已创建')
    await router.push('/data-sources')
  },
  onError: handleApiError,
})
const testMutation = useMutation({
  mutationFn: () =>
    edit.value
      ? testEditForm(id.value, form.value, descriptor.value || undefined)
      : testCreateForm(form.value, descriptor.value || undefined),
  retry: false,
  onSuccess: (result) => {
    testResult.value = result
  },
  onError: handleApiError,
})
function save(): void {
  if (!validate() || busy.value || (edit.value && !detailQuery.data.value)) return
  saveMutation.mutate()
}
function test(): void {
  if (!validate() || busy.value) return
  testResult.value = null
  testMutation.mutate()
}
async function reloadCurrent(): Promise<void> {
  conflict.value = null
  clearErrors()
  await detailQuery.refetch()
}
onBeforeUnmount(scrubPassword)
</script>
<template>
  <section>
    <RouterLink
      class="mb-5 inline-flex items-center gap-2 text-sm font-medium text-muted hover:text-brand"
      to="/data-sources"
      ><ArrowLeft :size="16" />返回数据源列表</RouterLink
    >
    <div class="mb-6">
      <p class="text-sm font-medium text-brand">{{ edit ? '编辑连接' : '新建连接' }}</p>
      <h1 class="mt-1 text-3xl font-semibold">
        {{ edit ? detailQuery.data.value?.name || '编辑数据源' : '新增数据源' }}
      </h1>
      <p class="mt-2 text-sm text-muted">
        配置目标库连接。产品范围由服务端会话确定，不在表单中选择。
      </p>
    </div>
    <div v-if="!allowed" class="panel p-8 text-center text-muted" role="alert">
      当前账号没有数据源管理能力。
    </div>
    <LoadingState
      v-else-if="enginesQuery.isPending.value || (edit && detailQuery.isPending.value)"
      :label="edit ? '正在加载数据源详情…' : '正在加载引擎目录…'"
    /><ErrorState
      v-else-if="enginesQuery.isError.value"
      :message="safeErrorMessage(enginesQuery.error.value)"
      retryable
      @retry="enginesQuery.refetch()"
    /><ErrorState
      v-else-if="edit && detailQuery.isError.value"
      :message="
        isApiError(detailQuery.error.value) &&
        detailQuery.error.value.code === 'DATA_SOURCE_NOT_FOUND'
          ? '数据源不存在或已不再可见。'
          : safeErrorMessage(detailQuery.error.value)
      "
      :retryable="
        !(
          isApiError(detailQuery.error.value) &&
          detailQuery.error.value.code === 'DATA_SOURCE_NOT_FOUND'
        )
      "
      @retry="detailQuery.refetch()"
    />
    <form v-else class="panel p-7" @submit.prevent="save">
      <DataSourceForm
        v-model="form"
        :errors="errors"
        :edit="edit"
        :engines="engines"
        :descriptor="descriptor"
        :password-configured="Boolean(detailQuery.data.value?.passwordConfigured)"
        :disabled="busy"
      />
      <div
        v-if="summaryErrors.length"
        class="mt-6 rounded-lg bg-danger-soft p-4 text-sm text-danger"
        role="alert"
      >
        <p v-for="message in summaryErrors" :key="message">{{ message }}</p>
      </div>
      <div
        v-if="conflict"
        class="mt-6 rounded-lg border border-warning-line bg-warning-soft p-4 text-sm"
        role="alert"
      >
        <strong>此数据源已被其他用户更新</strong>
        <p class="mt-1 text-ink">
          {{ conflict.currentUpdatedByName }} 于
          {{ new Date(conflict.currentUpdatedAt).toLocaleString('zh-CN') }} 更新，当前版本
          {{ conflict.currentVersion }}。请重新加载后再编辑。
        </p>
        <button class="btn mt-3" type="button" @click="reloadCurrent">重新加载当前详情</button>
      </div>
      <div
        v-if="testMutation.isPending.value"
        class="mt-6 rounded-lg bg-subtle p-4 text-sm text-muted"
        role="status"
      >
        正在测试连接，最多等待 {{ form.connectTimeoutSeconds }} 秒…
      </div>
      <div
        v-else-if="testResult"
        class="mt-6 rounded-lg p-4 text-sm"
        :class="
          testResult.status === 'SUCCESS'
            ? 'bg-success-soft text-success'
            : 'bg-danger-soft text-danger'
        "
        role="status"
      >
        <strong>{{ testResult.status === 'SUCCESS' ? '连接成功' : '连接失败' }}</strong>
        <p class="mt-1">
          {{ testResult.message
          }}<span v-if="testResult.serverVersion"> · MySQL {{ testResult.serverVersion }}</span> ·
          {{ testResult.durationMs }}ms<span v-if="testResult.failureCode">
            · {{ testResult.failureCode }}</span
          >
        </p>
      </div>
      <footer class="mt-8 flex items-center justify-end gap-3 border-t border-line pt-6">
        <RouterLink class="btn" to="/data-sources">取消</RouterLink
        ><button class="btn" type="button" :disabled="busy" @click="test">
          <FlaskConical :size="17" />{{
            testMutation.isPending.value ? '测试中…' : '测试连接'
          }}</button
        ><button class="btn-primary" type="submit" :disabled="busy">
          <Save :size="17" />{{ saveMutation.isPending.value ? '保存中…' : '保存' }}
        </button>
      </footer>
    </form>
  </section>
</template>
