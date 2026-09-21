# Backend SQL Editor Specification

## Purpose

Define product-isolated MySQL metadata browsing and the related SQL editor backend contracts.

## Requirements

### Requirement: Product-isolated metadata access
The SQL service SHALL authorize every metadata request by data-source ID and the current product before opening a target connection.

#### Scenario: Read visible metadata
- **WHEN** a user requests metadata for a data source owned by the current product
- **THEN** the service SHALL use only that saved data source's decrypted configuration and the target MySQL account's visibility

#### Scenario: Read another product's metadata
- **WHEN** a user requests metadata for an unknown data-source ID or one owned by another product
- **THEN** the service SHALL return `404 DATA_SOURCE_NOT_FOUND` without opening a target connection or revealing ownership

### Requirement: Navigable MySQL metadata
The SQL service SHALL expose paginated database, table/view, table-detail, column-search, and bounded table-relationship capabilities using safe engine-dispatched JDBC metadata access. Existing Web endpoints SHALL retain their response contracts; canonical constraint and relationship aggregates SHALL be reusable by the Agent adapter.

#### Scenario: List databases
- **WHEN** `GET /api/v1/data-sources/{id}/databases` receives a valid keyword, page size, page Token, and `includeSystem` value
- **THEN** it SHALL return a stable name-ordered cursor page and SHALL hide `information_schema`, `performance_schema`, `mysql`, and `sys` by default

#### Scenario: List databases without a default database
- **WHEN** the saved data source has a null or blank `defaultDatabase` and the target MySQL account can see at least one non-system database
- **THEN** `GET /api/v1/data-sources/{id}/databases` SHALL return those visible databases and SHALL NOT return `METADATA_QUERY_FAILED` or an empty page solely because no default database is configured

#### Scenario: List tables and views
- **WHEN** `GET /api/v1/data-sources/{id}/tables` receives a visible database, optional keyword, and declared TABLE/VIEW types
- **THEN** it SHALL return up to 200 matching objects with database, name, type, and safe comment fields

#### Scenario: Read table structure
- **WHEN** `GET /api/v1/data-sources/{id}/table-detail` receives a visible database and table
- **THEN** it SHALL return ordered columns, primary-key fields, ordered indexes including uniqueness and type, and the table DDL from `SHOW CREATE TABLE`

#### Scenario: Read reusable table constraints
- **WHEN** reusable metadata requests a visible MySQL table
- **THEN** the engine returns ordered columns, primary key, ordered indexes, bounded unique/outbound foreign keys and explicit coverage while the existing Web DTO remains compatible

#### Scenario: Read one-hop relationships
- **WHEN** reusable metadata requests inbound/outbound relationships for one visible table
- **THEN** the engine returns bounded atomic edges without scanning a second relationship hop

#### Scenario: Reject unsafe metadata input
- **WHEN** a database/table identifier, type, direction, page size, limit or cursor is malformed or tampered
- **THEN** the service SHALL return a stable validation/not-found error and SHALL NOT concatenate the value into executable SQL

### Requirement: Reusable relationship metadata remains product isolated
Every relationship or enriched schema operation SHALL authorize the datasource against the current product before borrowing a target connection. It SHALL use only that saved datasource and database account visibility, and ordinary logs/errors MUST NOT include constraint/index/relationship payloads, JDBC URLs, credentials, raw metadata SQL, or vendor exception text.

#### Scenario: Read visible relationships
- **WHEN** the current product requests relationships for its visible datasource and table
- **THEN** reusable metadata reads only that target account's visible constraints under configured limits

#### Scenario: Read another product's relationships
- **WHEN** a datasource belongs to another product
- **THEN** the service returns `404 DATA_SOURCE_NOT_FOUND` before target connection or metadata access

### Requirement: Database list is the NAMESPACE adapter
`GET /api/v1/data-sources/{id}/databases` SHALL remain the list endpoint for the product `NAMESPACE` layer. The service SHALL NOT add a `/namespaces` or generic `/resources` tree in this change.

#### Scenario: Tag database items as NAMESPACE
- **WHEN** databases are listed for a visible MYSQL data source
- **THEN** each item SHALL include `name` as today and `kind` equal to `NAMESPACE`

#### Scenario: Keep tables bound to the NAMESPACE name
- **WHEN** tables or table detail are requested
- **THEN** the query parameter and `TableItem.database` / `TableDetail.database` SHALL still carry the parent NAMESPACE name, and the paths SHALL remain `/tables` and `/table-detail`

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
The SQL service SHALL run target JDBC work outside Tomcat request threads with configurable global and per-user limits, a default 60-second timeout that a request MAY lower via `timeoutSeconds`, and deterministic resource cleanup.

#### Scenario: Execute within quota
- **WHEN** the global and current-user execution limits have capacity
- **THEN** the service SHALL insert RUNNING history, execute in the dedicated pool, register the running resources, and complete the asynchronous response

#### Scenario: Exceed per-user or global quota
- **WHEN** accepting an execution would exceed the configured current-user or global limit
- **THEN** the service SHALL return `429 EXECUTION_LIMIT_EXCEEDED` without leaking another user's activity

#### Scenario: Execution times out
- **WHEN** JDBC or the async response exceeds the effective execution timeout for that request
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

#### Scenario: The target engine rejects a statement
- **WHEN** JDBC raises a syntax, permission, object, or other database error
- **THEN** the service SHALL return `422 SQL_EXECUTION_FAILED` with execution ID, SQLState, `vendorErrorCode` (the driver error code, formerly `mysqlErrorCode`), and a sanitized message, and SHALL persist FAILED history

#### Scenario: Read-only policy rejects a statement
- **WHEN** `readOnly` is `true` and the statement is not classified as `SELECT`
- **THEN** the service SHALL return `422 READ_ONLY_VIOLATION` with a safe message and SHALL NOT persist history or include credentials, SQL text beyond the stable error contract, or internals

#### Scenario: Observe execution
- **WHEN** execution activity is logged or measured
- **THEN** diagnostics SHALL contain safe IDs, statement hash/type, duration, status, counts, and pool metrics but SHALL exclude SQL text, Token, password, JDBC URL, and result values

### Requirement: Engine-dispatched metadata and statement scanning
Metadata listing, constraint/relationship discovery, and single-statement scanning SHALL use the datasource's registered engine. Shared metadata and Agent orchestrators MUST NOT branch on engine ids, issue vendor metadata SQL, directly inspect JDBC metadata, or fall back from an unsupported engine to MySQL-family behavior. For `MYSQL` the visible databases, system-schema hiding, `SHOW CREATE TABLE` DDL, backtick quoting, and comment/string-aware statement split SHALL match the existing MySQL behavior.

#### Scenario: Browse MySQL metadata through the engine
- **WHEN** metadata or relationships run against a MYSQL datasource
- **THEN** the MYSQL engine owns catalog/constraint operations and system-schema hiding, and Web `/databases`, `/tables`, and `/table-detail` SHALL remain the documented MySQL metadata contract, including hiding `information_schema`, `performance_schema`, `mysql`, and `sys` by default and returning table DDL from `SHOW CREATE TABLE`

#### Scenario: Browse PostgreSQL metadata through the engine
- **WHEN** metadata or relationships run against a POSTGRESQL datasource
- **THEN** the POSTGRESQL engine owns schema-based catalog/constraint operations

#### Scenario: Reject extra statements with the engine scanner
- **WHEN** execution text contains more than one non-empty statement after the MYSQL engine ignores delimiters in strings, quoted identifiers, and comments
- **THEN** the service SHALL return `400 MULTI_STATEMENT_NOT_SUPPORTED` before target execution

### Requirement: PostgreSQL metadata through the NAMESPACE adapter
`GET /api/v1/data-sources/{id}/databases` SHALL remain the NAMESPACE list endpoint for POSTGRESQL. For a POSTGRESQL data source the items SHALL be schemas in the pinned database, each with `kind=NAMESPACE`. Table and table-detail APIs SHALL keep using query parameter/field `database` as the parent schema name.

#### Scenario: List PostgreSQL schemas
- **WHEN** databases are listed for a visible POSTGRESQL data source
- **THEN** the service SHALL return schema names as NAMESPACE items, hide `information_schema` and `pg_`-prefixed schemas by default, and SHALL NOT fail solely because MYSQL catalog APIs are unused

#### Scenario: Execute with schema as database
- **WHEN** SQL execution against a POSTGRESQL data source includes `database` equal to a visible schema
- **THEN** the service SHALL apply that schema on the connection and SHALL accept a single statement after the POSTGRESQL scanner ignores delimiters inside dollar-quotes, strings, quoted identifiers, and comments

### Requirement: Optional read-only execution guard
When `POST /api/v1/sql/executions` includes `readOnly` equal to `true`, the SQL service SHALL reject any statement whose classified type is not `SELECT` before acquiring an execution permit, inserting history, or opening a target connection. Statements classified as `SELECT` include those whose first keyword is `SELECT`, `WITH`, `SHOW`, `EXPLAIN`, `DESC`, or `DESCRIBE`. A classified `EXPLAIN` that requests analyzed execution (`EXPLAIN ANALYZE` or `EXPLAIN (ANALYZE ...)`) SHALL also be rejected as `422 EXPLAIN_ANALYZE_NOT_ALLOWED`. Omitted or `false` `readOnly` SHALL keep current write-capable editor behavior, including a user-submitted `EXPLAIN ANALYZE`.

#### Scenario: Reject a write in read-only mode
- **WHEN** an authenticated request sets `readOnly` to `true` and the statement classifies as `INSERT`, `UPDATE`, `DELETE`, `REPLACE`, `DDL`, or `OTHER`
- **THEN** the service SHALL return `422 READ_ONLY_VIOLATION` without inserting history, without incrementing execution concurrency, and without borrowing a target connection

#### Scenario: Allow a classified SELECT in read-only mode
- **WHEN** an authenticated request sets `readOnly` to `true` and the statement classifies as `SELECT`
- **THEN** the service SHALL continue the normal single-statement execution path, SHALL call `Connection.setReadOnly(true)` on the borrowed connection before executing, and SHALL return a `RESULT_SET` on success

#### Scenario: Editor requests remain writable
- **WHEN** `readOnly` is omitted or `false` and the statement is a single `INSERT`, `UPDATE`, `DELETE`, or `DDL`
- **THEN** the service SHALL execute it as today and SHALL NOT call `Connection.setReadOnly(true)`

#### Scenario: Read-only execution that still reports an update count
- **WHEN** `readOnly` is `true`, the statement was classified as `SELECT`, and JDBC returns an update count instead of a ResultSet
- **THEN** the service SHALL return `422 READ_ONLY_VIOLATION`, mark history `FAILED` if a RUNNING row was inserted, and SHALL NOT treat the outcome as a successful `UPDATE_COUNT` or `DDL` result

#### Scenario: Reject EXPLAIN ANALYZE in read-only mode
- **WHEN** `readOnly` is `true` and the statement is `EXPLAIN ANALYZE` or `EXPLAIN (ANALYZE ...)`
- **THEN** the service SHALL return `422 EXPLAIN_ANALYZE_NOT_ALLOWED` without inserting history, without incrementing execution concurrency, and without borrowing a target connection

### Requirement: Table detail includes engine-neutral statistics
`GET /api/v1/data-sources/{id}/table-detail` SHALL include a `stats` object with engine-neutral table statistics in addition to the existing columns, primary key, indexes, and `ddl`. `stats` SHALL expose at least `engine`, `estimatedRows`, `dataBytes`, `indexBytes`, `autoIncrement`, `createTime`, `updateTime`, and `comment`. Missing values SHALL be JSON `null`. The field names SHALL NOT be MySQL `SHOW TABLE STATUS` column names such as `Rows` or `Data_length`. When the engine cannot collect statistics, `stats` SHALL be JSON `null` and the remainder of the table-detail response SHALL still be returned.

#### Scenario: Return MySQL table statistics
- **WHEN** table-detail is requested for a visible MYSQL table whose status can be read
- **THEN** the response SHALL include existing structure and `ddl` as today and SHALL include `stats.estimatedRows`, `stats.engine`, and byte-size fields mapped from that engine's status data

#### Scenario: Statistics failure does not drop table structure
- **WHEN** DDL or column metadata succeeds but statistics collection fails
- **THEN** the response SHALL return columns, indexes, and `ddl` with `stats` equal to `null` and SHALL NOT fail the request solely because statistics are unavailable

#### Scenario: Unknown table still not found
- **WHEN** table-detail is requested for a table that is not visible
- **THEN** the service SHALL return `404 TABLE_NOT_FOUND` and SHALL NOT return a partial `stats` object for another table

### Requirement: Plan-only explain API
The SQL service SHALL expose `POST /api/v1/sql/explains` that accepts one statement, a client-generated execution ID, a visible data source, and an optional NAMESPACE and `timeoutSeconds`. The service SHALL rewrite the statement through the data source engine into a plan-only `EXPLAIN` (or engine equivalent) that MUST NOT execute the original statement. The request SHALL NOT accept `readOnly`, `source`, or an `analyze` flag. History SHALL store source `AI_AGENT_EXPLAIN` and the rewritten statement text. The execution SHALL reuse the existing single-statement validation, concurrency quota, cancellation, timeout, and read-only connection behavior.

#### Scenario: Explain a SELECT without running it
- **WHEN** an authenticated request submits a single `SELECT` to `POST /api/v1/sql/explains` for a visible data source
- **THEN** the service SHALL execute the engine-rewritten plan-only statement, return a `RESULT_SET` plan, persist history `source` equal to `AI_AGENT_EXPLAIN`, and SHALL NOT run the original `SELECT` as a data query

#### Scenario: Reject ANALYZE on the plan-only endpoint
- **WHEN** the submitted text is or rewrites to `EXPLAIN ANALYZE` / `EXPLAIN (ANALYZE ...)` or otherwise requests analyzed execution
- **THEN** the service SHALL return `422 EXPLAIN_ANALYZE_NOT_ALLOWED` before acquiring an execution permit, inserting history, or opening a target connection

#### Scenario: Reject a write on the plan-only endpoint
- **WHEN** the statement classifies as `INSERT`, `UPDATE`, `DELETE`, `REPLACE`, `DDL`, or `OTHER`
- **THEN** the service SHALL return `422 EXPLAIN_STATEMENT_NOT_SUPPORTED` without inserting history and without borrowing a target connection

#### Scenario: Ignore client source on the plan-only endpoint
- **WHEN** the JSON body includes `source` or `readOnly`
- **THEN** the service SHALL reject the unknown properties with `400` as today (`fail-on-unknown-properties`) and SHALL NOT persist a client-supplied source

#### Scenario: Product isolation matches executions
- **WHEN** the data source is unknown or owned by another product
- **THEN** the service SHALL return `404 DATA_SOURCE_NOT_FOUND` without opening a target connection

### Requirement: Regulated explain-analyze API
The SQL service SHALL expose `POST /api/v1/sql/explains:analyze` as a separate endpoint from plan-only explain. When `sql-editor.explain-analyze.enabled` is `false` (the default), the service SHALL return `403 EXPLAIN_ANALYZE_DISABLED` before execution. When enabled, the service SHALL rewrite a single classified-SELECT statement into engine `EXPLAIN ANALYZE` (or equivalent), execute it under the existing quota with `Connection.setReadOnly(true)`, apply a timeout capped by `sql-editor.explain-analyze.timeout-seconds`, and persist history source `AI_AGENT_EXPLAIN_ANALYZE` with the rewritten statement. The request SHALL NOT accept `readOnly`, `source`, or an `analyze` flag on any other endpoint as a substitute.

#### Scenario: Disabled by default
- **WHEN** `sql-editor.explain-analyze.enabled` is omitted or `false` and a client calls `POST /api/v1/sql/explains:analyze`
- **THEN** the service SHALL return `403 EXPLAIN_ANALYZE_DISABLED` without inserting history, without incrementing execution concurrency, and without opening a target connection

#### Scenario: Analyze a SELECT when enabled
- **WHEN** explain-analyze is enabled and an authenticated request submits a single `SELECT`
- **THEN** the service SHALL execute the engine-rewritten analyzed explain, return a `RESULT_SET` plan, and persist history `source` equal to `AI_AGENT_EXPLAIN_ANALYZE`

#### Scenario: Reject a write on the analyze endpoint
- **WHEN** explain-analyze is enabled and the statement classifies as `INSERT`, `UPDATE`, `DELETE`, `REPLACE`, `DDL`, or `OTHER`
- **THEN** the service SHALL return `422 EXPLAIN_STATEMENT_NOT_SUPPORTED` without running DML or DDL

#### Scenario: Timeout uses the analyze cap
- **WHEN** explain-analyze is enabled, the configured analyze timeout is 15, and the request omits `timeoutSeconds` or sends a larger value
- **THEN** the effective JDBC and async HTTP timeouts SHALL use 15 seconds (plus the existing five-second HTTP buffer) and SHALL NOT use the general execution maximum

#### Scenario: Plan-only endpoint is unchanged when analyze is enabled
- **WHEN** explain-analyze is enabled and a client calls `POST /api/v1/sql/explains` with `EXPLAIN ANALYZE ...`
- **THEN** the service SHALL still return `422 EXPLAIN_ANALYZE_NOT_ALLOWED`

### Requirement: Per-request execution timeout
`POST /api/v1/sql/executions` MAY include `timeoutSeconds`. When present, the service SHALL use that value as the effective timeout only if it is an integer between 1 and the configured `sql-editor.execution.timeout-seconds` inclusive. When omitted, the service SHALL use the configured value. The effective seconds SHALL be applied both to JDBC `Statement.setQueryTimeout` and to that request's `WebAsyncTask` timeout, which SHALL be effective seconds plus five.

#### Scenario: Agent short timeout
- **WHEN** a request includes `timeoutSeconds` equal to 10 and the configured maximum is 60
- **THEN** the service SHALL set JDBC query timeout to 10 seconds and SHALL fail that asynchronous HTTP request at 15 seconds if it is still running

#### Scenario: Reject timeout above configured maximum
- **WHEN** a request includes `timeoutSeconds` greater than the configured `sql-editor.execution.timeout-seconds`, or less than 1
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with a field error on `timeoutSeconds` before target execution

#### Scenario: Omitted timeout keeps the configured default
- **WHEN** a request omits `timeoutSeconds`
- **THEN** the service SHALL use the configured execution timeout for both JDBC and the asynchronous HTTP timeout, matching today's default of 60 seconds plus a 5-second HTTP buffer
