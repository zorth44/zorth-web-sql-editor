## 1. Hive engine on HIVE_WIRE

- [x] 1.1 Add failing unit tests for the Hive engine contract: `jdbc:hive2://` URL, IPv6 brackets, `hive.metastore.uris` allow-list accept/reject, `show databases`/`use`+`show tables`/`desc`/`show create table` result mapping, and missing-driver classification as sanitized `CONNECTION_FAILED`
- [x] 1.2 Add `EngineId.HIVE` and implement `HiveEngineSupport` `@Order(4)` with id `HIVE`, family `HIVE_WIRE`, editorLanguage `hive`, backtick quote, defaultPort 10000, optional `defaultDatabase`; make 1.1 tests pass
- [x] 1.3 Implement `HiveCatalogs`/`HiveSqlScanner`/`HiveExplain`/`HiveFailures`: `show databases`, `use <db>` + `show tables`, `desc <table>`, `show create table`; `supportsRelationshipMetadata()=false`, `tableConstraints=unavailable`; make 1.1 mapping tests pass
- [x] 1.4 Implement `HiveJdbc` building `jdbc:hive2://<host>:<port>/<db>` with IPv6 brackets and the `hive.metastore.uris` whitelist (optional); make 1.1 JDBC/property tests pass
- [x] 1.5 Add ArchUnit `orchestratorsDoNotDependOnHiveEngine` (orchestrator packages must not depend on `engine.hive`)
- [x] 1.6 Add Hive JDBC driver dependency (drop-in `service/third-party/hive` profile first, else pinned Central dependency with exclusions)

## 2. Catalog, registry, and service unit tests

- [x] 2.1 Update `EngineCatalogTest` for four engines: HIVE has port 10000, family HIVE_WIRE, `hive` language, backtick quote, NAMESPACE 数据库, optional `defaultDatabase`
- [x] 2.2 Update `EngineRegistryTest`/`DataSourceServiceTest` so `HIVE` is a registered id (use `HIVE_KERBEROS`/`ICEBERG` as the unregistered example) and add a `DataSourceService` create-with-`engine=HIVE` case asserting the persisted id
- [x] 2.3 Unit tests: HIVE_KERBEROS/ICEBERG still unregistered; descriptor `propertyFields` names equal the HIVE runtime allow-list

## 3. Docs and verification

- [x] 3.1 Update backend specs for four engines (spi, catalog, data-source-management, connection-security, backend-hive-engine)
- [x] 3.2 Run backend unit tests; confirm they pass without a live JDBC connection
- [x] 3.3 Confirm MYSQL/PG/GBASE_8A regression green, `EngineSupport` and `DynamicPoolManager` unchanged
- [x] 3.4 Updated existing integration assertions (`BackendIntegrationTest`, `PostgresEngineIntegrationTest`) from three to four engines so `mvn test` stays green; no new integration-test scenarios added
