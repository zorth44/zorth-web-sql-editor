import { describe, expect, it } from 'vitest'
import { createOperation, validateTaskInput } from './submission'
const valid = { datasourceId: 'ds', database: 'public', sql: "select ';'" }
describe('optimizer input', () => {
  it('validates fields, lengths and coordinates', () => {
    expect(validateTaskInput({ datasourceId: '', database: '', sql: ' ' })).toHaveLength(3)
    expect(
      validateTaskInput({ ...valid, name: 'a'.repeat(101), sql: 'a'.repeat(20001) }),
    ).toHaveLength(2)
    expect(validateTaskInput({ ...valid, database: 'foo\nbar' })).toHaveLength(1)
  })
  it('uses existing scanner for quoted/comment/dollar semicolons', () => {
    expect(validateTaskInput(valid)).toEqual([])
    expect(validateTaskInput({ ...valid, sql: 'select $$;$$; -- ;\n' })).toEqual([])
    expect(validateTaskInput({ ...valid, sql: 'select 1; select 2' })[0]).toContain('一条 SQL')
  })
  it('takes a snapshot with distinct keys for new operations', () => {
    const input = { ...valid }
    const op = createOperation(input)
    input.sql = 'select 2'
    expect(op.payload.sql).toBe(valid.sql)
    expect(createOperation(input).key).not.toBe(op.key)
  })
})
