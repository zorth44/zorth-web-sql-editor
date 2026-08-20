## MODIFIED Requirements

### Requirement: Registered engine dispatch
The SQL service SHALL resolve every target-database JDBC, metadata, statement-scan, connection-failure, and session-restore operation through a registered `EngineSupport` identified by the data source `engine` value. At startup the registry SHALL contain `MYSQL`, `POSTGRESQL`, and `GBASE_8A` in that order.

#### Scenario: Dispatch a saved MySQL data source
- **WHEN** a visible data source with `engine=MYSQL` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the MYSQL engine implementation and SHALL NOT assemble a `jdbc:mysql://` URL or MySQL-only property/SSL flags in the data-source, pool, execution, metadata, or history orchestrators

#### Scenario: Dispatch a saved PostgreSQL data source
- **WHEN** a visible data source with `engine=POSTGRESQL` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the POSTGRESQL engine implementation

#### Scenario: Dispatch a saved GBase 8a data source
- **WHEN** a visible data source with `engine=GBASE_8A` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the GBASE_8A engine implementation

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

#### Scenario: GBASE_8A descriptor matches MYSQL_WIRE allow-list
- **WHEN** the GBASE_8A engine reports its descriptor
- **THEN** `family` SHALL be `MYSQL_WIRE`, `propertyFields` names SHALL match MYSQL property validation, `defaultDatabase` SHALL be optional, and `resourceTree` first level SHALL be `NAMESPACE` with `listEndpoint=databases`

#### Scenario: Registry lists descriptors without target I/O
- **WHEN** the registry is asked for descriptors
- **THEN** it SHALL return the in-memory descriptor of every registered engine and SHALL NOT open a target connection
