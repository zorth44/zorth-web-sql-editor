import type {
  CreateConnectionTestRequest,
  CreateDataSourceRequest,
  DataSourceDetail,
  EditConnectionTestRequest,
  JdbcProperties,
  SslMode,
  UpdateDataSourceRequest,
} from '@/types/contracts'

export interface DataSourceFormModel {
  name: string
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

function connectionFields(form: DataSourceFormModel): EditConnectionTestRequest {
  return {
    host: form.host.trim(),
    port: Number(form.port),
    username: form.username.trim(),
    password: form.password || '',
    defaultDatabase: form.defaultDatabase.trim() || null,
    sslMode: form.sslMode,
    connectTimeoutSeconds: Number(form.connectTimeoutSeconds),
    properties: { ...form.properties },
  }
}

export function mapCreateRequest(form: DataSourceFormModel): CreateDataSourceRequest {
  return {
    name: form.name.trim(),
    engine: 'MYSQL',
    ...connectionFields(form),
    password: form.password,
    description: form.description.trim() || null,
  }
}
export function mapUpdateRequest(
  form: DataSourceFormModel,
  version: number,
): UpdateDataSourceRequest {
  return {
    name: form.name.trim(),
    engine: 'MYSQL',
    ...connectionFields(form),
    description: form.description.trim() || null,
    version,
  }
}
export function mapCreateTestRequest(form: DataSourceFormModel): CreateConnectionTestRequest {
  return { ...connectionFields(form), password: form.password }
}
export function mapEditTestRequest(form: DataSourceFormModel): EditConnectionTestRequest {
  return connectionFields(form)
}
