import type { EngineDescriptor, EngineField, FieldError } from '@/types/contracts'
import type { DataSourceFormModel } from '@/data-sources/model'

export type FormField = string
export type FormErrors = Record<string, string>

const KNOWN_FIELDS = new Set([
  'name',
  'engine',
  'description',
  'properties',
  'host',
  'port',
  'username',
  'password',
  'defaultDatabase',
  'sslMode',
  'connectTimeoutSeconds',
  'environment',
  'keytabFile',
  'queueName',
])

function isKnownTimeZone(value: string): boolean {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: value }).format()
    return true
  } catch {
    return false
  }
}

function validateConnectionField(
  field: EngineField,
  form: DataSourceFormModel,
  mode: 'create' | 'edit',
  errors: FormErrors,
): void {
  const value = form.connection[field.name] ?? ''
  const required = field.required || (Boolean(field.requiredOnCreate) && mode === 'create')
  if (field.widget === 'NUMBER') {
    const min = field.min ?? 1
    const max = field.max ?? 65535
    if (value === '') {
      if (required) errors[field.name] = `请输入${field.label}`
      return
    }
    const numeric = Number(value)
    if (!Number.isInteger(numeric) || numeric < min || numeric > max)
      errors[field.name] = `${field.label}必须在 ${min}–${max} 之间`
    return
  }
  if (value.length === 0) {
    if (required) errors[field.name] = `请输入${field.label}`
    return
  }
  if (field.kind === 'HOST' && /^[a-z][a-z\d+.-]*:\/\//i.test(value)) {
    errors[field.name] = 'Host 不应包含协议'
    return
  }
  if (field.maxLength != null && value.length > field.maxLength) {
    errors[field.name] = `${field.label}最多 ${field.maxLength} 个字符`
    return
  }
  if (
    field.widget === 'SELECT' &&
    field.options?.length &&
    !field.options.some((option) => option.value === value)
  ) {
    errors[field.name] = `${field.label}不合法`
  }
}

export function validateDataSourceForm(
  form: DataSourceFormModel,
  mode: 'create' | 'edit',
  descriptor?: EngineDescriptor,
): FormErrors {
  const errors: FormErrors = {}
  const nameLength = form.name.trim().length
  if (nameLength < 1 || nameLength > 100) errors.name = '名称长度必须为 1–100 个字符'
  if (!form.engine) errors.engine = '请选择数据库类型'
  if (form.description.length > 500) errors.description = '描述最多 500 个字符'
  descriptor?.connectionFields.forEach((field) =>
    validateConnectionField(field, form, mode, errors),
  )
  const propertyFields = descriptor?.propertyFields
  if (propertyFields) {
    for (const [key, value] of Object.entries(form.properties)) {
      if (!value) continue
      const field = propertyFields.find((item) => item.name === key)
      const allowed = field
        ? key === 'serverTimezone'
          ? isKnownTimeZone(value)
          : !field.options?.length || field.options.some((item) => item.value === value)
        : false
      if (!allowed) {
        errors.properties = `JDBC 参数 ${key} 的值不在白名单中`
        break
      }
    }
  }
  return errors
}

export function mapFieldErrors(fieldErrors: FieldError[]): {
  fields: FormErrors
  summary: string[]
} {
  const fields: FormErrors = {}
  const summary: string[] = []
  fieldErrors.forEach((item) => {
    if (KNOWN_FIELDS.has(item.field)) fields[item.field] = item.message
    else summary.push(item.message)
  })
  return { fields, summary }
}

export function hasFormErrors(errors: FormErrors): boolean {
  return Object.keys(errors).length > 0
}
