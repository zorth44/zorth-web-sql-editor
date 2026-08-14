## ADDED Requirements

### Requirement: Product-isolated metadata access
The SQL service SHALL authorize every metadata request by data-source ID and the current product before opening a target connection.

#### Scenario: Read visible metadata
- **WHEN** a user requests metadata for a data source owned by the current product
- **THEN** the service SHALL use only that saved data source's decrypted configuration and the target MySQL account's visibility

#### Scenario: Read another product's metadata
- **WHEN** a user requests metadata for an unknown data-source ID or one owned by another product
- **THEN** the service SHALL return `404 DATA_SOURCE_NOT_FOUND` without opening a target connection or revealing ownership

### Requirement: Navigable MySQL metadata
The SQL service SHALL expose paginated database, table/view, and table-detail APIs using safe JDBC metadata access.

#### Scenario: List databases
- **WHEN** `GET /api/v1/data-sources/{id}/databases` receives a valid keyword, page size, page Token, and `includeSystem` value
- **THEN** it SHALL return a stable name-ordered cursor page and SHALL hide `information_schema`, `performance_schema`, `mysql`, and `sys` by default

#### Scenario: List tables and views
- **WHEN** `GET /api/v1/data-sources/{id}/tables` receives a visible database, optional keyword, and declared TABLE/VIEW types
- **THEN** it SHALL return up to 200 matching objects with database, name, type, and safe comment fields

#### Scenario: Read table structure
- **WHEN** `GET /api/v1/data-sources/{id}/table-detail` receives a visible database and table
- **THEN** it SHALL return ordered columns, primary-key fields, ordered indexes including uniqueness and type, and the table DDL from `SHOW CREATE TABLE`

#### Scenario: Reject unsafe metadata input
- **WHEN** a database/table identifier, type, page size, or cursor is malformed or tampered
- **THEN** the service SHALL return a stable validation/not-found error and SHALL NOT concatenate the value into executable SQL

### Requirement: Single-statement execution validation
The SQL service SHALL accept at most one MySQL JDBC Statement per execution request and SHALL use client-generated canonical UUIDs as globally unique execution IDs.

#### Scenario: Execute one statement
- **WHEN** a request contains one non-empty statement no larger than 1 MiB, a valid UUID, valid row limit, and any required database
- **THEN** the service SHALL accept it for execution with `allowMultiQueries=false` and `autoCommit=true`

#### Scenario: Reject multiple statements
- **WHEN** text contains more than one non-empty statement after correctly ignoring delimiters in strings, quoted identifiers, and comments
- **THEN** the service SHALL return `400 MULTI_STATEMENT_NOT_SUPPORTED` before target execution

#### Scenario: Reject an execution ID conflict
- **WHEN** an execution ID exists in either running state or persisted history
- **THEN** the service SHALL return `409 EXECUTION_ID_CONFLICT` without disclosing the owning user

#### Scenario: Reject an oversized statement
- **WHEN** UTF-8 statement bytes exceed 1 MiB
- **THEN** the service SHALL return `413 STATEMENT_TOO_LARGE`

### Requirement: Asynchronous bounded SQL execution
The SQL service SHALL run target JDBC work outside Tomcat request threads with configurable global and per-user limits, a default 60-second timeout, and deterministic resource cleanup.

#### Scenario: Execute within quota
- **WHEN** the global and current-user execution limits have capacity
- **THEN** the service SHALL insert RUNNING history, execute in the dedicated pool, register the running resources, and complete the asynchronous response

#### Scenario: Exceed per-user or global quota
- **WHEN** accepting an execution would exceed the configured current-user or global limit
- **THEN** the service SHALL return `429 EXECUTION_LIMIT_EXCEEDED` without leaking another user's activity

#### Scenario: Execution times out
- **WHEN** JDBC or the async response exceeds the configured execution timeout
- **THEN** the service SHALL cancel the Statement/Future, mark history `TIMEOUT`, release all permits and connections, and return `504 SQL_EXECUTION_TIMEOUT`

#### Scenario: Finish any execution path
- **WHEN** execution succeeds, fails, is cancelled, times out, or the client disconnects
- **THEN** the service SHALL remove the registry entry, decrement running counts, reset the connection session, and close JDBC resources exactly once

### Requirement: Typed and bounded SQL results
The SQL service SHALL return the documented RESULT_SET, UPDATE_COUNT, or DDL result union while preserving database precision and enforcing row/byte limits.

#### Scenario: Return a result set
- **WHEN** JDBC returns a ResultSet
- **THEN** the service SHALL return column name/label, JDBC type name, native type name, encoded rows, row count, duration, and truncation state

#### Scenario: Preserve scalar values
- **WHEN** a row contains BIGINT, DECIMAL/NUMERIC, date/time, NULL, JSON, or binary values
- **THEN** BIGINT and decimal values SHALL be strings, temporal/JSON values SHALL remain normalized strings, NULL SHALL be JSON null, and binary content SHALL be replaced by `{ binary: true, size, base64: null }`

#### Scenario: Apply result limits
- **WHEN** a query reaches `rowLimit + 1` or the configured approximate serialized byte limit
- **THEN** the service SHALL stop returning additional rows and set `truncated=true` without exceeding the 100,000-row hard limit

#### Scenario: Return an update or DDL result
- **WHEN** JDBC returns an update count or the statement is classified as DDL
- **THEN** the response SHALL contain execution ID, appropriate kind, affected rows when meaningful, duration, and a safe success message

### Requirement: User-owned execution cancellation
The SQL service SHALL expose a short synchronous cancellation API that only the execution creator can use.

#### Scenario: Cancel a running execution
- **WHEN** the current user calls `POST /api/v1/sql/executions/{executionId}:cancel` for their running execution
- **THEN** the service SHALL call `Statement.cancel()`, interrupt its Future, mark it cancelled when observed, and return an accepted cancellation response

#### Scenario: Cancel another user's or unknown execution
- **WHEN** the ID is unknown or belongs to another user
- **THEN** the service SHALL return `404 EXECUTION_NOT_FOUND`

#### Scenario: Cancel an already finished own execution
- **WHEN** the ID belongs to the current user but no longer runs
- **THEN** the service SHALL return `409 EXECUTION_ALREADY_FINISHED`

### Requirement: Safe SQL failure contract
The SQL service SHALL convert target SQL and execution failures into stable API errors without exposing credentials or internals.

#### Scenario: MySQL rejects a statement
- **WHEN** JDBC raises a syntax, permission, object, or other database error
- **THEN** the service SHALL return `422 SQL_EXECUTION_FAILED` with execution ID, SQLState, MySQL error code, and a sanitized message, and SHALL persist FAILED history

#### Scenario: Observe execution
- **WHEN** execution activity is logged or measured
- **THEN** diagnostics SHALL contain safe IDs, statement hash/type, duration, status, counts, and pool metrics but SHALL exclude SQL text, Token, password, JDBC URL, and result values

