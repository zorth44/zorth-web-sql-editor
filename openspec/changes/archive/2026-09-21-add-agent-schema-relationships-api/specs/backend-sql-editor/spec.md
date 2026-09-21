## MODIFIED Requirements

### Requirement: Navigable MySQL metadata
The SQL service SHALL expose paginated database, table/view, table-detail, column-search, and bounded table-relationship capabilities using safe engine-dispatched JDBC metadata access. Existing Web endpoints SHALL retain their response contracts; canonical constraint and relationship aggregates SHALL be reusable by the Agent adapter.

#### Scenario: List databases
- **WHEN** the databases endpoint receives valid paging/filter input
- **THEN** it returns a stable cursor page and hides system databases by default

#### Scenario: List databases without a default database
- **WHEN** a saved MySQL datasource has no defaultDatabase but can see non-system databases
- **THEN** visible databases are returned without failing solely due to the missing default

#### Scenario: List tables and views
- **WHEN** tables are requested for a visible database
- **THEN** the service returns bounded matching objects with safe fields

#### Scenario: Read table structure
- **WHEN** reusable metadata requests a visible MySQL table
- **THEN** the engine returns ordered columns, primary key, ordered indexes, bounded unique/outbound foreign keys and explicit coverage while the existing Web DTO remains compatible

#### Scenario: Read one-hop relationships
- **WHEN** reusable metadata requests inbound/outbound relationships for one visible table
- **THEN** the engine returns bounded atomic edges without scanning a second relationship hop

#### Scenario: Reject unsafe metadata input
- **WHEN** a database/table identifier, direction, page size, limit or cursor is malformed/tampered
- **THEN** the service returns a stable validation/not-found error and does not concatenate input into executable SQL

### Requirement: Engine-dispatched metadata and statement scanning
Metadata listing, constraint/relationship discovery, and single-statement scanning SHALL use the datasource's registered engine. Shared metadata and Agent orchestrators MUST NOT branch on engine ids, issue vendor metadata SQL, directly inspect JDBC metadata, or fall back from an unsupported engine to MySQL-family behavior.

#### Scenario: Browse MySQL metadata through the engine
- **WHEN** metadata or relationships run against a MYSQL datasource
- **THEN** the MYSQL engine owns catalog/constraint operations and system-schema hiding

#### Scenario: Browse PostgreSQL metadata through the engine
- **WHEN** metadata or relationships run against a POSTGRESQL datasource
- **THEN** the POSTGRESQL engine owns schema-based catalog/constraint operations

#### Scenario: Reject extra statements with the engine scanner
- **WHEN** execution text contains multiple statements after engine-aware lexical handling
- **THEN** the service rejects it before target execution

## ADDED Requirements

### Requirement: Reusable relationship metadata remains product isolated
Every relationship or enriched schema operation SHALL authorize the datasource against the current product before borrowing a target connection. It SHALL use only that saved datasource and database account visibility, and ordinary logs/errors MUST NOT include constraint/index/relationship payloads, JDBC URLs, credentials, raw metadata SQL, or vendor exception text.

#### Scenario: Read visible relationships
- **WHEN** the current product requests relationships for its visible datasource and table
- **THEN** reusable metadata reads only that target account's visible constraints under configured limits

#### Scenario: Read another product's relationships
- **WHEN** a datasource belongs to another product
- **THEN** the service returns `404 DATA_SOURCE_NOT_FOUND` before target connection or metadata access
