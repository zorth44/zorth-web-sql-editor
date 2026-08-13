import { describe, expect, it } from 'vitest'
import {
  emptyDataSourceForm,
  mapCreateRequest,
  mapCreateTestRequest,
  mapEditTestRequest,
  mapUpdateRequest,
} from '@/data-sources/model'
import { mapFieldErrors, validateDataSourceForm } from '@/data-sources/validation'

function validForm() {
  return {
    ...emptyDataSourceForm(),
    name: '同名库',
    host: 'mysql.internal',
    username: 'dev',
    password: 'db-secret',
  }
}

describe('data-source request mappers', () => {
  it('never includes client-selected product/user fields', () => {
    const requests = [
      mapCreateRequest(validForm()),
      mapUpdateRequest(validForm(), 7),
      mapCreateTestRequest(validForm()),
      mapEditTestRequest(validForm()),
    ]
    requests.forEach((request) => {
      expect(request).not.toHaveProperty('productId')
      expect(request).not.toHaveProperty('productIds')
      expect(request).not.toHaveProperty('userId')
    })
    expect(requests[1]).toMatchObject({ version: 7, password: 'db-secret' })
  })
  it('allows empty edit password for saved-secret reuse', () => {
    const form = { ...validForm(), password: '' }
    expect(validateDataSourceForm(form, 'edit')).not.toHaveProperty('password')
    expect(mapUpdateRequest(form, 2).password).toBe('')
    expect(mapEditTestRequest(form).password).toBe('')
  })
  it('validates documented bounds without rejecting duplicate names', () => {
    const form = {
      ...validForm(),
      host: 'https://mysql.internal',
      port: 70000,
      connectTimeoutSeconds: 31,
      description: 'x'.repeat(501),
    }
    expect(validateDataSourceForm(form, 'create')).toMatchObject({
      host: expect.any(String),
      port: expect.any(String),
      connectTimeoutSeconds: expect.any(String),
      description: expect.any(String),
    })
    expect(validateDataSourceForm(validForm(), 'create')).toEqual({})
  })
  it('accepts only the authoritative JDBC allow-list and valid IANA zones', () => {
    const allowed = {
      ...validForm(),
      properties: {
        serverTimezone: 'Europe/Paris',
        characterSetResults: 'UTF-8',
        zeroDateTimeBehavior: 'CONVERT_TO_NULL',
        tinyInt1isBit: 'false',
        sendFractionalSeconds: 'true',
      },
    }
    expect(validateDataSourceForm(allowed, 'create')).toEqual({})
    expect(JSON.stringify(mapCreateRequest(allowed))).not.toContain('useUnicode')
    expect(JSON.stringify(mapCreateRequest(allowed))).not.toContain('allowPublicKeyRetrieval')
    expect(
      validateDataSourceForm(
        { ...allowed, properties: { serverTimezone: 'not/a-real-time-zone' } },
        'create',
      ),
    ).toHaveProperty('properties')
    expect(
      validateDataSourceForm({ ...allowed, password: 'x'.repeat(1025) }, 'create'),
    ).toHaveProperty('password')
  })
  it('maps known backend fields and summarizes unknown ones', () => {
    expect(
      mapFieldErrors([
        { field: 'port', code: 'OUT_OF_RANGE', message: 'bad port' },
        { field: 'mystery', code: 'BAD', message: 'safe summary' },
      ]),
    ).toEqual({ fields: { port: 'bad port' }, summary: ['safe summary'] })
  })
})
