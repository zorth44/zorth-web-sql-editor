## 1. ICEBERG engine on the Kerberos mechanism

- [ ] 1.1 Add `EngineId.ICEBERG` and `IcebergEngineSupport` `@Order(6)`, `family=HIVE_WIRE`, delegating catalog/scan/explain to `HiveEngineSupport`
- [ ] 1.2 Reuse `KerberosHiveConnector`; select keytab subdirectory `iceberg`; `requiresHostResolution=false`, `usesPooledConnections=false`
- [ ] 1.3 Descriptor: displayName Iceberg, defaultPort 10000, `hive` language, backtick quote, fields `environment`/`keytabFile`/`queueName`/optional `defaultDatabase`
- [ ] 1.4 Add ArchUnit: orchestrators must not depend on `engine.iceberg`

## 2. Catalog and unit tests

- [ ] 2.1 Catalog API returns MYSQL, POSTGRESQL, GBASE_8A, HIVE, HIVE_KERBEROS, ICEBERG; ICEBERG fields match HIVE_KERBEROS
- [ ] 2.2 Unit tests: ICEBERG resolves keytab under `iceberg/`, builds the same service-discovery URL, classified failures sanitized
- [ ] 2.3 DataSourceService create with `engine=ICEBERG` persists that id

## 3. Integration verification

- [ ] 3.1 Integration test: catalog includes ICEBERG and create persists that engine (no live cluster)
- [ ] 3.2 Update catalog assertions from 5 items to 6

## 4. Frontend

- [ ] 4.1 MSW catalog includes ICEBERG; form renders Kerberos fields from the descriptor
- [ ] 4.2 Engine type icon for ICEBERG; form/icon tests

## 5. Docs and verification

- [ ] 5.1 Update backend and frontend specs for six engines
- [ ] 5.2 Run backend unit/integration tests and frontend typecheck/unit tests
- [ ] 5.3 Confirm only a new engine was added: no `EngineSupport`, `KerberosHiveConnector`, or `DynamicPoolManager` behavior change
