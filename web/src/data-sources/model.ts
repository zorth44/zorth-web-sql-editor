import type {
  CreateConnectionTestRequest,
  CreateDataSourceRequest,
  DataSourceDetail,
  EditConnectionTestRequest,
  Engine,
  JdbcProperties,
  SslMode,
  UpdateDataSourceRequest,
} from '@/types/contracts'
import { sanitizeProperties } from '@/data-sources/catalog'
import type { EngineDescriptor } from '@/types/contracts'

export interface DataSourceFormModel {
  name: string
  engine: Engine
  host: string
  port: number
  username: string
  password: string
  defaultDatabase: string
  sslMode: SslMode
  connectTimeoutSeconds: number
  properties: JdbcProperties
  description: string
}

export function emptyDataSourceForm(): DataSourceFormModel {
  return {
    name: '',
    engine: 'MYSQL',
    host: '',
    port: 3306,
    username: '',
    password: '',
    defaultDatabase: '',
    sslMode: 'PREFERRED',
    connectTimeoutSeconds: 10,
    properties: { serverTimezone: 'Asia/Shanghai' },
    description: '',
  }
}

export function detailToForm(detail: DataSourceDetail): DataSourceFormModel {
  return {
    name: detail.name,
    engine: detail.engine,
    host: detail.host,
    port: detail.port,
    username: detail.username,
    password: '',
    defaultDatabase: detail.defaultDatabase || '',
    sslMode: detail.sslMode,
    connectTimeoutSeconds: detail.connectTimeoutSeconds,
    properties: { ...detail.properties },
    description: detail.description || '',
  }
}

function connectionFields(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): EditConnectionTestRequest {
  return {
    engine: form.engine,
    host: form.host.trim(),
    port: Number(form.port),
    username: form.username.trim(),
    password: form.password || '',
    defaultDatabase: form.defaultDatabase.trim() || null,
    sslMode: form.sslMode,
    connectTimeoutSeconds: Number(form.connectTimeoutSeconds),
    properties: sanitizeProperties({ ...form.properties }, descriptor),
  }
}

export function mapCreateRequest(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): CreateDataSourceRequest {
  return {
    name: form.name.trim(),
    engine: form.engine,
    ...connectionFields(form, descriptor),
    password: form.password,
    description: form.description.trim() || null,
  }
}
export function mapUpdateRequest(
  form: DataSourceFormModel,
  version: number,
  descriptor?: EngineDescriptor,
): UpdateDataSourceRequest {
  return {
    name: form.name.trim(),
    engine: form.engine,
    ...connectionFields(form, descriptor),
    description: form.description.trim() || null,
    version,
  }
}
export function mapCreateTestRequest(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): CreateConnectionTestRequest {
  return { ...connectionFields(form, descriptor), password: form.password }
}
export function mapEditTestRequest(
  form: DataSourceFormModel,
  descriptor?: EngineDescriptor,
): EditConnectionTestRequest {
  return connectionFields(form, descriptor)
}
