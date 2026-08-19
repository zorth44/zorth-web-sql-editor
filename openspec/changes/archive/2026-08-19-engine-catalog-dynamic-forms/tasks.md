## 1. Engine descriptor on SPI

- [x] 1.1 Add `EngineDescriptor` and field/tree DTOs in `com.bocsoft.sqleditor.engine` (`connectionFields`, `propertyFields`, `resourceTree`, capabilities)
- [x] 1.2 Add `EngineSupport.descriptor()` and `EngineRegistry.descriptors()` that return in-memory descriptors without opening a target connection
- [x] 1.3 Implement the MYSQL descriptor: `id=MYSQL`, `family=MYSQL_WIRE`, `defaultPort=3306`, `editorLanguage=mysql`, `DEFAULT_NAMESPACE` → `defaultDatabase`, `resourceTree` first level `NAMESPACE` with `listEndpoint=databases`
- [x] 1.4 Source MYSQL `propertyFields` from the same allow-list `MysqlJdbc` already validates; add a unit test that the two key sets match

## 2. Engine catalog API

- [x] 2.1 Add authenticated `GET /api/v1/engines` that returns `{ items: descriptors }` and does not require `DATA_SOURCE_MANAGE`
- [x] 2.2 Exclude passwords, JDBC URLs, CIDR policy, and encryption material from the catalog JSON
- [x] 2.3 Add tests: unauthenticated 401; authenticated list contains exactly MYSQL with the documented field names and tree kinds

## 3. Connection test engine and NAMESPACE adapter

- [x] 3.1 Add optional `engine` on unsaved test bodies; registered id dispatches that engine; omitted still defaults to MYSQL; unknown id returns `400 VALIDATION_FAILED` without connecting
- [x] 3.2 Keep rejecting name/description/user/product/permission fields on test bodies
- [x] 3.3 Add `kind=NAMESPACE` to `DatabaseItem` without renaming `/databases`, `/tables`, `/table-detail`, or the `database` query/JSON field
- [x] 3.4 Update metadata tests so listed databases include `kind` and table payloads still use `database` as the parent NAMESPACE name

## 4. Frontend catalog and dynamic form

- [x] 4.1 Add engine-catalog types and `GET /api/v1/engines` client; change `Engine` / `JdbcProperties` to catalog-driven (`string` / `Record<string, string>`)
- [x] 4.2 Load the catalog on create/edit; default MYSQL; initialize port/SSL/properties from the descriptor; render type as a select of `displayName`
- [x] 4.3 Drive connection and JDBC controls from the selected descriptor; do not submit keys absent from `propertyFields`
- [x] 4.4 Put `engine` on the form model and send it on create, update, and unsaved tests; stop hard-coding `'MYSQL'` in mappers
- [x] 4.5 Show catalog `displayName` on the data-source list (fallback to raw id); keep the default-database column as the `defaultDatabase` field
- [x] 4.6 Add MSW `GET /api/v1/engines` and update form/list tests to use the catalog fixture

## 5. NAMESPACE tree and editor language

- [x] 5.1 Load each data source's engine descriptor in the SQL workspace and label NAMESPACE/table filters from `resourceTree`
- [x] 5.2 Keep fetching MYSQL NAMESPACE children via `listDatabases`; keep URL/events as `dataSourceId` + `database`
- [x] 5.3 Skip unknown `resourceTree` kinds without breaking the tree
- [x] 5.4 Pass catalog `editorLanguage` into Monaco; MYSQL uses `mysql`; unbound tabs and unknown languages fall back to `mysql`
- [x] 5.5 Leave `sql.ts` as the MySQL splitter; do not add a per-engine frontend scanner in this change

## 6. Docs and verification

- [x] 6.1 Document `GET /api/v1/engines` and the NAMESPACE ↔ `/databases` adapter in `docs/backend-development-spec.md` without renaming existing paths
- [x] 6.2 Run backend unit tests (catalog, registry, connection-test engine, metadata `kind`) and keep ArchUnit: orchestrators still must not depend on `engine.mysql`
- [x] 6.3 Run frontend typecheck/unit tests for form, list, resource tree labels, and Monaco language
- [x] 6.4 Confirm no PostgreSQL/Hive/GBase module, driver, or `PARTITION` UI was added
