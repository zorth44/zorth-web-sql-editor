import type { EngineDescriptor, FieldError } from '@/types/contracts'
import type { DataSourceFormModel } from '@/data-sources/model'

export type FormField = keyof DataSourceFormModel
export type FormErrors = Partial<Record<FormField, string>>

function isKnownTimeZone(value: string): boolean {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: value }).format()
    return true
  } catch {
    return false
  }
}

function fieldOf(descriptor: EngineDescriptor | undefined, name: string) {
  return descriptor?.connectionFields.find((item) => item.name === name)
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
  if (!form.host.trim()) errors.host = '请输入 Host'
  else if (/^[a-z][a-z\d+.-]*:\/\//i.test(form.host.trim())) errors.host = 'Host 不应包含协议'
  else if (form.host.trim().length > 255) errors.host = 'Host 最多 255 个字符'
  const portField = fieldOf(descriptor, 'port')
  const portMin = portField?.min ?? 1
  const portMax = portField?.max ?? 65535
  if (
    !Number.isInteger(Number(form.port)) ||
    Number(form.port) < portMin ||
    Number(form.port) > portMax
  )
    errors.port = `端口必须在 ${portMin}–${portMax} 之间`
  const usernameLength = form.username.trim().length
  if (usernameLength < 1 || usernameLength > 128) errors.username = '用户名长度必须为 1–128 个字符'
  if (mode === 'create' && !form.password) errors.password = '新增数据源必须输入密码'
  else if (form.password.length > 1024) errors.password = '密码最多 1024 个字符'
  const namespaceField = fieldOf(descriptor, 'defaultDatabase')
  const namespaceMax = namespaceField?.maxLength ?? 64
  const namespaceLabel = namespaceField?.label || '默认数据库'
  if (namespaceField?.required && !form.defaultDatabase.trim())
    errors.defaultDatabase = `请输入${namespaceLabel}`
  else if (form.defaultDatabase.trim().length > namespaceMax)
    errors.defaultDatabase = `${namespaceLabel}最多 ${namespaceMax} 个字符`
  const timeoutField = fieldOf(descriptor, 'connectTimeoutSeconds')
  const timeoutMin = timeoutField?.min ?? 1
  const timeoutMax = timeoutField?.max ?? 30
  if (
    !Number.isInteger(Number(form.connectTimeoutSeconds)) ||
    Number(form.connectTimeoutSeconds) < timeoutMin ||
    Number(form.connectTimeoutSeconds) > timeoutMax
  )
    errors.connectTimeoutSeconds = `连接超时必须在 ${timeoutMin}–${timeoutMax} 秒之间`
  if (form.description.length > 500) errors.description = '描述最多 500 个字符'
  const sslField = fieldOf(descriptor, 'sslMode')
  if (sslField?.options?.length && !sslField.options.some((item) => item.value === form.sslMode)) {
    errors.sslMode = 'SSL 模式不合法'
  }
  const propertyFields = descriptor?.propertyFields
  for (const [key, value] of Object.entries(form.properties)) {
    if (!value) continue
    if (propertyFields) {
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
    } else if (key === 'serverTimezone' ? !isKnownTimeZone(value) : false) {
      errors.properties = `JDBC 参数 ${key} 的值不在白名单中`
      break
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
  const aliases: Record<string, FormField> = {
    connectTimeoutSeconds: 'connectTimeoutSeconds',
    defaultDatabase: 'defaultDatabase',
    sslMode: 'sslMode',
    properties: 'properties',
    name: 'name',
    engine: 'engine',
    host: 'host',
    port: 'port',
    username: 'username',
    password: 'password',
    description: 'description',
  }
  fieldErrors.forEach((item) => {
    const field = aliases[item.field]
    if (field) fields[field] = item.message
    else summary.push(item.message)
  })
  return { fields, summary }
}

export function hasFormErrors(errors: FormErrors): boolean {
  return Object.keys(errors).length > 0
}
