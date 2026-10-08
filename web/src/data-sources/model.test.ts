import { describe, expect, it } from 'vitest'
import {
  detailToForm,
  emptyDataSourceForm,
  mapCreateRequest,
  mapCreateTestRequest,
  mapEditTestRequest,
  mapUpdateRequest,
} from '@/data-sources/model'
import {
  gbase8aEngineDescriptor,
  hiveKerberosEngineDescriptor,
  mysqlEngineDescriptor,
  postgresEngineDescriptor,
} from '@/mocks/engines'
import { mapFieldErrors, validateDataSourceForm } from '@/data-sources/validation'
import type { DataSourceDetail } from '@/types/contracts'

function validForm() {
  return {
    ...emptyDataSourceForm(mysqlEngineDescriptor),
    name: '同名库',
    connection: {
      ...emptyDataSourceForm(mysqlEngineDescriptor).connection,
      host: 'mysql.internal',
      username: 'dev',
      password: 'db-secret',
    },
  }
}

function kerberosForm() {
  const form = emptyDataSourceForm(hiveKerberosEngineDescriptor)
  return {
    ...form,
    name: 'Kerberos 订单库',
    connection: {
      ...form.connection,
      environment: 'dev',
      keytabFile: 'hive.keytab',
      queueName: 'root.default',
      defaultDatabase: '',
    },
  }
}

function mysqlDetail(): DataSourceDetail {
  return {
    id: 'ds-1',
    name: '订单测试库',
    engine: 'MYSQL',
    host: 'mysql-a.internal',
    port: 3306,
    username: 'order_dev',
    passwordConfigured: true,
    defaultDatabase: 'orders',
    sslMode: 'PREFERRED',
    lastTestStatus: null,
    lastTestAt: null,
    version: 1,
    updatedBy: '1001',
    updatedByName: '张三',
    updatedAt: '2026-08-12T06:00:00Z',
    connectTimeoutSeconds: 10,
    properties: { serverTimezone: 'Asia/Shanghai' },
    description: '测试环境 A',
    lastTestMessage: null,
    createdBy: '1001',
    createdByName: '张三',
    createdAt: '2026-08-11T02:00:00Z',
  }
}

describe('descriptor-driven form seeding', () => {
  it('seeds MYSQL defaults from the descriptor', () => {
    const form = emptyDataSourceForm(mysqlEngineDescriptor)
    expect(form.engine).toBe('MYSQL')
    expect(form.connection.host).toBe('')
    expect(form.connection.port).toBe('3306')
    expect(form.connection.sslMode).toBe('PREFERRED')
    expect(form.connection.connectTimeoutSeconds).toBe('10')
    expect(form.properties.serverTimezone).toBe('Asia/Shanghai')
  })

  it('seeds Kerberos fields with empty values and no host-based fields', () => {
    const form = emptyDataSourceForm(hiveKerberosEngineDescriptor)
    expect(form.engine).toBe('HIVE_KERBEROS')
    expect(form.connection).toEqual({
      environment: 'dev',
      keytabFile: '',
      queueName: '',
      defaultDatabase: '',
    })
    expect(form.connection).not.toHaveProperty('host')
    expect(form.connection).not.toHaveProperty('password')
    expect(form.properties).toEqual({})
  })

  it('fills only the descriptor-declared fields from a detail', () => {
    const form = detailToForm(mysqlDetail(), mysqlEngineDescriptor)
    expect(form.connection.host).toBe('mysql-a.internal')
    expect(form.connection.port).toBe('3306')
    expect(form.connection.username).toBe('order_dev')
    expect(form.connection.password).toBe('')
    expect(form.connection.defaultDatabase).toBe('orders')
    expect(form.properties).toEqual({ serverTimezone: 'Asia/Shanghai' })
  })

  it('fills Kerberos detail fields by descriptor name and leaves missing ones blank', () => {
    const detail = {
      ...mysqlDetail(),
      engine: 'HIVE_KERBEROS',
      environment: 'pro',
      keytabFile: 'orders.keytab',
      queueName: 'etl',
      defaultDatabase: 'ods',
    } as unknown as DataSourceDetail
    const form = detailToForm(detail, hiveKerberosEngineDescriptor)
    expect(form.connection).toEqual({
      environment: 'pro',
      keytabFile: 'orders.keytab',
      queueName: 'etl',
      defaultDatabase: 'ods',
    })
    expect(form.connection).not.toHaveProperty('host')
  })
})

describe('data-source request mappers', () => {
  it('never includes client-selected product/user fields', () => {
    const requests = [
      mapCreateRequest(validForm(), mysqlEngineDescriptor),
      mapUpdateRequest(validForm(), 7, mysqlEngineDescriptor),
      mapCreateTestRequest(validForm(), mysqlEngineDescriptor),
      mapEditTestRequest(validForm(), mysqlEngineDescriptor),
    ]
    requests.forEach((request) => {
      expect(request).not.toHaveProperty('productId')
      expect(request).not.toHaveProperty('productIds')
      expect(request).not.toHaveProperty('userId')
      expect(request.engine).toBe('MYSQL')
    })
    expect(
      mapCreateRequest({ ...validForm(), engine: 'OTHER' }, mysqlEngineDescriptor).engine,
    ).toBe('OTHER')
    expect(
      mapCreateTestRequest({ ...validForm(), engine: 'OTHER' }, mysqlEngineDescriptor).engine,
    ).toBe('OTHER')
    expect(requests[1]).toMatchObject({ version: 7, password: 'db-secret' })
  })

  it('serializes only MYSQL descriptor fields with typed values', () => {
    const request = mapCreateRequest(validForm(), mysqlEngineDescriptor)
    expect(request).toMatchObject({
      engine: 'MYSQL',
      host: 'mysql.internal',
      port: 3306,
      username: 'dev',
      password: 'db-secret',
      defaultDatabase: null,
      sslMode: 'PREFERRED',
      connectTimeoutSeconds: 10,
    })
    expect(request).not.toHaveProperty('environment')
    expect(request).not.toHaveProperty('keytabFile')
  })

  it('serializes only the Kerberos descriptor fields and omits host-based fields', () => {
    const request = mapCreateRequest(kerberosForm(), hiveKerberosEngineDescriptor)
    expect(request).toEqual({
      name: 'Kerberos 订单库',
      engine: 'HIVE_KERBEROS',
      environment: 'dev',
      keytabFile: 'hive.keytab',
      queueName: 'root.default',
      defaultDatabase: null,
      properties: {},
      description: null,
    })
    for (const absent of ['host', 'port', 'username', 'password', 'sslMode']) {
      expect(request).not.toHaveProperty(absent)
    }
  })

  it('sends the Kerberos fields on update and test requests', () => {
    expect(mapUpdateRequest(kerberosForm(), 4, hiveKerberosEngineDescriptor)).toMatchObject({
      engine: 'HIVE_KERBEROS',
      environment: 'dev',
      keytabFile: 'hive.keytab',
      queueName: 'root.default',
      version: 4,
    })
    expect(mapCreateTestRequest(kerberosForm(), hiveKerberosEngineDescriptor)).not.toHaveProperty(
      'password',
    )
    expect(mapCreateTestRequest(kerberosForm(), hiveKerberosEngineDescriptor)).toMatchObject({
      engine: 'HIVE_KERBEROS',
      environment: 'dev',
      keytabFile: 'hive.keytab',
    })
  })

  it('allows empty edit password for saved-secret reuse', () => {
    const form = { ...validForm(), connection: { ...validForm().connection, password: '' } }
    expect(validateDataSourceForm(form, 'edit', mysqlEngineDescriptor)).not.toHaveProperty(
      'password',
    )
    expect(mapUpdateRequest(form, 2, mysqlEngineDescriptor).password).toBe('')
    expect(mapEditTestRequest(form, mysqlEngineDescriptor).password).toBe('')
  })

  it('validates documented bounds without rejecting duplicate names', () => {
    const form = {
      ...validForm(),
      connection: {
        ...validForm().connection,
        host: 'https://mysql.internal',
        port: '70000',
        connectTimeoutSeconds: '31',
      },
      description: 'x'.repeat(501),
    }
    expect(validateDataSourceForm(form, 'create', mysqlEngineDescriptor)).toMatchObject({
      host: expect.any(String),
      port: expect.any(String),
      connectTimeoutSeconds: expect.any(String),
      description: expect.any(String),
    })
    expect(validateDataSourceForm(validForm(), 'create', mysqlEngineDescriptor)).toEqual({})
    expect(
      validateDataSourceForm(
        {
          ...validForm(),
          connection: {
            ...emptyDataSourceForm(postgresEngineDescriptor).connection,
            host: 'pg.internal',
            username: 'dev',
            password: 'secret',
            defaultDatabase: '',
          },
        },
        'create',
        postgresEngineDescriptor,
      ),
    ).toHaveProperty('defaultDatabase')
    expect(
      validateDataSourceForm(
        {
          ...validForm(),
          connection: {
            ...emptyDataSourceForm(gbase8aEngineDescriptor).connection,
            host: 'gbase.internal',
            username: 'dev',
            password: 'secret',
            defaultDatabase: '',
          },
          properties: { serverTimezone: 'UTC' },
        },
        'create',
        gbase8aEngineDescriptor,
      ),
    ).toEqual({})
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
    expect(validateDataSourceForm(allowed, 'create', mysqlEngineDescriptor)).toEqual({})
    expect(JSON.stringify(mapCreateRequest(allowed, mysqlEngineDescriptor))).not.toContain(
      'useUnicode',
    )
    expect(JSON.stringify(mapCreateRequest(allowed, mysqlEngineDescriptor))).not.toContain(
      'allowPublicKeyRetrieval',
    )
    expect(
      JSON.stringify(
        mapCreateRequest(
          { ...allowed, properties: { ...allowed.properties, allowLoadLocalInfile: 'true' } },
          mysqlEngineDescriptor,
        ),
      ),
    ).not.toContain('allowLoadLocalInfile')
    expect(
      validateDataSourceForm(
        { ...allowed, properties: { serverTimezone: 'not/a-real-time-zone' } },
        'create',
        mysqlEngineDescriptor,
      ),
    ).toHaveProperty('properties')
    expect(
      validateDataSourceForm(
        {
          ...allowed,
          connection: { ...allowed.connection, password: 'x'.repeat(1025) },
        },
        'create',
        mysqlEngineDescriptor,
      ),
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
