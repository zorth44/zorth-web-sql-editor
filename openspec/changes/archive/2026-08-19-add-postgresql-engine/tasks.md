## 1. SPI and PostgreSQL engine

- [x] 1.1 Add `EngineId.POSTGRESQL`, `@Order` on MYSQL (1) and POSTGRESQL (2), and SPI defaults `applyConnectTimeout` / `streamingFetchSize`
- [x] 1.2 Add `identifierQuote` on `EngineDescriptor` (MYSQL backtick, POSTGRESQL double quote)
- [x] 1.3 Implement `engine.postgres`: JDBC URL/SSL/property allow-list, schema metadata, dollar-quote scanner, failure classification, `search_path` restore
- [x] 1.4 Require `defaultDatabase` for POSTGRESQL in `DataSourceValidator`; keep MYSQL optional
- [x] 1.5 Wire `ShortLivedConnectionTester` and CSV export to the new SPI defaults
- [x] 1.6 Add ArchUnit: orchestrators must not depend on `engine.postgres`

## 2. Catalog and unit tests

- [x] 2.1 Catalog API returns MYSQL then POSTGRESQL; POSTGRESQL descriptor has port 5432, `pgsql`, required `defaultDatabase`, NAMESPACE label 模式, `listEndpoint=databases`
- [x] 2.2 Unit tests: property key sets match validator; scanner dollar-quotes; JDBC IPv6/SSL; missing defaultDatabase; unregistered engine is not POSTGRESQL (use HIVE/ORACLE)
- [x] 2.3 Metadata unit test: PG lists schemas, hides `pg_catalog` / `information_schema`, tables bound to schema name

## 3. Testcontainers verification

- [x] 3.1 Add PostgreSQL driver (runtime) and `testcontainers-postgresql`
- [x] 3.2 Integration test: create PG source, test connection, list schemas as NAMESPACE, hide system schemas, execute with `database=<schema>`, auth/database failure codes

## 4. Frontend

- [x] 4.1 MSW catalog includes POSTGRESQL; form requires defaultDatabase; switching engine drops MYSQL property keys
- [x] 4.2 Register Monaco `pgsql`; `editorLanguageFor` returns `pgsql` for POSTGRESQL and still falls back for unknown languages
- [x] 4.3 Quote identifiers from `identifierQuote`; resource tree uses 模式 labels; dollar-quote in `sql.ts`

## 5. Docs and verification

- [x] 5.1 Update `docs/backend-development-spec.md` (and frontend spec if needed) for two engines and schema-as-NAMESPACE
- [x] 5.2 Run backend unit/integration tests and frontend typecheck/unit tests
- [x] 5.3 Confirm Hive/GBase were not added
