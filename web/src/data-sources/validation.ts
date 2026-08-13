import type { FieldError } from '@/types/contracts'
import type { DataSourceFormModel } from '@/data-sources/model'

export type FormField = keyof DataSourceFormModel
export type FormErrors = Partial<Record<FormField, string>>

const PROPERTY_VALUES: Record<string, readonly string[]> = {
  serverTimezone: ['Asia/Shanghai', 'UTC'],
  useUnicode: ['true'],
  zeroDateTimeBehavior: ['EXCEPTION', 'CONVERT_TO_NULL', 'ROUND'],
  allowPublicKeyRetrieval: ['true', 'false'],
}

export function validateDataSourceForm(
  form: DataSourceFormModel,
  mode: 'create' | 'edit',
): FormErrors {
  const errors: FormErrors = {}
  const nameLength = form.name.trim().length
  if (nameLength < 1 || nameLength > 100) errors.name = '名称长度必须为 1–100 个字符'
  if (!form.host.trim()) errors.host = '请输入 Host'
  else if (/^[a-z][a-z\d+.-]*:\/\//i.test(form.host.trim())) errors.host = 'Host 不应包含协议'
  else if (form.host.trim().length > 255) errors.host = 'Host 最多 255 个字符'
  if (!Number.isInteger(Number(form.port)) || Number(form.port) < 1 || Number(form.port) > 65535)
    errors.port = '端口必须在 1–65535 之间'
  const usernameLength = form.username.trim().length
  if (usernameLength < 1 || usernameLength > 128) errors.username = '用户名长度必须为 1–128 个字符'
  if (mode === 'create' && !form.password) errors.password = '新增数据源必须输入密码'
  if (form.defaultDatabase.trim().length > 64) errors.defaultDatabase = '默认数据库最多 64 个字符'
  if (
    !Number.isInteger(Number(form.connectTimeoutSeconds)) ||
    Number(form.connectTimeoutSeconds) < 1 ||
    Number(form.connectTimeoutSeconds) > 30
  )
    errors.connectTimeoutSeconds = '连接超时必须在 1–30 秒之间'
  if (form.description.length > 500) errors.description = '描述最多 500 个字符'
  for (const [key, value] of Object.entries(form.properties)) {
    if (!PROPERTY_VALUES[key]?.includes(value || '')) {
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
