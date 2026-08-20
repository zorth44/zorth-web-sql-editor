<script setup lang="ts">
import { ref, watch } from 'vue'
import { Check, ChevronRight } from 'lucide-vue-next'
import type { DataSourceFormModel } from '@/data-sources/model'
import type { FormErrors } from '@/data-sources/validation'
import type { EngineDescriptor, EngineField } from '@/types/contracts'
import EngineTypeIcon from '@/components/EngineTypeIcon.vue'
const props = withDefaults(
  defineProps<{
    modelValue: DataSourceFormModel
    errors: FormErrors
    edit: boolean
    engines?: EngineDescriptor[]
    descriptor?: EngineDescriptor | null
    passwordConfigured?: boolean
    disabled?: boolean
  }>(),
  { passwordConfigured: false, disabled: false, engines: () => [], descriptor: null },
)
const emit = defineEmits<{ 'update:modelValue': [value: DataSourceFormModel] }>()
const advancedOpen = ref(false)
watch(
  () => props.errors.properties,
  (message) => {
    if (message) advancedOpen.value = true
  },
)
function update<K extends keyof DataSourceFormModel>(key: K, value: DataSourceFormModel[K]): void {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
function fieldValue(field: EngineField): string {
  const value = props.modelValue[field.name as keyof DataSourceFormModel]
  if (value == null) return ''
  if (typeof value === 'object') return ''
  return String(value)
}
function updateField(field: EngineField, raw: string): void {
  if (field.widget === 'NUMBER') update(field.name as 'port', Number(raw) as never)
  else update(field.name as 'host', raw as never)
}
function propertyValue(name: string): string {
  return props.modelValue.properties[name] || ''
}
function property(key: string, value: string): void {
  const properties = { ...props.modelValue.properties }
  if (value) properties[key] = value
  else delete properties[key]
  update('properties', properties)
}
function requiredMark(field: EngineField): string {
  return field.required || (Boolean(field.requiredOnCreate) && !props.edit) ? ' *' : ''
}
function connectionInputAttrs(field: EngineField) {
  return {
    type: (field.widget === 'PASSWORD'
      ? 'password'
      : field.widget === 'NUMBER'
        ? 'number'
        : 'text') as 'password' | 'number' | 'text',
    ...(field.min != null ? { min: field.min } : {}),
    ...(field.max != null ? { max: field.max } : {}),
    ...(field.maxLength != null ? { maxlength: field.maxLength } : {}),
    ...(field.kind === 'DEFAULT_NAMESPACE'
      ? { placeholder: field.required ? '请输入要连接的数据库' : '手工输入，可留空' }
      : {}),
  }
}
function propertyInputAttrs(field: EngineField) {
  return {
    ...(field.name === 'serverTimezone'
      ? { placeholder: '例如 Asia/Shanghai', list: 'property-timezone-suggestions' }
      : {}),
  }
}
</script>
<template>
  <fieldset :disabled="disabled" class="space-y-8">
    <section>
      <h2 class="text-base font-semibold">数据库类型</h2>
      <p class="mt-1 text-sm text-muted">选择要连接的数据库引擎。</p>
      <div
        id="ds-engine"
        class="engine-type-grid mt-4"
        role="radiogroup"
        aria-label="数据库类型"
        :aria-invalid="Boolean(errors.engine)"
      >
        <label
          v-for="item in engines"
          :key="item.id"
          class="engine-type-card"
          :class="{ 'engine-type-card-selected': modelValue.engine === item.id }"
          :data-testid="`engine-type-${item.id}`"
        >
          <input
            :id="'ds-engine-' + item.id"
            class="sr-only"
            type="radio"
            name="ds-engine"
            :value="item.id"
            :checked="modelValue.engine === item.id"
            @change="update('engine', item.id)"
          />
          <Check
            v-if="modelValue.engine === item.id"
            class="engine-type-card-check"
            :size="14"
            stroke-width="2.5"
          />
          <EngineTypeIcon :engine="item.id" />
          <span class="engine-type-card-name">{{ item.displayName }}</span>
        </label>
      </div>
      <p v-if="errors.engine" class="mt-2 text-xs text-danger">{{ errors.engine }}</p>
    </section>
    <section>
      <h2 class="text-base font-semibold">基本信息</h2>
      <div class="mt-4 max-w-xl">
        <label class="label" for="ds-name">数据源名称 *</label
        ><input
          id="ds-name"
          class="field"
          :value="modelValue.name"
          maxlength="100"
          :aria-invalid="Boolean(errors.name)"
          aria-describedby="ds-name-error"
          @input="update('name', ($event.target as HTMLInputElement).value)"
        />
        <p v-if="errors.name" id="ds-name-error" class="mt-1 text-xs text-danger">
          {{ errors.name }}
        </p>
      </div>
    </section>
    <section>
      <h2 class="text-base font-semibold">连接配置</h2>
      <div class="mt-4 grid grid-cols-2 gap-5">
        <div v-for="field in descriptor?.connectionFields || []" :key="field.name">
          <label class="label" :for="'ds-' + field.name"
            >{{ field.label }}{{ requiredMark(field) }}</label
          >
          <select
            v-if="field.widget === 'SELECT'"
            :id="'ds-' + field.name"
            class="field"
            :value="fieldValue(field)"
            @change="updateField(field, ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="option in field.options || []" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <input
            v-else
            :id="'ds-' + field.name"
            class="field"
            v-bind="connectionInputAttrs(field)"
            :value="fieldValue(field)"
            :autocomplete="field.kind === 'PASSWORD' ? 'new-password' : 'off'"
            :aria-invalid="Boolean(errors[field.name as keyof FormErrors])"
            @input="updateField(field, ($event.target as HTMLInputElement).value)"
          />
          <p v-if="errors[field.name as keyof FormErrors]" class="mt-1 text-xs text-danger">
            {{ errors[field.name as keyof FormErrors] }}
          </p>
          <p
            v-else-if="field.kind === 'PASSWORD' && edit && passwordConfigured"
            class="mt-1 text-xs text-muted"
          >
            已配置密码；留空将保留现有密码。
          </p>
          <p
            v-else-if="field.kind === 'DEFAULT_NAMESPACE' && field.required"
            class="mt-1 text-xs text-muted"
          >
            连接时打开的数据库。资源树里列出的是该库下的模式，不是库列表。
          </p>
        </div>
      </div>
    </section>
    <section v-if="descriptor?.propertyFields?.length">
      <button
        class="advanced-toggle"
        type="button"
        data-testid="advanced-jdbc"
        :aria-expanded="advancedOpen"
        aria-controls="advanced-jdbc-fields"
        @click="advancedOpen = !advancedOpen"
      >
        <ChevronRight
          class="mt-0.5 shrink-0 text-muted transition-transform"
          :class="{ 'rotate-90': advancedOpen }"
          :size="16"
        />
        <span class="advanced-toggle-copy">
          <span class="advanced-toggle-title">高级配置</span>
          <span class="advanced-toggle-hint"
            >JDBC 白名单参数已按引擎默认值填写，一般无需修改。</span
          >
        </span>
      </button>
      <div v-show="advancedOpen" id="advanced-jdbc-fields" data-testid="advanced-jdbc-fields">
        <div class="mt-4 grid grid-cols-2 gap-5">
          <div v-for="field in descriptor?.propertyFields || []" :key="field.name">
            <label class="label" :for="'property-' + field.name">{{ field.label }}</label>
            <select
              v-if="field.widget === 'SELECT'"
              :id="'property-' + field.name"
              class="field"
              :value="propertyValue(field.name)"
              @change="property(field.name, ($event.target as HTMLSelectElement).value)"
            >
              <option value="">不设置</option>
              <option
                v-for="option in field.options || []"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
            <input
              v-else
              :id="'property-' + field.name"
              class="field"
              v-bind="propertyInputAttrs(field)"
              :value="propertyValue(field.name)"
              @input="property(field.name, ($event.target as HTMLInputElement).value)"
            />
          </div>
        </div>
        <datalist id="property-timezone-suggestions">
          <option value="Asia/Shanghai"></option>
          <option value="UTC"></option>
        </datalist>
      </div>
      <p v-if="errors.properties" class="mt-2 text-xs text-danger">{{ errors.properties }}</p>
    </section>
    <section>
      <label class="label" for="ds-description">描述</label
      ><textarea
        id="ds-description"
        class="field min-h-28 resize-y"
        :value="modelValue.description"
        maxlength="500"
        @input="update('description', ($event.target as HTMLTextAreaElement).value)"
      />
      <div class="mt-1 flex justify-between text-xs">
        <p class="text-danger">{{ errors.description || '' }}</p>
        <span class="text-muted">{{ modelValue.description.length }}/500</span>
      </div>
    </section>
  </fieldset>
</template>
