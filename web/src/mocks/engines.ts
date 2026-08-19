import type { EngineCatalog, EngineDescriptor } from '@/types/contracts'

export const mysqlEngineDescriptor: EngineDescriptor = {
  id: 'MYSQL',
  displayName: 'MySQL',
  family: 'MYSQL_WIRE',
  defaultPort: 3306,
  editorLanguage: 'mysql',
  capabilities: {
    defaultNamespaceRequired: false,
    canSwitchNamespaceOnConnection: true,
  },
  connectionFields: [
    {
      name: 'host',
      kind: 'HOST',
      widget: 'TEXT',
      label: 'Host',
      required: true,
      maxLength: 255,
    },
    {
      name: 'port',
      kind: 'PORT',
      widget: 'NUMBER',
      label: 'Port',
      required: true,
      min: 1,
      max: 65535,
      defaultValue: '3306',
    },
    {
      name: 'username',
      kind: 'USERNAME',
      widget: 'TEXT',
      label: '用户名',
      required: true,
      maxLength: 128,
    },
    {
      name: 'password',
      kind: 'PASSWORD',
      widget: 'PASSWORD',
      label: '密码',
      required: false,
      requiredOnCreate: true,
      maxLength: 1024,
    },
    {
      name: 'defaultDatabase',
      kind: 'DEFAULT_NAMESPACE',
      widget: 'TEXT',
      label: '默认数据库',
      required: false,
      maxLength: 64,
    },
    {
      name: 'sslMode',
      kind: 'SSL_MODE',
      widget: 'SELECT',
      label: 'SSL 模式',
      required: true,
      defaultValue: 'PREFERRED',
      options: [
        { value: 'DISABLED', label: '禁用' },
        { value: 'PREFERRED', label: '优先' },
        { value: 'REQUIRED', label: '必需' },
      ],
    },
    {
      name: 'connectTimeoutSeconds',
      kind: 'TIMEOUT',
      widget: 'NUMBER',
      label: '连接超时（秒）',
      required: true,
      min: 1,
      max: 30,
      defaultValue: '10',
    },
  ],
  propertyFields: [
    {
      name: 'serverTimezone',
      widget: 'TEXT',
      label: 'serverTimezone',
      required: false,
      defaultValue: 'Asia/Shanghai',
    },
    {
      name: 'characterSetResults',
      widget: 'SELECT',
      label: 'characterSetResults',
      required: false,
      options: [
        { value: 'utf8', label: 'utf8' },
        { value: 'UTF-8', label: 'UTF-8' },
      ],
    },
    {
      name: 'zeroDateTimeBehavior',
      widget: 'SELECT',
      label: 'zeroDateTimeBehavior',
      required: false,
      options: [
        { value: 'EXCEPTION', label: 'EXCEPTION' },
        { value: 'CONVERT_TO_NULL', label: 'CONVERT_TO_NULL' },
        { value: 'ROUND', label: 'ROUND' },
      ],
    },
    {
      name: 'tinyInt1isBit',
      widget: 'SELECT',
      label: 'tinyInt1isBit',
      required: false,
      options: [
        { value: 'true', label: 'true' },
        { value: 'false', label: 'false' },
      ],
    },
    {
      name: 'sendFractionalSeconds',
      widget: 'SELECT',
      label: 'sendFractionalSeconds',
      required: false,
      options: [
        { value: 'true', label: 'true' },
        { value: 'false', label: 'false' },
      ],
    },
  ],
  resourceTree: [
    { kind: 'NAMESPACE', label: '数据库', filterLabel: '筛选数据库', listEndpoint: 'databases' },
    { kind: 'TABLE', label: '表', filterLabel: '筛选表名', parentKind: 'NAMESPACE' },
    { kind: 'VIEW', label: '视图', parentKind: 'NAMESPACE' },
  ],
}

export const mockEngineCatalog: EngineCatalog = { items: [mysqlEngineDescriptor] }
