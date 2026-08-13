<script setup lang="ts">
import type { DataSourceFormModel } from '@/data-sources/model'
import type { FormErrors } from '@/data-sources/validation'
const props = withDefaults(
  defineProps<{
    modelValue: DataSourceFormModel
    errors: FormErrors
    edit: boolean
    passwordConfigured?: boolean
    disabled?: boolean
  }>(),
  { passwordConfigured: false, disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: DataSourceFormModel] }>()
function update<K extends keyof DataSourceFormModel>(key: K, value: DataSourceFormModel[K]): void {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
function property(key: keyof DataSourceFormModel['properties'], value: string): void {
  const properties = { ...props.modelValue.properties }
  if (value) properties[key] = value
  else delete properties[key]
  update('properties', properties)
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
          ><input id="ds-engine" class="field bg-slate-50" value="MySQL" readonly />
        </div>
      </div>
    </section>
    <section>
      <h2 class="text-base font-semibold">连接配置</h2>
      <div class="mt-4 grid grid-cols-2 gap-5">
        <div>
          <label class="label" for="ds-host">Host *</label
          ><input
            id="ds-host"
            class="field"
            :value="modelValue.host"
            placeholder="mysql.internal"
            :aria-invalid="Boolean(errors.host)"
            @input="update('host', ($event.target as HTMLInputElement).value)"
          />
          <p v-if="errors.host" class="mt-1 text-xs text-danger">{{ errors.host }}</p>
        </div>
        <div>
          <label class="label" for="ds-port">Port *</label
          ><input
            id="ds-port"
            class="field"
            type="number"
            min="1"
            max="65535"
            :value="modelValue.port"
            :aria-invalid="Boolean(errors.port)"
            @input="update('port', Number(($event.target as HTMLInputElement).value))"
          />
          <p v-if="errors.port" class="mt-1 text-xs text-danger">{{ errors.port }}</p>
        </div>
        <div>
          <label class="label" for="ds-username">用户名 *</label
          ><input
            id="ds-username"
            class="field"
            :value="modelValue.username"
            maxlength="128"
            autocomplete="off"
            :aria-invalid="Boolean(errors.username)"
            @input="update('username', ($event.target as HTMLInputElement).value)"
          />
          <p v-if="errors.username" class="mt-1 text-xs text-danger">{{ errors.username }}</p>
        </div>
        <div>
          <label class="label" for="ds-password">密码 {{ edit ? '' : '*' }}</label
          ><input
            id="ds-password"
            class="field"
            type="password"
            :value="modelValue.password"
            autocomplete="new-password"
            :aria-invalid="Boolean(errors.password)"
            @input="update('password', ($event.target as HTMLInputElement).value)"
          />
          <p v-if="errors.password" class="mt-1 text-xs text-danger">{{ errors.password }}</p>
          <p v-else-if="edit && passwordConfigured" class="mt-1 text-xs text-muted">
            已配置密码；留空将保留现有密码。
          </p>
        </div>
        <div>
          <label class="label" for="ds-database">默认数据库</label
          ><input
            id="ds-database"
            class="field"
            :value="modelValue.defaultDatabase"
            maxlength="64"
            placeholder="手工输入，可留空"
            @input="update('defaultDatabase', ($event.target as HTMLInputElement).value)"
          />
          <p v-if="errors.defaultDatabase" class="mt-1 text-xs text-danger">
            {{ errors.defaultDatabase }}
          </p>
        </div>
        <div>
          <label class="label" for="ds-ssl">SSL 模式 *</label
          ><select
            id="ds-ssl"
            class="field"
            :value="modelValue.sslMode"
            @change="
              update(
                'sslMode',
                ($event.target as HTMLSelectElement).value as DataSourceFormModel['sslMode'],
              )
            "
          >
            <option value="DISABLED">禁用</option>
            <option value="PREFERRED">优先</option>
            <option value="REQUIRED">必需</option>
          </select>
        </div>
        <div>
          <label class="label" for="ds-timeout">连接超时（秒）*</label
          ><input
            id="ds-timeout"
            class="field"
            type="number"
            min="1"
            max="30"
            :value="modelValue.connectTimeoutSeconds"
            :aria-invalid="Boolean(errors.connectTimeoutSeconds)"
            @input="
              update('connectTimeoutSeconds', Number(($event.target as HTMLInputElement).value))
            "
          />
          <p v-if="errors.connectTimeoutSeconds" class="mt-1 text-xs text-danger">
            {{ errors.connectTimeoutSeconds }}
          </p>
        </div>
      </div>
    </section>
    <section>
      <h2 class="text-base font-semibold">JDBC 白名单参数</h2>
      <div class="mt-4 grid grid-cols-2 gap-5">
        <div>
          <label class="label" for="property-timezone">serverTimezone</label
          ><select
            id="property-timezone"
            class="field"
            :value="modelValue.properties.serverTimezone || ''"
            @change="property('serverTimezone', ($event.target as HTMLSelectElement).value)"
          >
            <option value="">不设置</option>
            <option value="Asia/Shanghai">Asia/Shanghai</option>
            <option value="UTC">UTC</option>
          </select>
        </div>
        <div>
          <label class="label" for="property-unicode">useUnicode</label
          ><select
            id="property-unicode"
            class="field"
            :value="modelValue.properties.useUnicode || ''"
            @change="property('useUnicode', ($event.target as HTMLSelectElement).value)"
          >
            <option value="">不设置</option>
            <option value="true">true</option>
          </select>
        </div>
        <div>
          <label class="label" for="property-zero">zeroDateTimeBehavior</label
          ><select
            id="property-zero"
            class="field"
            :value="modelValue.properties.zeroDateTimeBehavior || ''"
            @change="property('zeroDateTimeBehavior', ($event.target as HTMLSelectElement).value)"
          >
            <option value="">不设置</option>
            <option value="EXCEPTION">EXCEPTION</option>
            <option value="CONVERT_TO_NULL">CONVERT_TO_NULL</option>
            <option value="ROUND">ROUND</option>
          </select>
        </div>
        <div>
          <label class="label" for="property-key">allowPublicKeyRetrieval</label
          ><select
            id="property-key"
            class="field"
            :value="modelValue.properties.allowPublicKeyRetrieval || ''"
            @change="
              property('allowPublicKeyRetrieval', ($event.target as HTMLSelectElement).value)
            "
          >
            <option value="">不设置</option>
            <option value="true">true</option>
            <option value="false">false</option>
          </select>
        </div>
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
