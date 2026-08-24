# Backend SQL History and Export Specification

## Purpose

Define durable execution-history persistence, current-user history reads, query-only CSV export by statement replay, and safe streaming CSV encoding.

## Requirements

### Requirement: Durable execution history lifecycle
The SQL service SHALL persist every execution and export lifecycle with current user/product snapshots, statement metadata, safe outcome details, and UTC timestamps.

#### Scenario: Start an execution
- **WHEN** a validated execution is accepted
- **THEN** the service SHALL insert a globally unique RUNNING record before target JDBC work begins

#### Scenario: Finish an execution
- **WHEN** target work succeeds, fails, is cancelled, or times out
- **THEN** the service SHALL update the same record to its terminal status with result summary, duration, finish time, and safe SQL error fields when applicable

#### Scenario: Recover stale running records
- **WHEN** service startup finds RUNNING records older than the configured threshold
- **THEN** it SHALL mark them failed with a safe `SERVICE_RESTARTED` classification

### Requirement: Current-user history list
The SQL service SHALL expose cursor-paginated history ordered by `started_at DESC, id DESC` and scoped strictly to the current user.

#### Scenario: Filter history
- **WHEN** a user supplies a SQL keyword of at most 200 characters or data-source, database, status, or statement-type filters
- **THEN** the service SHALL safely escape LIKE wildcards, apply every filter with current `user_id`, and return summaries only

#### Scenario: Continue history pagination
- **WHEN** a valid page Token bound to the same filters/page size is supplied
- **THEN** the service SHALL continue after its encoded `started_at + id` boundary with a maximum page size of 100

#### Scenario: Attempt to list another user's history
- **WHEN** users share a product or a client submits another user identifier
- **THEN** only the authenticated user's rows SHALL be queried and no client-selected identity SHALL affect the result

### Requirement: Current-user history detail
The SQL service SHALL expose full statement text and execution summary only to the history creator.

#### Scenario: Read own history detail
- **WHEN** the current user requests an execution they created
- **THEN** the service SHALL return SQL text, connection snapshots, status/result summary, `sqlState`, `vendorErrorCode` when a vendor code was recorded, and `connectionAvailable` based on current product-scoped data-source visibility, without returning result rows and without a `mysqlErrorCode` field

#### Scenario: Read another user's or unknown detail
- **WHEN** the history ID is unknown or belongs to another user
- **THEN** the service SHALL return `404 EXECUTION_NOT_FOUND`

#### Scenario: Read history for a removed connection
- **WHEN** the user still owns history but its data source is deleted or no longer visible
- **THEN** SQL text SHALL remain readable and `connectionAvailable` SHALL be false without exposing current data-source details

### Requirement: Vendor-neutral execution error persistence
The SQL service SHALL persist driver error codes in `vendor_error_code` and SHALL NOT persist or serialize them as `mysql_error_code` / `mysqlErrorCode`.

#### Scenario: Persist a failed execution
- **WHEN** target JDBC fails with a non-zero driver error code
- **THEN** the history row SHALL store that value in `vendor_error_code` and the detail API SHALL return it as `vendorErrorCode`

### Requirement: Query-only history replay export
The SQL service SHALL export only a current user's successful RESULT_SET execution by replaying its persisted statement against a still-visible data source.

#### Scenario: Export a successful query
- **WHEN** `POST /api/v1/sql/exports` contains the current user's successful query execution ID and a valid row limit
- **THEN** the service SHALL ignore any replacement SQL, reauthorize the data source, replay the persisted database and statement, stream a CSV attachment, and create an independent EXPORT history row

#### Scenario: Reject a non-query export
- **WHEN** the referenced history is not successful or not RESULT_SET
- **THEN** the service SHALL return `400 SQL_NOT_EXPORTABLE` without executing SQL

#### Scenario: Reject unavailable history or connection
- **WHEN** the execution is absent/owned by another user or its data source is no longer visible
- **THEN** the service SHALL return the non-disclosing execution/data-source not-found contract

### Requirement: Safe streaming CSV encoding
CSV export SHALL stream UTF-8 with BOM using RFC 4180 escaping, CRLF records, configured row/byte/time limits, and formula-injection protection enabled by default.

#### Scenario: Encode text and NULL
- **WHEN** fields contain commas, quotes, newlines, Chinese text, empty strings, or NULL
- **THEN** the stream SHALL quote and double quotes as required, preserve UTF-8 text and empty strings, and encode NULL using the configured empty/literal policy

#### Scenario: Protect spreadsheet formulas
- **WHEN** a textual cell begins with `=`, `+`, `-`, or `@` and protection is enabled
- **THEN** the exporter SHALL prefix the cell with a single quote before CSV escaping

#### Scenario: Client disconnects or limit is reached
- **WHEN** output disconnects, times out, reaches its row limit, or reaches 100 MiB
- **THEN** the service SHALL stop reading, cancel target work when needed, close resources, and finalize export history safely without buffering the complete file

### Requirement: Execution source attribution
The SQL service SHALL persist an execution `source` of `WEB_SQL_EDITOR` or `AI_AGENT` on every execution-history row. `POST /api/v1/sql/executions` MAY include `source`; when omitted or blank the service SHALL store `WEB_SQL_EDITOR`. Export history SHALL store `WEB_SQL_EDITOR`. `source` is audit metadata and SHALL NOT authorize the statement or imply `readOnly`.

#### Scenario: Persist an Agent source
- **WHEN** a validated execution request includes `source` equal to `AI_AGENT`
- **THEN** the RUNNING history row SHALL store `AI_AGENT` and the current-user history list and detail SHALL return that value as `source`

#### Scenario: Default editor source
- **WHEN** a validated execution request omits `source`
- **THEN** the history row SHALL store `WEB_SQL_EDITOR`

#### Scenario: Reject an unknown source
- **WHEN** a request includes `source` that is not `WEB_SQL_EDITOR` or `AI_AGENT`
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with a field error on `source` and SHALL NOT insert history

### Requirement: Trusted client IP capture
When inserting a RUNNING execution-history row, the SQL service SHALL store `client_ip`. If the peer address is in `sql-editor.http.trusted-proxy-cidrs`, the service SHALL use the leftmost non-empty `X-Forwarded-For` hop or `X-Real-IP` when that header is the only forwarded address. Otherwise it SHALL store `HttpServletRequest.getRemoteAddr()`. The value SHALL be truncated to 64 characters. History list and detail APIs SHALL NOT return `client_ip`.

#### Scenario: Direct caller without trusted proxies
- **WHEN** `trusted-proxy-cidrs` is empty or the peer is not in the list
- **THEN** the history row SHALL store the servlet remote address and SHALL ignore any `X-Forwarded-For` value supplied by the caller

#### Scenario: Trusted gateway forwards a client address
- **WHEN** the peer address is in `trusted-proxy-cidrs` and `X-Forwarded-For` contains a client address
- **THEN** the history row SHALL store that client address, truncated to 64 characters

#### Scenario: History APIs omit client IP
- **WHEN** the current user reads history list or detail
- **THEN** the JSON SHALL include `source` when present and SHALL NOT include `clientIp` or `client_ip`
