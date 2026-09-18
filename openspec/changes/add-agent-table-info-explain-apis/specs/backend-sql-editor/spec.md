# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

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
