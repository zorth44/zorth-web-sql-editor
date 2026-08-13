export type Capability = 'DATA_SOURCE_MANAGE' | 'SQL_EXECUTE' | 'SQL_EXPORT' | 'HISTORY_READ'

export interface Session {
  user: { id: string; username: string; displayName: string }
  product: { id: string; name: string }
  expiresAt: string
  capabilities: Capability[]
}

export interface BoundAccount {
  id: string
  username: string
  displayName: string
}

export type LoginResult =
  | { kind: 'authenticated'; token: string }
  | { kind: 'select-account'; accounts: BoundAccount[] }
  | { kind: 'binding-required' }

export interface FieldError {
  field: string
  code: string
  message: string
}
export interface VersionConflictDetails {
  currentVersion: number
  currentUpdatedAt: string
  currentUpdatedByName: string
}
export interface DataSourceInUseDetails {
  runningTaskCount: number
}
export interface ApiErrorBody {
  requestId: string
  code: string
  message: string
  details?:
    | { fieldErrors?: FieldError[] }
    | VersionConflictDetails
    | DataSourceInUseDetails
    | Record<string, unknown>
}

export type Engine = 'MYSQL'
export type SslMode = 'DISABLED' | 'PREFERRED' | 'REQUIRED'
export type TestStatus = 'SUCCESS' | 'FAILED'
export type ConnectionFailureCode =
  | 'AUTHENTICATION_FAILED'
  | 'CONNECTION_REFUSED'
  | 'CONNECTION_TIMEOUT'
  | 'DATABASE_NOT_FOUND'
  | 'TLS_FAILED'
  | 'CONNECTION_FAILED'
export type JdbcProperties = Partial<
  Record<
    | 'serverTimezone'
    | 'characterSetResults'
    | 'zeroDateTimeBehavior'
    | 'tinyInt1isBit'
    | 'sendFractionalSeconds',
    string
  >
>

export interface DataSourceListItem {
  id: string
  name: string
  engine: Engine
  host: string
  port: number
  username: string
  passwordConfigured: boolean
  defaultDatabase: string | null
  sslMode: SslMode
  lastTestStatus: TestStatus | null
  lastTestAt: string | null
  version: number
  updatedBy: string
  updatedByName: string
  updatedAt: string
}

export interface DataSourceDetail extends DataSourceListItem {
  connectTimeoutSeconds: number
  properties: JdbcProperties
  description: string | null
  lastTestMessage: string | null
  createdBy: string
  createdByName: string
  createdAt: string
}

export interface CursorPage<T> {
  items: T[]
  nextPageToken: string | null
}
export interface DataSourceListParams {
  keyword: string
  pageSize: number
  pageToken?: string
}

export interface ConnectionFields {
  host: string
  port: number
  username: string
  password?: string | null
  defaultDatabase: string | null
  sslMode: SslMode
  connectTimeoutSeconds: number
  properties: JdbcProperties
}

export interface CreateDataSourceRequest extends ConnectionFields {
  name: string
  engine: Engine
  password: string
  description: string | null
}

export interface UpdateDataSourceRequest extends ConnectionFields {
  name: string
  engine: Engine
  description: string | null
  version: number
}

export type CreateConnectionTestRequest = ConnectionFields & { password: string }
export type EditConnectionTestRequest = ConnectionFields

export interface ConnectionTestResult {
  status: TestStatus
  serverVersion: string | null
  durationMs: number
  message: string
  failureCode: ConnectionFailureCode | null
}
