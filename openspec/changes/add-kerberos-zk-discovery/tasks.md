## 1. Engine SPI: connection acquisition model (default-compatible)

- [ ] 1.1 Add `EngineSupport` defaults: `requiresHostResolution()` (true), `usesPooledConnections()` (true), `openConnection(JdbcTarget)` (`DriverManager.getConnection`)
- [ ] 1.2 Add `ENVIRONMENT`/`KEYTAB`/`QUEUE` engine field kinds; keep existing engines unchanged
- [ ] 1.3 `JdbcConfigurationBuilder` skips `NetworkPolicy.resolve(host)` when `requiresHostResolution()==false`
- [ ] 1.4 `ShortLivedConnectionTester` builds/opens via `engine.openConnection(...)`

## 2. Kerberos + ZooKeeper connector

- [ ] 2.1 Add `KerberosHiveConnector` porting `TBDSSecurityHandle`/`KerberosUtil`: keytab login, krb5.conf, realm, `zookeeper.server.principal`, `jdbc:hive2://<zk>/<db>;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2;principal=hadoop/_HOST@<realm>[?tez.queue.name=...]`
- [ ] 2.2 Single process lock serializes the login+connect window; snapshot and restore system properties and `UserGroupInformation` in `finally` (success and failure paths)
- [ ] 2.3 Enforce CIDR on every configured zk quorum endpoint via `NetworkPolicy`; sanitize keytab/realm/zk from messages
- [ ] 2.4 Map Kerberos/krb5/SASL/GSS failures to `AUTHENTICATION_FAILED`/`CONNECTION_FAILED` (sanitized)

## 3. Server config and persistence

- [ ] 3.1 `SqlEditorProperties`: add `kerberos` (`keytab-base-path`, `environments.<env>.{zookeeper-quorum, krb5-conf, realm, principal-template, zookeeper-namespace}`) + `@PostConstruct` validation; add `application.yml` placeholders
- [ ] 3.2 Flyway `V7__hive_kerberos_connection.sql`: relax `host/port/username/password_*` to NULL, add `environment`/`keytab_file`/`queue_name`
- [ ] 3.3 `DataSourceRecord`/`DataSourceMapper.xml`/`DataSourceService#configuration/apply` carry the new fields
- [ ] 3.4 `ConnectionRequest`/`ConnectionConfiguration` carry `environment`/`keytabFile`/`queueName`
- [ ] 3.5 `DataSourceValidator`: descriptor-driven required-field validation (no hardcoded host/username/password/sslMode); keep unsaved-test engine default = MYSQL

## 4. Connection routing without touching the pool

- [ ] 4.1 `TargetConnectionProvider.borrow/release/evictor` dispatch on `usesPooledConnections()`; pooled path unchanged
- [ ] 4.2 Confirm `DynamicPoolManager` is not modified; MYSQL/PG/GBASE_8A/HIVE behavior identical

## 5. HIVE_KERBEROS engine

- [ ] 5.1 Add `EngineId.HIVE_KERBEROS` and `HiveKerberosEngineSupport` `@Order(5)`, `family=HIVE_WIRE`, delegating catalog/scan/explain to `HiveEngineSupport`
- [ ] 5.2 Descriptor: `environment`(SELECT, required) + `keytabFile`(TEXT, required) + `queueName`(TEXT, optional) + optional `defaultDatabase`; no host/port/username/password/sslMode
- [ ] 5.3 Override `buildJdbc`/`openConnection`/`requiresHostResolution=false`/`usesPooledConnections=false`; classify Kerberos failures
- [ ] 5.4 ArchUnit: orchestrators must not depend on `engine.hive_kerberos` or `KerberosHiveConnector`
- [ ] 5.5 Response/log redaction: keytab path, realm, zk quorum, krb5.conf never leave the server

## 6. Frontend

- [ ] 6.1 `DataSourceFormModel`/contracts add optional `environment`/`keytabFile`/`queueName`
- [ ] 6.2 `DataSourceForm.vue` renders the new kinds; `model.ts` submits only descriptor-declared fields; `validation.ts` validates per descriptor
- [ ] 6.3 MSW catalog includes HIVE_KERBEROS; create/edit/test flows + component tests
- [ ] 6.4 `frontend-sql-editor-workbench`: HIVE_KERBEROS NAMESPACE navigator + `hive` editor language

## 7. Verification

- [ ] 7.1 Unit: URL assembly (with/without queue), realm/principal derivation, global state restored on success and on exception, CIDR rejects out-of-range zk, failure classification, `DynamicPoolManager` not used for Kerberos
- [ ] 7.2 Integration: catalog includes HIVE_KERBEROS; create persists `engine=HIVE_KERBEROS`; missing keytab/driver → sanitized `CONNECTION_FAILED` (no live cluster)
- [ ] 7.3 Regression: MYSQL/POSTGRESQL/GBASE_8A/HIVE still pooled and unchanged; `SqlEditorProperties` validation passes
- [ ] 7.4 Run backend unit/integration tests and frontend typecheck/unit tests
