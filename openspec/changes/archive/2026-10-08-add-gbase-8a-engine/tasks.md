## 1. GBase 8a engine on MYSQL_WIRE

- [x] 1.1 Add `EngineId.GBASE_8A` and `Gbase8aEngineSupport` `@Order(3)` that delegates catalog/scan/failures to `MysqlEngineSupport`
- [x] 1.2 Descriptor: displayName GBase 8a, family MYSQL_WIRE, defaultPort 5258, mysql language, backtick quote, optional defaultDatabase, NAMESPACE 数据库
- [x] 1.3 Add ArchUnit: orchestrators must not depend on `engine.gbase8a`
- [x] 1.4 Rewrite JDBC to `jdbc:gbase://` and load `com.gbase.jdbc.Driver`; Maven drop-in path for official `gbase-connector-java.jar`

## 2. Catalog and unit tests

- [x] 2.1 Catalog API returns MYSQL, POSTGRESQL, GBASE_8A; GBASE_8A has port 5258, mysql, optional defaultDatabase, NAMESPACE 数据库
- [x] 2.2 Unit tests: GBASE_8A builds `jdbc:gbase://`, MYSQL property keys, missing defaultDatabase allowed, missing official driver classified, GBASE_8C/HIVE still unregistered
- [x] 2.3 DataSourceService create with `engine=GBASE_8A` persists that id

## 3. Integration verification

- [x] 3.1 Integration test: catalog includes GBASE_8A and create persists that engine (no live JDBC against MySQL Testcontainers)
- [x] 3.2 Update PostgreSQL integration catalog assertion from 2 items to 3

## 4. Frontend

- [x] 4.1 MSW catalog includes GBASE_8A; form defaults port 5258; switching from PG drops ApplicationName and restores mysql property keys
- [x] 4.2 Engine type icon for GBASE_8A; form test covers the GBase 8a card

## 5. Docs and verification

- [x] 5.1 Update backend and frontend specs for three engines; GBase 8a hangs on MYSQL_WIRE with official JDBC
- [x] 5.2 Run backend unit/integration tests and frontend typecheck/unit tests
- [x] 5.3 Confirm GBase 8c / 8s / Hive were not added and EngineSupport was not changed
