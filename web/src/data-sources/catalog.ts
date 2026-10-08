import type { DataSourceFormModel } from '@/data-sources/model'
import type { EngineDescriptor, ResourceTreeLevel } from '@/types/contracts'

export const MYSQL_EDITOR_LANGUAGE = 'mysql' as const
export const PG_EDITOR_LANGUAGE = 'pgsql' as const
export const HIVE_EDITOR_LANGUAGE = 'hive' as const

const REGISTERED_EDITOR_LANGUAGES = new Set<string>([
  MYSQL_EDITOR_LANGUAGE,
  PG_EDITOR_LANGUAGE,
  HIVE_EDITOR_LANGUAGE,
])

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
  const language = descriptor?.editorLanguage
  return language && REGISTERED_EDITOR_LANGUAGES.has(language) ? language : MYSQL_EDITOR_LANGUAGE
}

export function identifierQuoteFor(descriptor: EngineDescriptor | undefined): string {
  return descriptor?.identifierQuote === '"' ? '"' : '`'
}

export function formatterLanguageFor(language: string): 'mysql' | 'postgresql' {
  return language === PG_EDITOR_LANGUAGE ? 'postgresql' : 'mysql'
}

export function namespaceLevel(
  descriptor: EngineDescriptor | undefined,
): ResourceTreeLevel | undefined {
  return descriptor?.resourceTree.find((level) => level.kind === 'NAMESPACE')
}

export function objectLevels(descriptor: EngineDescriptor | undefined): ResourceTreeLevel[] {
  return (descriptor?.resourceTree || []).filter(
    (level) => level.kind === 'TABLE' || level.kind === 'VIEW',
  )
}

export function connectionDefaults(descriptor: EngineDescriptor): Record<string, string> {
  const connection: Record<string, string> = {}
  descriptor.connectionFields.forEach((field) => {
    connection[field.name] = field.defaultValue ?? ''
  })
  return connection
}

export function propertyDefaults(descriptor: EngineDescriptor): Record<string, string> {
  const properties: Record<string, string> = {}
  descriptor.propertyFields.forEach((field) => {
    if (field.defaultValue) properties[field.name] = field.defaultValue
  })
  return properties
}

export function defaultsFromDescriptor(
  descriptor: EngineDescriptor,
): Pick<DataSourceFormModel, 'engine' | 'connection' | 'properties'> {
  return {
    engine: descriptor.id,
    connection: connectionDefaults(descriptor),
    properties: propertyDefaults(descriptor),
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
