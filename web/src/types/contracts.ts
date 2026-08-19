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

export type Engine = string
export type SslMode = string
export type TestStatus = 'SUCCESS' | 'FAILED'
export type ConnectionFailureCode =
  | 'AUTHENTICATION_FAILED'
  | 'CONNECTION_REFUSED'
  | 'CONNECTION_TIMEOUT'
  | 'DATABASE_NOT_FOUND'
  | 'TLS_FAILED'
  | 'CONNECTION_FAILED'
export type JdbcProperties = Record<string, string>

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
  engine?: Engine
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

export interface DatabaseItem {
  name: string
  kind?: 'NAMESPACE'
}
export type DatabaseObjectType = 'TABLE' | 'VIEW'
export interface TableItem {
  database: string
  name: string
  type: DatabaseObjectType
  comment: string | null
}
export interface ColumnItem {
  name: string
  typeName: string
  jdbcType: string
  length: number | null
  precision: number | null
  scale: number | null
  nullable: boolean
  defaultValue: string | null
  extra: string | null
  comment: string | null
  ordinal: number
  primaryKey: boolean
}
export interface PrimaryKeyItem {
  name: string | null
  columns: string[]
}
export interface IndexItem {
  name: string
  unique: boolean
  type: string
  columns: string[]
}
export interface TableDetail {
  database: string
  table: string
  columns: ColumnItem[]
  primaryKey: PrimaryKeyItem | null
  indexes: IndexItem[]
  ddl: string | null
}

export interface SqlColumn {
  name: string
  label: string
  jdbcType: string
  typeName: string
}
export interface BinaryValue {
  binary: true
  size: number
  base64: null
}
export type SqlCellValue = unknown | BinaryValue
export type SqlExecutionResult =
  | {
      kind: 'RESULT_SET'
      executionId: string
      columns: SqlColumn[]
      rows: SqlCellValue[][]
      rowCount: number
      truncated: boolean
      durationMs: number
    }
  | {
      kind: 'UPDATE_COUNT' | 'DDL'
      executionId: string
      affectedRows: number | null
      durationMs: number
      message: string
    }
export interface SqlExecutionRequest {
  executionId: string
  dataSourceId: string
  database: string | null
  statement: string
  rowLimit?: number
}

export type ExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'TIMEOUT'
export type StatementType = 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE' | 'REPLACE' | 'DDL' | 'OTHER'
export interface HistorySummary {
  id: string
  dataSourceId: string
  dataSourceName: string
  database: string | null
  operation: 'EXECUTE' | 'EXPORT'
  statementSummary: string
  statementType: StatementType
  status: ExecutionStatus
  resultKind: SqlExecutionResult['kind'] | null
  returnedRows: number | null
  affectedRows: number | null
  durationMs: number | null
  truncated: boolean
  startedAt: string
  finishedAt: string | null
}
export interface HistoryDetail extends HistorySummary {
  statement: string
  sqlState: string | null
  vendorErrorCode: number | null
  errorMessage: string | null
  connectionAvailable: boolean
}
export interface HistoryListParams {
  keyword?: string
  dataSourceId?: string
  database?: string
  status?: ExecutionStatus | ''
  statementType?: StatementType | ''
  pageSize?: number
  pageToken?: string
}

export type EngineFieldWidget = 'TEXT' | 'NUMBER' | 'PASSWORD' | 'SELECT'
export type EngineFieldKind =
  | 'HOST'
  | 'PORT'
  | 'USERNAME'
  | 'PASSWORD'
  | 'DEFAULT_NAMESPACE'
  | 'SSL_MODE'
  | 'TIMEOUT'
export type ResourceTreeKind = 'NAMESPACE' | 'TABLE' | 'VIEW' | 'PARTITION'

export interface EngineFieldOption {
  value: string
  label: string
}
export interface EngineField {
  name: string
  kind?: EngineFieldKind | string
  widget: EngineFieldWidget | string
  label: string
  required: boolean
  requiredOnCreate?: boolean
  min?: number
  max?: number
  maxLength?: number
  defaultValue?: string
  options?: EngineFieldOption[]
}
export interface ResourceTreeLevel {
  kind: ResourceTreeKind | string
  label: string
  filterLabel?: string
  listEndpoint?: string
  parentKind?: string
}
export interface EngineCapabilities {
  defaultNamespaceRequired: boolean
  canSwitchNamespaceOnConnection: boolean
}
export interface EngineDescriptor {
  id: Engine
  displayName: string
  family: string
  defaultPort: number
  editorLanguage: string
  identifierQuote: string
  capabilities: EngineCapabilities
  connectionFields: EngineField[]
  propertyFields: EngineField[]
  resourceTree: ResourceTreeLevel[]
}
export interface EngineCatalog {
  items: EngineDescriptor[]
}
