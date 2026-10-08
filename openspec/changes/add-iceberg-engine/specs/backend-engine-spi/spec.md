## MODIFIED Requirements

### Requirement: Registered engine dispatch
The SQL service SHALL resolve every target-database JDBC, metadata, constraint/relationship discovery, statement-scan, connection-failure, and session-restore operation through a registered `EngineSupport` identified by the data source `engine` value. At startup the registry SHALL contain `MYSQL`, `POSTGRESQL`, `GBASE_8A`, `HIVE`, `HIVE_KERBEROS`, and `ICEBERG` in that order. Unknown or unsupported engines MUST fail closed and MUST NOT fall back to another engine's behavior.

#### Scenario: Dispatch a saved MySQL data source
- **WHEN** a visible data source with `engine=MYSQL` is tested, browsed for schema or relationships, executed against, or used for export
- **THEN** the service SHALL use the MYSQL engine implementation and SHALL NOT assemble a `jdbc:mysql://` URL or MySQL-only property/SSL flags in the data-source, pool, execution, metadata, or history orchestrators

#### Scenario: Dispatch a saved Iceberg data source
- **WHEN** a visible data source with `engine=ICEBERG` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the ICEBERG engine implementation with databases as NAMESPACE and SHALL connect through the Kerberos + ZooKeeper connector

#### Scenario: Reject an unregistered engine on write
- **WHEN** a create or update submits `engine` other than a registered id
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

#### Scenario: Fail closed on an unknown persisted engine
- **WHEN** a saved row's `engine` is not in the registry
- **THEN** the service SHALL fail the operation with a stable error without opening a target connection and SHALL NOT fall back to MYSQL behavior
