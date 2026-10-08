import type {
  CreateConnectionTestRequest,
  CreateDataSourceRequest,
  DataSourceDetail,
  EditConnectionTestRequest,
  Engine,
  EngineDescriptor,
  EngineField,
  JdbcProperties,
  UpdateDataSourceRequest,
} from '@/types/contracts'
import { connectionDefaults, propertyDefaults, sanitizeProperties } from '@/data-sources/catalog'

export interface DataSourceFormModel {
  name: string
  engine: Engine
  connection: Record<string, string>
  properties: JdbcProperties
  description: string
}

export function emptyDataSourceForm(descriptor?: EngineDescriptor): DataSourceFormModel {
  return {
    name: '',
    engine: descriptor?.id ?? 'MYSQL',
    connection: descriptor ? connectionDefaults(descriptor) : {},
    properties: descriptor ? propertyDefaults(descriptor) : {},
    description: '',
  }
}

function isPasswordField(field: EngineField): boolean {
  return field.kind === 'PASSWORD' || field.widget === 'PASSWORD'
}

export function detailToForm(
  detail: DataSourceDetail,
  descriptor?: EngineDescriptor,
): DataSourceFormModel {
  const source = detail as unknown as Record<string, unknown>
  const connection: Record<string, string> = {}
  descriptor?.connectionFields.forEach((field) => {
    if (isPasswordField(field)) {
      connection[field.name] = ''
      return
    }
    const value = source[field.name]
    connection[field.name] = value == null ? '' : String(value)
  })
  return {
    name: detail.name,
    engine: detail.engine,
    connection,
    properties: { ...detail.properties },
    description: detail.description || '',
  }
}

type ConnectionPayloadValue = string | number | null

function serializeConnectionField(field: EngineField, raw: string): ConnectionPayloadValue {
  if (isPasswordField(field)) return raw
  if (field.widget === 'NUMBER') return raw === '' ? null : Number(raw)
  const text = raw.trim()
  if (field.kind === 'DEFAULT_NAMESPACE') return text || null
  return text
}

function connectionFields(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): Record<string, unknown> {
  const fields: Record<string, unknown> = { engine: form.engine }
  descriptor?.connectionFields.forEach((field) => {
    fields[field.name] = serializeConnectionField(field, form.connection[field.name] ?? '')
  })
  fields.properties = sanitizeProperties({ ...form.properties }, descriptor)
  return fields
}

export function mapCreateRequest(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): CreateDataSourceRequest {
  return {
    name: form.name.trim(),
    ...connectionFields(form, descriptor),
    description: form.description.trim() || null,
  } as unknown as CreateDataSourceRequest
}

export function mapUpdateRequest(
  form: DataSourceFormModel,
  version: number,
  descriptor?: EngineDescriptor,
): UpdateDataSourceRequest {
  return {
    name: form.name.trim(),
    ...connectionFields(form, descriptor),
    description: form.description.trim() || null,
    version,
  } as unknown as UpdateDataSourceRequest
}

export function mapCreateTestRequest(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): CreateConnectionTestRequest {
  return connectionFields(form, descriptor) as unknown as CreateConnectionTestRequest
}

export function mapEditTestRequest(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): EditConnectionTestRequest {
  return connectionFields(form, descriptor) as unknown as EditConnectionTestRequest
}
