## ADDED Requirements

### Requirement: Engine publishes a catalog descriptor
Each `EngineSupport` SHALL expose an `EngineDescriptor` used by the engine catalog API. The descriptor SHALL be derived from the same engine implementation that validates JDBC properties and lists namespaces.

#### Scenario: MYSQL descriptor matches runtime allow-list
- **WHEN** the MYSQL engine reports its descriptor
- **THEN** `propertyFields` names SHALL be exactly the keys accepted by MYSQL property validation, and `connectionFields` SHALL cover the structured connection fields the engine already consumes

#### Scenario: Registry lists descriptors without target I/O
- **WHEN** the registry is asked for descriptors
- **THEN** it SHALL return the in-memory descriptor of every registered engine and SHALL NOT open a target connection

## MODIFIED Requirements

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
