## 1. Engine SPI: connection acquisition model (default-compatible)

- [x] 1.1 Add `EngineSupport` defaults: `requiresHostResolution()` (true), `usesPooledConnections()` (true), `openConnection(JdbcTarget)` (`DriverManager.getConnection`)
- [x] 1.2 Add `ENVIRONMENT`/`KEYTAB`/`QUEUE` engine field kinds; keep existing engines unchanged
- [x] 1.3 `JdbcConfigurationBuilder` skips `NetworkPolicy.resolve(host)` when `requiresHostResolution()==false`
- [x] 1.4 `ShortLivedConnectionTester` builds/opens via `engine.openConnection(...)`

## 2. Kerberos + ZooKeeper connector

- [x] 2.1 Add `KerberosHiveConnector` porting `TBDSSecurityHandle`/`KerberosUtil`: keytab login, krb5.conf, realm, `zookeeper.server.principal`, `jdbc:hive2://<zk>/<db>;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2;principal=hadoop/_HOST@<realm>[?tez.queue.name=...]`
- [x] 2.2 Single process lock serializes the login+connect window; snapshot and restore system properties and `UserGroupInformation` in `finally` (success and failure paths)
- [x] 2.3 Enforce CIDR on every configured zk quorum endpoint via `NetworkPolicy`; sanitize keytab/realm/zk from messages
- [x] 2.4 Map Kerberos/krb5/SASL/GSS failures to `AUTHENTICATION_FAILED`/`CONNECTION_FAILED` (sanitized)
- [x] 2.5 Isolate login+connect behind a thin adapter so URL assembly, realm/principal derivation, system-property snapshot/restore, CIDR check, and failure classification are unit-testable without a KDC/cluster
- [x] 2.6 Add always-compiled `KerberosHiveConnector` interface (no vendor imports), `DisabledKerberosHiveConnector` stub (sanitized `CONNECTION_FAILED`), and `KerberosConnectorFactory` that resolves the vendor impl via `Class.forName` and falls back to the stub (the only reflection point)
- [x] 2.7 Port the verified `bddf-public-service` implementation into the conditional source root `service/src/kerberos/java/.../hive_kerberos/vendor/TbdsKerberosHiveConnector.java` (direct `org.apache.hadoop.*` imports), compiled only under the `kerberos-vendor` profile; wire `build-helper-maven-plugin` add-source and keep the default build free of vendor source
- [x] 2.8 Add the `kerberos-vendor` profile (auto-activated by file presence under `service/third-party/hive-auth/`, system-scope jars) and update `.gitignore` + drop-in README

## 3. Server config and persistence

- [x] 3.1 `SqlEditorProperties`: add `kerberos` (`keytab-base-path`, `environments.<env>.{zookeeper-quorum, krb5-conf, realm, principal-template, zookeeper-namespace}`) + `@PostConstruct` validation distinguishing **absent = feature disabled (start OK)** from **present-but-invalid = fail startup**; add `application.yml` placeholders (no live keytab required to boot)
- [x] 3.2 Flyway `V7__hive_kerberos_connection.sql`: relax `host/port/username/password_*` to NULL, add `environment`/`keytab_file`/`queue_name`
- [x] 3.3 `DataSourceRecord`/`DataSourceMapper.xml`/`DataSourceService#configuration/apply` carry the new fields
- [x] 3.4 `ConnectionRequest`/`ConnectionConfiguration` carry `environment`/`keytabFile`/`queueName`
- [x] 3.5 `DataSourceValidator`: descriptor-driven required-field validation (no hardcoded host/username/password/sslMode); keep unsaved-test engine default = MYSQL

## 4. Connection routing without touching the pool

- [x] 4.1 `TargetConnectionProvider.borrow/release/evictor` dispatch on `usesPooledConnections()`; pooled path unchanged
- [x] 4.2 Confirm `DynamicPoolManager` is not modified; MYSQL/PG/GBASE_8A/HIVE behavior identical

## 5. HIVE_KERBEROS engine

- [x] 5.1 Add `EngineId.HIVE_KERBEROS` and `HiveKerberosEngineSupport` `@Order(5)`, `family=HIVE_WIRE`, delegating catalog/scan/explain to `HiveEngineSupport`
- [x] 5.2 Descriptor: `environment`(SELECT, required) + `keytabFile`(TEXT, required) + `queueName`(TEXT, optional) + optional `defaultDatabase`; no host/port/username/password/sslMode
- [x] 5.3 Override `buildJdbc`/`openConnection`/`requiresHostResolution=false`/`usesPooledConnections=false`; classify Kerberos failures
- [x] 5.4 ArchUnit: orchestrators must not depend on `engine.hive_kerberos` or `KerberosHiveConnector`
- [x] 5.5 Response/log redaction: keytab path, realm, zk quorum, krb5.conf never leave the server

## 6. Verification (no live cluster; site E2E is manual)

- [x] 6.1 Unit: URL assembly (with/without queue), realm/principal derivation, global state restored on success and on exception, CIDR rejects out-of-range zk, failure classification, `DynamicPoolManager` not used for Kerberos
- [x] 6.2 Integration: catalog includes HIVE_KERBEROS; create persists `engine=HIVE_KERBEROS`; missing keytab/driver → sanitized `CONNECTION_FAILED` (no live cluster)
- [x] 6.3 Regression: MYSQL/POSTGRESQL/GBASE_8A/HIVE still pooled and unchanged; `SqlEditorProperties` validation passes
- [x] 6.4 Config: absent `sql-editor.kerberos` boots and lists HIVE_KERBEROS; present-but-invalid (missing realm/zk/path) fails startup
- [ ] 6.5 Boot with vendor jars present but no Kerberos data source: application context loads, existing engines and catalog unaffected (guard against Hadoop static-init side effects)
- [x] 6.6 Build/package with the `kerberos-vendor` profile inactive (no vendor jars/source): compiles and boots, catalog still lists all 5 engines including HIVE_KERBEROS, and a HIVE_KERBEROS connect returns the stub's sanitized `CONNECTION_FAILED`
- [x] 6.7 Run backend unit/integration tests

## 7. Site acceptance (manual, after delivery)

- [ ] 7.1 Real TBDS Hive data source: keytab login, ZooKeeper service discovery, browse/execute/export, `queue` applied
- [ ] 7.2 Verify sanitized failures on the site (bad keytab/realm/quorum) and that credentials/keytab/realm never appear in responses or logs
- [ ] 7.3 Record the exact vendor jar set used and confirm transitive completeness on the site classpath
