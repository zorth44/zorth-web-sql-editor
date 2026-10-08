## 1. Hive engine on HIVE_WIRE

- [ ] 1.1 Add `EngineId.HIVE` and `HiveEngineSupport` `@Order(4)` with id `HIVE`, family `HIVE_WIRE`, editorLanguage `hive`, backtick quote, defaultPort 10000, optional `defaultDatabase`
- [ ] 1.2 Implement `HiveCatalogs`/`HiveSqlScanner`/`HiveExplain`/`HiveFailures`: `show databases`, `use <db>` + `show tables`, `desc <table>`, `show create table`; `supportsRelationshipMetadata()=false`, `tableConstraints=unavailable`
- [ ] 1.3 `HiveJdbc` builds `jdbc:hive2://<ip>:<port>/<db>` with IPv6 brackets; whitelist property `hive.metastore.uris` (optional)
- [ ] 1.4 Add ArchUnit: orchestrators must not depend on `engine.hive`
- [ ] 1.5 Add Hive JDBC driver dependency (drop-in `service/third-party/hive` profile first, else pinned Central dependency with exclusions)

## 2. Catalog and unit tests

- [ ] 2.1 Catalog API returns MYSQL, POSTGRESQL, GBASE_8A, HIVE; HIVE has port 10000, family HIVE_WIRE, `hive` language, NAMESPACE 数据库, optional defaultDatabase
- [ ] 2.2 Unit tests: HIVE builds `jdbc:hive2://`, IPv6 brackets, `hive.metastore.uris` allow-list, `show`/`desc` result mapping, missing driver classified `CONNECTION_FAILED` (sanitized), HIVE_KERBEROS/ICEBERG still unregistered
- [ ] 2.3 DataSourceService create with `engine=HIVE` persists that id

## 3. Integration verification

- [ ] 3.1 Integration test: catalog includes HIVE and create persists that engine (no live JDBC)
- [ ] 3.2 Update existing catalog assertions from 3 items to 4

## 4. Frontend

- [ ] 4.1 MSW catalog includes HIVE; form defaults port 10000; registry renders from descriptor
- [ ] 4.2 Register `hive` editor language in `catalog.ts`; Engine type icon for HIVE; form/icon tests

## 5. Docs and verification

- [ ] 5.1 Update backend and frontend specs for four engines
- [ ] 5.2 Run backend unit/integration tests and frontend typecheck/unit tests
- [ ] 5.3 Confirm MYSQL/PG/GBASE_8A regression green, `EngineSupport` and `DynamicPoolManager` unchanged
