## ADDED Requirements

### Requirement: Registered engine dispatch
The SQL service SHALL resolve every target-database JDBC, metadata, statement-scan, connection-failure, and session-restore operation through a registered `EngineSupport` identified by the data source `engine` value. At startup the registry SHALL contain exactly `MYSQL` until a later change adds more engines.

#### Scenario: Dispatch a saved MySQL data source
- **WHEN** a visible data source with `engine=MYSQL` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the MYSQL engine implementation and SHALL NOT assemble a `jdbc:mysql://` URL or MySQL-only property/SSL flags in the data-source, pool, execution, metadata, or history orchestrators

#### Scenario: Reject an unregistered engine on write
- **WHEN** a create or update submits `engine` other than a registered id
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

#### Scenario: Fail closed on an unknown persisted engine
- **WHEN** a saved row's `engine` is not in the registry
- **THEN** the service SHALL fail the operation with a stable error without opening a target connection and SHALL NOT fall back to MYSQL behavior

### Requirement: Connection configuration carries engine
Saved connection configuration used to build JDBC targets and borrow pools SHALL include the persisted `engine` id.

#### Scenario: Borrow a pool for a saved source
- **WHEN** the service decrypts a saved data source for pooling, metadata, execution, or export
- **THEN** the connection configuration SHALL include `engine` equal to the row's `engine`

#### Scenario: Test an unsaved configuration without engine
- **WHEN** `POST /api/v1/data-sources:test` or an unsaved-edit test body omits `engine`
- **THEN** the service SHALL dispatch the MYSQL engine (the only registered engine) and SHALL keep the public test request shape unchanged
