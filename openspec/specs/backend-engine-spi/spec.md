# Backend Engine SPI Specification

## Purpose

Define the target-database engine registry and how saved or unsaved connections dispatch JDBC, metadata, statement scanning, connection-failure classification, and session restore through a registered `EngineSupport`.

## Requirements

### Requirement: Registered engine dispatch
The SQL service SHALL resolve every target-database JDBC, metadata, statement-scan, connection-failure, and session-restore operation through a registered `EngineSupport` identified by the data source `engine` value. At startup the registry SHALL contain `MYSQL` and `POSTGRESQL` in that order.

#### Scenario: Dispatch a saved MySQL data source
- **WHEN** a visible data source with `engine=MYSQL` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the MYSQL engine implementation and SHALL NOT assemble a `jdbc:mysql://` URL or MySQL-only property/SSL flags in the data-source, pool, execution, metadata, or history orchestrators

#### Scenario: Dispatch a saved PostgreSQL data source
- **WHEN** a visible data source with `engine=POSTGRESQL` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the POSTGRESQL engine implementation

#### Scenario: Reject an unregistered engine on write
- **WHEN** a create or update submits `engine` other than a registered id
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

#### Scenario: Fail closed on an unknown persisted engine
- **WHEN** a saved row's `engine` is not in the registry
- **THEN** the service SHALL fail the operation with a stable error without opening a target connection and SHALL NOT fall back to MYSQL behavior

### Requirement: Engine publishes a catalog descriptor
Each `EngineSupport` SHALL expose an `EngineDescriptor` used by the engine catalog API. The descriptor SHALL be derived from the same engine implementation that validates JDBC properties and lists namespaces.

#### Scenario: MYSQL descriptor matches runtime allow-list
- **WHEN** the MYSQL engine reports its descriptor
- **THEN** `propertyFields` names SHALL be exactly the keys accepted by MYSQL property validation, and `connectionFields` SHALL cover the structured connection fields the engine already consumes

#### Scenario: POSTGRESQL descriptor matches runtime allow-list
- **WHEN** the POSTGRESQL engine reports its descriptor
- **THEN** `propertyFields` names SHALL be exactly the keys accepted by POSTGRESQL property validation, `connectionFields` SHALL include `defaultDatabase` as required, and `resourceTree` first level SHALL be `NAMESPACE` with `listEndpoint=databases`

#### Scenario: Registry lists descriptors without target I/O
- **WHEN** the registry is asked for descriptors
- **THEN** it SHALL return the in-memory descriptor of every registered engine and SHALL NOT open a target connection

### Requirement: Connection configuration carries engine
Saved connection configuration used to build JDBC targets and borrow pools SHALL include the persisted `engine` id.

#### Scenario: Borrow a pool for a saved source
- **WHEN** the service decrypts a saved data source for pooling, metadata, execution, or export
- **THEN** the connection configuration SHALL include `engine` equal to the row's `engine`

#### Scenario: Test an unsaved configuration without engine
- **WHEN** `POST /api/v1/data-sources:test` or an unsaved-edit test body omits `engine`
- **THEN** the service SHALL dispatch the MYSQL engine

#### Scenario: Test an unsaved configuration with engine
- **WHEN** a test body includes `engine` equal to a registered id
- **THEN** the service SHALL dispatch that engine and SHALL NOT ignore the field in favor of MYSQL
