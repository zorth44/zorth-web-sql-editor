import type { DataSourceFormModel } from '@/data-sources/model'
import type { EngineDescriptor, ResourceTreeLevel, SslMode } from '@/types/contracts'

export const MYSQL_EDITOR_LANGUAGE = 'mysql' as const

export function engineById(
  items: EngineDescriptor[] | undefined,
  id: string | undefined,
): EngineDescriptor | undefined {
  if (!items?.length) return undefined
  return items.find((item) => item.id === id) || items[0]
}

export function engineDisplayName(
  items: EngineDescriptor[] | undefined,
  id: string | undefined,
): string {
  return engineById(items, id)?.displayName || id || ''
}

export function editorLanguageFor(descriptor: EngineDescriptor | undefined): string {
  return descriptor?.editorLanguage === MYSQL_EDITOR_LANGUAGE
    ? MYSQL_EDITOR_LANGUAGE
    : MYSQL_EDITOR_LANGUAGE
}

export function namespaceLevel(descriptor: EngineDescriptor | undefined): ResourceTreeLevel | undefined {
  return descriptor?.resourceTree.find((level) => level.kind === 'NAMESPACE')
}

export function objectLevels(descriptor: EngineDescriptor | undefined): ResourceTreeLevel[] {
  return (descriptor?.resourceTree || []).filter(
    (level) => level.kind === 'TABLE' || level.kind === 'VIEW',
  )
}

export function defaultsFromDescriptor(descriptor: EngineDescriptor): Pick<
  DataSourceFormModel,
  'engine' | 'port' | 'sslMode' | 'connectTimeoutSeconds' | 'properties'
> {
  const properties: Record<string, string> = {}
  descriptor.propertyFields.forEach((field) => {
    if (field.defaultValue) properties[field.name] = field.defaultValue
  })
  const port = Number(fieldDefault(descriptor, 'port') || descriptor.defaultPort)
  const timeout = Number(fieldDefault(descriptor, 'connectTimeoutSeconds') || 10)
  return {
    engine: descriptor.id,
    port,
    sslMode: (fieldDefault(descriptor, 'sslMode') || 'PREFERRED') as SslMode,
    connectTimeoutSeconds: timeout,
    properties,
  }
}

export function sanitizeProperties(
  properties: Record<string, string>,
  descriptor: EngineDescriptor | undefined,
): Record<string, string> {
  if (!descriptor) return { ...properties }
  const allowed = new Set(descriptor.propertyFields.map((field) => field.name))
  const next: Record<string, string> = {}
  Object.entries(properties).forEach(([key, value]) => {
    if (allowed.has(key) && value) next[key] = value
  })
  return next
}

function fieldDefault(descriptor: EngineDescriptor, name: string): string | undefined {
  return descriptor.connectionFields.find((field) => field.name === name)?.defaultValue
}
