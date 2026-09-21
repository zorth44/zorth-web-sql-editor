## MODIFIED Requirements

### Requirement: Registered engine dispatch
The SQL service SHALL resolve every target-database JDBC, metadata, constraint/relationship discovery, statement-scan, connection-failure, and session-restore operation through a registered `EngineSupport` identified by the datasource engine. At startup the registry SHALL contain the configured registered engines in stable order. Unknown or unsupported engines MUST fail closed and MUST NOT fall back to another engine's relationship behavior.

#### Scenario: Dispatch a saved MySQL data source
- **WHEN** a visible datasource with `engine=MYSQL` is browsed for schema or relationships
- **THEN** the service uses the MYSQL engine implementation and no orchestrator assembles MySQL-specific metadata SQL

#### Scenario: Dispatch a saved PostgreSQL data source
- **WHEN** a visible datasource with `engine=POSTGRESQL` is browsed for schema or relationships
- **THEN** the service uses the POSTGRESQL engine implementation with schemas as NAMESPACE

#### Scenario: Reject an unregistered engine on write
- **WHEN** a create or update submits an unregistered engine id
- **THEN** the service returns `400 VALIDATION_FAILED` without persistence or target connection

#### Scenario: Fail closed on unknown or unsupported relationship behavior
- **WHEN** a persisted engine is unknown or its relationship capability is unavailable
- **THEN** the service returns a stable error/coverage and does not fall back to MYSQL or guess metadata

## ADDED Requirements

### Requirement: Engine SPI returns canonical constraints and one-hop relationships
Each supporting `EngineSupport` SHALL return engine-neutral unique-key, foreign-key, index and imported/exported relationship models through its catalog adapter. Composite metadata rows SHALL be grouped atomically by real constraint identity and ordered by key sequence. The SPI MUST NOT expose `ResultSet`, `DatabaseMetaData`, vendor DTOs, credentials, or SQL text to metadata/Agent controllers.

#### Scenario: Engine groups a composite foreign key
- **WHEN** JDBC metadata returns multiple KEY_SEQ rows for one foreign key
- **THEN** the engine returns one relationship with ordered, equal-length source/target columns

#### Scenario: Engine cannot distinguish unique constraint from unique index
- **WHEN** the driver proves uniqueness but not declaration type
- **THEN** the engine reports `UNIQUE_INDEX` evidence rather than fabricating `UNIQUE_CONSTRAINT`

#### Scenario: Metadata capability is absent
- **WHEN** a driver cannot reliably read imported/exported keys
- **THEN** the engine reports capability unavailable and never returns COMPLETE merely because its result is empty

### Requirement: Engine metadata operations are bounded before mapping
Constraint, index and relationship collection SHALL accept provider hard limits and stop or page before returning an unbounded aggregate. The engine/catalog layer SHALL supply deterministic safe sort keys so composite relationships remain atomic and cursors are stable.

#### Scenario: Index count reaches the cap
- **WHEN** table indexes exceed the provider maximum
- **THEN** collection returns no more than the cap and marks index coverage TRUNCATED

#### Scenario: Relationship page continues
- **WHEN** grouped one-hop edges exceed effective pageSize
- **THEN** the service returns a stable next cursor without splitting a composite relationship
