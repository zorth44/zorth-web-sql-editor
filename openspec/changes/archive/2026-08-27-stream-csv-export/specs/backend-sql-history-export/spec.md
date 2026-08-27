## ADDED Requirements

### Requirement: JDBC cursor session for streaming export
CSV export SHALL prepare the borrowed target connection for engine-defined JDBC streaming before creating the export statement. Engines that require it SHALL run with `autoCommit=false` and their `streamingFetchSize()`. Engines that do not SHALL keep autocommit unchanged. The service SHALL still stream CSV records to the HTTP body without assembling the complete file or the complete JDBC result in memory.

#### Scenario: Stream a PostgreSQL export through a server cursor
- **WHEN** export replays a successful RESULT_SET against a POSTGRESQL data source
- **THEN** the service SHALL set `autoCommit=false` after applying the namespace and before executing, SHALL apply the PostgreSQL streaming fetch size, and SHALL write CSV records as rows arrive

#### Scenario: Leave MySQL streaming on autocommit
- **WHEN** export replays a successful RESULT_SET against a MYSQL data source
- **THEN** the service SHALL keep `autoCommit=true` and SHALL keep using the MySQL streaming fetch size

#### Scenario: Commit a successful streaming transaction
- **WHEN** a streaming export that turned autocommit off finishes writing within row, byte, and time limits
- **THEN** the service SHALL commit that transaction and restore `autoCommit=true` before releasing the connection

#### Scenario: Roll back a failed or cancelled streaming transaction
- **WHEN** a streaming export that turned autocommit off fails, times out, exceeds limits, or the client disconnects
- **THEN** the service SHALL NOT commit, SHALL cancel the statement when needed, and SHALL roll back and restore `autoCommit=true` when releasing the connection

## MODIFIED Requirements

### Requirement: Safe streaming CSV encoding
CSV export SHALL stream UTF-8 with BOM using RFC 4180 escaping, CRLF records, configured row/byte/time limits, and formula-injection protection enabled by default. The JDBC driver and JVM SHALL NOT buffer the complete result set or the complete CSV file.

#### Scenario: Encode text and NULL
- **WHEN** fields contain commas, quotes, newlines, Chinese text, empty strings, or NULL
- **THEN** the stream SHALL quote and double quotes as required, preserve UTF-8 text and empty strings, and encode NULL using the configured empty/literal policy

#### Scenario: Protect spreadsheet formulas
- **WHEN** a textual cell begins with `=`, `+`, `-`, or `@` and protection is enabled
- **THEN** the exporter SHALL prefix the cell with a single quote before CSV escaping

#### Scenario: Client disconnects or limit is reached
- **WHEN** output disconnects, times out, reaches its row limit, or reaches 100 MiB
- **THEN** the service SHALL stop reading, cancel target work when needed, close resources, and finalize export history safely without buffering the complete file
