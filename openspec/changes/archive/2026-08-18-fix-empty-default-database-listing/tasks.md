## 1. Connection reset

- [x] 1.1 Change `ConnectionUse.resetAndClose` so it calls `setCatalog` only when `defaultCatalog` is non-blank
- [x] 1.2 Keep a successful `execute` result when catalog restore is skipped or close/eviction fails after the work completed
- [x] 1.3 Discard the pooled connection when default catalog is blank and the session catalog is non-empty, instead of recycling a leftover `USE`

## 2. Metadata listing

- [x] 2.1 Confirm `MysqlMetadataService.databases` still lists by `getCatalogs()` without requiring `defaultDatabase`, and that empty-default sources no longer fail in `jdbc()` because of reset
- [x] 2.2 Align `docs/backend-development-spec.md` session-reset text with the Connector/J-safe catalog rule

## 3. Verification

- [x] 3.1 Extend `ConnectionUseTest` for non-empty restore, null/blank skip of `setCatalog`, and successful work surviving a later close failure
- [x] 3.2 Add a Testcontainers case that creates a data source with null `defaultDatabase` and asserts `GET /databases` returns visible non-system databases instead of `METADATA_QUERY_FAILED`
