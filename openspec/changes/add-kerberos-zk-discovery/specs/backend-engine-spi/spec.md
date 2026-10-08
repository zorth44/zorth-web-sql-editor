## MODIFIED Requirements

### Requirement: Registered engine dispatch
The SQL service SHALL resolve every target-database JDBC, metadata, constraint/relationship discovery, statement-scan, connection-failure, and session-restore operation through a registered `EngineSupport` identified by the data source `engine` value. At startup the registry SHALL contain `MYSQL`, `POSTGRESQL`, `GBASE_8A`, `HIVE`, and `HIVE_KERBEROS` in that order. Unknown or unsupported engines MUST fail closed and MUST NOT fall back to another engine's behavior.

#### Scenario: Dispatch a saved MySQL data source
- **WHEN** a visible data source with `engine=MYSQL` is tested, browsed for schema or relationships, executed against, or used for export
- **THEN** the service SHALL use the MYSQL engine implementation and SHALL NOT assemble a `jdbc:mysql://` URL or MySQL-only property/SSL flags in the data-source, pool, execution, metadata, or history orchestrators

#### Scenario: Dispatch a saved Kerberos Hive data source
- **WHEN** a visible data source with `engine=HIVE_KERBEROS` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the HIVE_KERBEROS engine implementation with databases as NAMESPACE and SHALL connect through the Kerberos + ZooKeeper connector

#### Scenario: Reject an unregistered engine on write
- **WHEN** a create or update submits `engine` other than a registered id
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

#### Scenario: Fail closed on an unknown persisted engine
- **WHEN** a saved row's `engine` is not in the registry
- **THEN** the service SHALL fail the operation with a stable error without opening a target connection and SHALL NOT fall back to MYSQL behavior

## ADDED Requirements

### Requirement: Engine declares connection acquisition model
Each `EngineSupport` SHALL let an engine declare how a target connection is acquired, so orchestrators never branch on engine id. It SHALL expose `requiresHostResolution()`, `usesPooledConnections()`, and `openConnection(JdbcTarget)`. The defaults SHALL keep host resolution, pooled connections, and `DriverManager.getConnection`, so existing engines are unchanged. `TargetConnectionProvider` and `ShortLivedConnectionTester` SHALL acquire connections through `openConnection`, and `JdbcConfigurationBuilder` SHALL skip host resolution when `requiresHostResolution()` is false.

#### Scenario: Pooled engine is unchanged
- **WHEN** MYSQL, POSTGRESQL, GBASE_8A, or HIVE is used
- **THEN** the service SHALL resolve the host, borrow from the dynamic pool, and behave exactly as before the change

#### Scenario: Non-host engine skips network resolution
- **WHEN** an engine reports `requiresHostResolution()=false`
- **THEN** building its JDBC target SHALL NOT call `NetworkPolicy.resolve` on a submitted host

#### Scenario: Orchestrators stay engine-neutral
- **WHEN** metadata, execution, export, or connection test acquires a connection
- **THEN** the service SHALL call `EngineSupport.openConnection` and the data-source/pool/test orchestrators SHALL NOT contain an engine-id branch
