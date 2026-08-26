import { describe, expect, it } from 'vitest'
import { appendSqlText, replaceSqlOnce } from '@/sql-editor/sql-insert'

describe('sql insert helpers', () => {
  it('writes into an empty editor and appends after existing sql', () => {
    expect(appendSqlText('', 'select 1')).toBe('select 1\n')
    expect(appendSqlText('select 1;', 'select 2;')).toBe('select 1;\n\nselect 2;\n')
  })

  it('replaces the failed statement once and falls back to append', () => {
    const current = 'select a;\nselect * from mock_error;\nselect b;'
    expect(replaceSqlOnce(current, 'select * from mock_error;', 'select 1')).toEqual({
      text: 'select a;\nselect 1\nselect b;',
      replaced: true,
    })
    expect(replaceSqlOnce('select a;', 'select * from mock_error;', 'select 1')).toEqual({
      text: 'select a;\n\nselect 1\n',
      replaced: false,
    })
  })
})
