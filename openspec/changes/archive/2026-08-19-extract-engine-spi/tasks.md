## 1. Engine SPI skeleton

- [x] 1.1 Add `com.bocsoft.sqleditor.engine` with `EngineId.MYSQL`, `EngineSupport`, and `EngineRegistry` that indexes Spring `EngineSupport` beans by id
- [x] 1.2 Give `EngineSupport` methods for property validation, JDBC target build, connection-failure classification, namespace apply/restore, metadata list/detail, statement split/require-single, identifier quoting, and MySQL-matching capability flags
- [x] 1.3 Add `EngineRegistry.require` so unknown ids fail closed without opening a target connection
- [x] 1.4 Extend `ArchitectureTest` so `datasource` (orchestrators), `execution`, `metadata`, `history`, `export`, `auth`, and `common` cannot depend on `..engine.mysql..`, and `engine` cannot depend on controllers

## 2. Thread engine through saved connections

- [x] 2.1 Add `engine` to `ConnectionConfiguration` and `SavedDataSource`
- [x] 2.2 Populate `engine` from `DataSourceRecord` in `DataSourceService.configuration` / `requireSaved`
- [x] 2.3 Change `DataSourceValidator` so create/update `engine` must be a registered id (currently only `MYSQL`) instead of a string-equals check
- [x] 2.4 Keep unsaved connection-test bodies without `engine` and dispatch `MYSQL` as the default registered engine

## 3. Move MySQL JDBC and session behavior

- [x] 3.1 Implement `engine.mysql.MysqlEngineSupport` and move JDBC URL/SSL/property whitelist construction into it
- [x] 3.2 Move MySQL connection-failure classification (1045/1049, Connector/J types, sanitized messages) onto the MySQL engine
- [x] 3.3 Point `JdbcConfigurationBuilder`, `ShortLivedConnectionTester`, and `DynamicPoolManager` at `EngineRegistry` instead of concatenating `jdbc:mysql://` themselves
- [x] 3.4 Move catalog apply/restore (including blank `defaultDatabase` skip and leftover-`USE` eviction) onto the MySQL engine; keep generic try/finally close in `ConnectionUse`
- [x] 3.5 Relocate existing JDBC security and `ConnectionUse` tests to cover the MySQL engine without weakening assertions

## 4. Move MySQL metadata and SQL scanning

- [x] 4.1 Move catalog listing, system-schema hiding, tables/views, table detail, and `SHOW CREATE TABLE` from `MysqlMetadataService` into the MySQL engine
- [x] 4.2 Make `MetadataController` resolve the saved data source engine and dispatch; keep `/databases`, `/tables`, `/table-detail` shapes unchanged
- [x] 4.3 Move backtick/`#`/string-aware statement splitting into the MySQL engine; `SqlExecutionService.requireSingle` must call the engine
- [x] 4.4 Leave `SqlStatementClassifier` in `execution` for this change
- [x] 4.5 Keep frontend `sql.ts` scanner unchanged (MySQL rules); backend remains the authority for `MULTI_STATEMENT_NOT_SUPPORTED`

## 5. Rename vendor error code

- [x] 5.1 Add Flyway migration renaming `sql_execution_history.mysql_error_code` to `vendor_error_code`
- [x] 5.2 Rename Java/MyBatis fields and history detail JSON from `mysqlErrorCode` to `vendorErrorCode`
- [x] 5.3 Persist `SQLException.getErrorCode()` as `vendorErrorCode` on `422 SQL_EXECUTION_FAILED` details and FAILED history
- [x] 5.4 Update `docs/backend-development-spec.md` JDBC-dispatch and error-code field names
- [x] 5.5 Change frontend `contracts.ts`, MSW handlers, and error/history display to `vendorErrorCode` only

## 6. Verification

- [x] 6.1 Add unit tests: unregistered engine on create/update; missing engine on unsaved test defaults to MYSQL; persisted unknown engine fails closed
- [x] 6.2 Re-run `NetworkAndJdbcSecurityTest`, metadata tests, execution scanner tests, history tests, and `BackendIntegrationTest` and keep current MySQL behavior
- [x] 6.3 Run frontend typecheck/unit tests for contracts and mocks after the error-code rename
- [x] 6.4 Confirm ArchUnit rules fail if an orchestrator references `engine.mysql` types directly
