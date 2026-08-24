## ADDED Requirements

### Requirement: Optional read-only execution guard
When `POST /api/v1/sql/executions` includes `readOnly` equal to `true`, the SQL service SHALL reject any statement whose classified type is not `SELECT` before acquiring an execution permit, inserting history, or opening a target connection. Statements classified as `SELECT` include those whose first keyword is `SELECT`, `WITH`, `SHOW`, `EXPLAIN`, `DESC`, or `DESCRIBE`. Omitted or `false` `readOnly` SHALL keep current write-capable editor behavior.

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

## MODIFIED Requirements

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
