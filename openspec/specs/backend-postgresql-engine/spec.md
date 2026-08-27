# Backend PostgreSQL Engine Specification

## Purpose

Define PostgreSQL as the second registered `EngineSupport`: pinned-database JDBC connections, schema as the NAMESPACE layer, dollar-quote statement scanning, and vendor failure classification.

## Requirements

### Requirement: PostgreSQL engine registration
The SQL service SHALL register a `POSTGRESQL` `EngineSupport` at startup in addition to `MYSQL`. Target JDBC, metadata, statement scanning, connection-failure classification, and session restore for `engine=POSTGRESQL` SHALL use that implementation.

#### Scenario: Dispatch a saved PostgreSQL data source
- **WHEN** a visible data source with `engine=POSTGRESQL` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the POSTGRESQL engine and SHALL NOT assemble a `jdbc:mysql://` URL or apply MySQL-only property/SSL flags

#### Scenario: Reject Hive or other unregistered engines
- **WHEN** a create or update submits `engine` other than `MYSQL` or `POSTGRESQL`
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

### Requirement: Schema is the NAMESPACE layer
For POSTGRESQL, `GET /api/v1/data-sources/{id}/databases` SHALL list schemas in the pinned database. The query parameter and JSON field `database` SHALL carry the schema name. `defaultDatabase` SHALL remain the PostgreSQL database name used in the JDBC URL and SHALL NOT be listed as the tree's first layer.

#### Scenario: List schemas as NAMESPACE items
- **WHEN** databases are listed for a visible POSTGRESQL data source with `includeSystem=false`
- **THEN** the page SHALL include user schemas such as `public` with `kind=NAMESPACE`, SHALL hide `information_schema` and `pg_`-prefixed schemas by default, and SHALL NOT return the pinned database name as the only NAMESPACE

#### Scenario: List tables under a schema
- **WHEN** tables are requested with `database` equal to a visible schema
- **THEN** the service SHALL return tables and views in that schema and SHALL set `TableItem.database` to the schema name

#### Scenario: Apply schema on execute
- **WHEN** SQL is executed against a POSTGRESQL data source with `database` set to a schema
- **THEN** the engine SHALL set the connection schema to that name before running the statement and SHALL restore `search_path` when returning the connection to the pool

### Requirement: Pinned PostgreSQL database is required
Creating, updating, or testing a POSTGRESQL configuration SHALL require `defaultDatabase` as the database to open. The engine SHALL put that value in the JDBC URL and SHALL NOT switch databases on an open connection.

#### Scenario: Reject a missing default database
- **WHEN** a POSTGRESQL create, update, or unsaved test omits `defaultDatabase`
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with a `defaultDatabase` field error and SHALL NOT open a target connection

#### Scenario: Unknown database fails closed
- **WHEN** a POSTGRESQL test or connect uses a database that does not exist
- **THEN** the engine SHALL classify the failure as `DATABASE_NOT_FOUND` without exposing the JDBC URL

### Requirement: PostgreSQL JDBC, scan, and failure classification
The POSTGRESQL engine SHALL build `jdbc:postgresql://` URLs, apply its own SSL flags and property allow-list, split statements with dollar-quotes, and classify vendor SQLStates.

#### Scenario: Build a PostgreSQL JDBC target
- **WHEN** a POSTGRESQL configuration with host, port, username, password, and default database is built
- **THEN** every URL SHALL use the `jdbc:postgresql://` scheme, include the pinned database, wrap IPv6 hosts in brackets, and SHALL NOT set MySQL Connector/J properties

#### Scenario: Split dollar-quoted statements
- **WHEN** execution text contains a semicolon inside a PostgreSQL dollar-quoted string
- **THEN** the POSTGRESQL scanner SHALL treat that text as one statement

#### Scenario: Classify authentication failure
- **WHEN** PostgreSQL rejects the password
- **THEN** the engine SHALL return `AUTHENTICATION_FAILED` with a sanitized message

### Requirement: PostgreSQL export uses a server cursor
PostgreSQL CSV export SHALL run the replayed statement with `autoCommit=false` and a positive `streamingFetchSize()` so the JDBC driver fetches through a server cursor instead of buffering the complete result set.

#### Scenario: Export a large PostgreSQL result
- **WHEN** CSV export replays a successful POSTGRESQL RESULT_SET statement
- **THEN** the engine SHALL require autocommit off, the export SHALL set `autoCommit=false` before executing, SHALL use a positive fetch size, and SHALL NOT rely on the driver materializing every row before the first CSV record is written

#### Scenario: Return the PostgreSQL connection after export
- **WHEN** a PostgreSQL export finishes, fails, is cancelled, or hits a limit
- **THEN** the service SHALL restore `autoCommit=true` before the connection is reused, rolling back if the stream did not complete successfully
