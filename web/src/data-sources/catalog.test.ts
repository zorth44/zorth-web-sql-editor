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
})
