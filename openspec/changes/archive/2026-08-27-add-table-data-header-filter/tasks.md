## 1. Filter language and preview SQL

- [x] 1.1 Add a table-data filter parser (draft string + column type → predicate or validation error) covering empty, `NULL`/`NOT NULL`, comparison prefixes, string `LIKE` with escaped wildcards, numeric/date/boolean equality, and binary-column restrictions
- [x] 1.2 Add SQL literal quoting and extend `selectTableData` (or a sibling builder) to emit `SELECT * FROM ns.table` plus optional `AND`-combined `WHERE` and single-column `ORDER BY`, using engine `identifierQuote`
- [x] 1.3 Unit-test parser and SQL builder for quoting, `LIKE` escaping, invalid number/boolean/binary drafts, omitted empty clauses, and PostgreSQL double-quoted identifiers

## 2. Result grid header filter row

- [x] 2.1 Add an opt-in header filter row to `ResultGrid` (controlled drafts, Enter emits apply, filter-row pointer does not select/sort) and include its height in sticky header / hit-test layout
- [x] 2.2 In table-Data mode, hide footer quick-filter and context-menu column filter; keep pin, value panel, copy, and CSV; make sort controlled so the parent re-queries instead of sorting returned rows
- [x] 2.3 Keep SQL RESULT_SET grids on the default path: no header filter row, existing client-side sort/filter/footer filter unchanged
- [x] 2.4 Extend grid and selection tests for filter-row visibility, Enter apply, invalid-draft error display, and “click filter input does not select column”

## 3. Table tab query wiring

- [x] 3.1 Store per-column filter drafts, last-applied filters, and current sort on the table-object tab (memory only; survive Properties ↔ Data; not Session Storage)
- [x] 3.2 Point `loadTableData`, Data refresh, filter Enter, and table-Data sort changes at the same builder + `executeOnTab`; sort changes MUST use last-applied filters, not uncommitted drafts
- [x] 3.3 After a successful table-Data re-query, keep header drafts and sort icons; do not let `resetView()` wipe them

## 4. Verification and docs

- [x] 4.1 Cover table-viewer / editor-page wiring: Enter rebuilds `WHERE`, sort click rebuilds `ORDER BY`, refresh keeps both, Properties round-trip keeps drafts
- [x] 4.2 Update `docs/frontend-development-spec.md` so SQL results stay “filter/sort returned rows” and table Data documents header filters that re-query
- [x] 4.3 Run frontend typecheck, unit/component tests, and `openspec validate add-table-data-header-filter --strict`
