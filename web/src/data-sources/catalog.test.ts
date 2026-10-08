import { describe, expect, it } from 'vitest'
import {
  MYSQL_EDITOR_LANGUAGE,
  PG_EDITOR_LANGUAGE,
  editorLanguageFor,
  formatterLanguageFor,
} from '@/data-sources/catalog'
import {
  gbase8aEngineDescriptor,
  hiveEngineDescriptor,
  hiveKerberosEngineDescriptor,
  mockEngineCatalog,
  mysqlEngineDescriptor,
  postgresEngineDescriptor,
} from '@/mocks/engines'

describe('catalog editor language mapping', () => {
  it('uses hive for a HIVE descriptor', () => {
    expect(editorLanguageFor(hiveEngineDescriptor)).toBe('hive')
  })

  it('keeps mysql for MYSQL and GBASE_8A and pgsql for POSTGRESQL', () => {
    expect(editorLanguageFor(mysqlEngineDescriptor)).toBe(MYSQL_EDITOR_LANGUAGE)
    expect(editorLanguageFor(gbase8aEngineDescriptor)).toBe(MYSQL_EDITOR_LANGUAGE)
    expect(editorLanguageFor(postgresEngineDescriptor)).toBe(PG_EDITOR_LANGUAGE)
  })

  it('falls back to mysql when the language is unregistered', () => {
    expect(editorLanguageFor({ ...hiveEngineDescriptor, editorLanguage: 'cassandra' })).toBe(
      MYSQL_EDITOR_LANGUAGE,
    )
    expect(editorLanguageFor(undefined)).toBe(MYSQL_EDITOR_LANGUAGE)
  })

  it('formats hive with the mysql dialect', () => {
    expect(formatterLanguageFor('hive')).toBe('mysql')
    expect(formatterLanguageFor(PG_EDITOR_LANGUAGE)).toBe('postgresql')
    expect(formatterLanguageFor(MYSQL_EDITOR_LANGUAGE)).toBe('mysql')
  })

  it('uses hive for the Kerberos Hive descriptor', () => {
    expect(editorLanguageFor(hiveKerberosEngineDescriptor)).toBe('hive')
  })
})

describe('HIVE_KERBEROS catalog descriptor', () => {
  it('is registered in the catalog with five engines', () => {
    const ids = mockEngineCatalog.items.map((item) => item.id)
    expect(ids).toContain('HIVE_KERBEROS')
    expect(ids).toHaveLength(5)
  })

  it('declares Hive-wire Kerberos connection fields and no host-based fields', () => {
    const descriptor = hiveKerberosEngineDescriptor
    expect(descriptor.family).toBe('HIVE_WIRE')
    expect(descriptor.editorLanguage).toBe('hive')
    expect(descriptor.identifierQuote).toBe('`')

    const names = descriptor.connectionFields.map((field) => field.name)
    expect(names).toEqual(['environment', 'keytabFile', 'queueName', 'defaultDatabase'])
    for (const absent of ['host', 'port', 'username', 'password', 'sslMode']) {
      expect(names).not.toContain(absent)
    }

    const environment = descriptor.connectionFields.find((field) => field.name === 'environment')!
    expect(environment.kind).toBe('ENVIRONMENT')
    expect(environment.widget).toBe('SELECT')
    expect(environment.required).toBe(true)
    expect(environment.options?.map((option) => option.value)).toEqual(['dev', 'func', 'pro'])

    const keytab = descriptor.connectionFields.find((field) => field.name === 'keytabFile')!
    expect(keytab.kind).toBe('KEYTAB')
    expect(keytab.widget).toBe('TEXT')
    expect(keytab.required).toBe(true)

    const queue = descriptor.connectionFields.find((field) => field.name === 'queueName')!
    expect(queue.kind).toBe('QUEUE')
    expect(queue.required).toBe(false)

    const namespace = descriptor.connectionFields.find((field) => field.name === 'defaultDatabase')!
    expect(namespace.kind).toBe('DEFAULT_NAMESPACE')
    expect(namespace.required).toBe(false)
  })

  it('labels the namespace level as 数据库 and filters it as 筛选数据库', () => {
    const namespace = hiveKerberosEngineDescriptor.resourceTree.find(
      (level) => level.kind === 'NAMESPACE',
    )!
    expect(namespace.label).toBe('数据库')
    expect(namespace.filterLabel).toBe('筛选数据库')
  })
})
