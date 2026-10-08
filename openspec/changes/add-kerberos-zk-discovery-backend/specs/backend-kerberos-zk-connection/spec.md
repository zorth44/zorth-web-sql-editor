## ADDED Requirements

### Requirement: Kerberos service-discovery connection
The SQL service SHALL support connecting to HiveServer2 through Kerberos authentication and ZooKeeper service discovery. The connector SHALL authenticate with a keytab and build `jdbc:hive2://<zkQuorum>/<database>;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2;principal=hadoop/_HOST@<realm>` (appending `?tez.queue.name=<queue>` when a queue is configured). It SHALL be used only by engines that report `usesPooledConnections()=false`.

#### Scenario: Build a service-discovery URL
- **WHEN** a Kerberos engine connects with environment `E`, database `d`, and optional queue `q`
- **THEN** the connector SHALL obtain the zk quorum, krb5.conf, realm, and zookeeper namespace from that environment's server-side configuration and SHALL connect with a `jdbc:hive2://` URL using `serviceDiscoveryMode=zooKeeper` and `principal=hadoop/_HOST@<realm>`, appending `?tez.queue.name=q` when `q` is present

#### Scenario: Environment is unknown
- **WHEN** a saved row references an environment absent from server configuration
- **THEN** the connection attempt SHALL fail closed with a sanitized connection failure and SHALL NOT open a connection

### Requirement: Kerberos global state is serialized and restored
Kerberos login mutates JVM-global state (`java.security.krb5.conf`, default realm, ZooKeeper server principal, `UserGroupInformation`). The service SHALL serialize the login-and-connect window with a single process lock and SHALL restore the prior system properties and reset `UserGroupInformation` in a `finally` block on both success and failure, so one data source cannot leak realm or ticket state into another.

#### Scenario: Concurrent Kerberos connects do not interleave
- **WHEN** two Kerberos connections to different environments or realms are requested concurrently
- **THEN** the service SHALL run their login-and-connect windows one at a time

#### Scenario: State is restored after a failed connect
- **WHEN** a Kerberos login or connect throws
- **THEN** the service SHALL still restore the prior system properties and reset `UserGroupInformation` before releasing the lock

### Requirement: Kerberos server-side configuration and keytabs
Kerberos zk quorum, krb5.conf, realm, principal template, and zookeeper namespace SHALL come from server configuration keyed by environment. The SQL service SHALL resolve a saved row's keytab file name against a configured keytab base path and SHALL NOT persist or return the resolved absolute path. Configuration SHALL be validated in two distinct states: a wholly absent Kerberos configuration SHALL leave the feature disabled without failing startup, while a present-but-invalid configuration SHALL fail startup with a safe error.

#### Scenario: Resolve a keytab file
- **WHEN** a saved Kerberos row has environment `E` and keytab file name `f`
- **THEN** the connector SHALL load `<keytab-base-path>/<engine-subdirectory>/f` and SHALL NOT expose that path in any response or log

#### Scenario: Absent configuration disables the feature
- **WHEN** no `sql-editor.kerberos` configuration is present
- **THEN** the service SHALL start successfully, SHALL still list `HIVE_KERBEROS` in the catalog, and a `HIVE_KERBEROS` connection attempt SHALL fail closed with a sanitized `CONNECTION_FAILED` without affecting any other engine

#### Scenario: Invalid configuration fails startup
- **WHEN** a Kerberos configuration is present but an environment omits realm or zookeeper quorum, or the keytab base path is blank
- **THEN** the service SHALL fail startup with a safe configuration error

### Requirement: Kerberos capability degrades safely without vendor components
The service SHALL build and start whether or not the vendor Kerberos and Hive authentication components are present on the classpath. When a `HIVE_KERBEROS` connection is attempted without them, the service SHALL fail closed with a sanitized `CONNECTION_FAILED` and SHALL NOT affect other engines.

#### Scenario: Start without vendor components
- **WHEN** the service starts without the vendor Hadoop/Hive authentication jars present
- **THEN** it SHALL start successfully, SHALL still list `HIVE_KERBEROS` in the engine catalog, and SHALL continue to serve the other engines unchanged

#### Scenario: Connect without vendor components
- **WHEN** a `HIVE_KERBEROS` connection is attempted and the vendor components are absent
- **THEN** the service SHALL return a sanitized `CONNECTION_FAILED` and SHALL NOT reveal internal paths or class names

### Requirement: Kerberos metadata and query semantics
A Kerberos Hive connection SHALL expose the same Hive catalog and execution behavior as the HIVE engine (databases as NAMESPACE, `show databases`/`show tables`/`desc`/`show create table`), and SHALL report relationship metadata as unavailable.

#### Scenario: Browse a Kerberos Hive data source
- **WHEN** a visible Kerberos Hive data source is browsed
- **THEN** the service SHALL list namespaces, tables, and columns through the Hive catalog behavior and SHALL NOT report relationship coverage as complete
