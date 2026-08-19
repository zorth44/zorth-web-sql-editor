<script setup lang="ts">
import type { DataSourceFormModel } from '@/data-sources/model'
import type { FormErrors } from '@/data-sources/validation'
import type { EngineDescriptor, EngineField } from '@/types/contracts'
const props = withDefaults(
  defineProps<{
    modelValue: DataSourceFormModel
    errors: FormErrors
    edit: boolean
    engines: EngineDescriptor[]
    descriptor: EngineDescriptor | null
    passwordConfigured?: boolean
    disabled?: boolean
  }>(),
  { passwordConfigured: false, disabled: false, engines: () => [], descriptor: null },
)
const emit = defineEmits<{ 'update:modelValue': [value: DataSourceFormModel] }>()
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
    type: (field.widget === 'PASSWORD' ? 'password' : field.widget === 'NUMBER' ? 'number' : 'text') as
      | 'password'
      | 'number'
      | 'text',
    ...(field.min != null ? { min: field.min } : {}),
    ...(field.max != null ? { max: field.max } : {}),
    ...(field.maxLength != null ? { maxlength: field.maxLength } : {}),
    ...(field.kind === 'DEFAULT_NAMESPACE' ? { placeholder: '手工输入，可留空' } : {}),
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
      <h2 class="text-base font-semibold">基本信息</h2>
      <div class="mt-4 grid grid-cols-2 gap-5">
        <div>
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
        <div>
          <label class="label" for="ds-engine">数据库类型</label
          ><select
            id="ds-engine"
            class="field"
            :value="modelValue.engine"
            :aria-invalid="Boolean(errors.engine)"
            @change="update('engine', ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="item in engines" :key="item.id" :value="item.id">
              {{ item.displayName }}
            </option>
          </select>
          <p v-if="errors.engine" class="mt-1 text-xs text-danger">{{ errors.engine }}</p>
        </div>
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
            :id="'ds-' + field.name"
            class="field"
            v-bind="connectionInputAttrs(field)"
            :value="fieldValue(field)"
            :autocomplete="field.kind === 'PASSWORD' ? 'new-password' : 'off'"
            :aria-invalid="Boolean(errors[field.name as keyof FormErrors])"
            @input="updateField(field, ($event.target as HTMLInputElement).value)"
          />
          <p
            v-if="errors[field.name as keyof FormErrors]"
            class="mt-1 text-xs text-danger"
          >
            {{ errors[field.name as keyof FormErrors] }}
          </p>
          <p
            v-else-if="field.kind === 'PASSWORD' && edit && passwordConfigured"
            class="mt-1 text-xs text-muted"
          >
            已配置密码；留空将保留现有密码。
          </p>
        </div>
      </div>
    </section>
    <section>
      <h2 class="text-base font-semibold">JDBC 白名单参数</h2>
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
