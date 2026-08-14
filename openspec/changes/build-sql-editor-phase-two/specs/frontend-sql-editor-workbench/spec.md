## ADDED Requirements

### Requirement: Connection-bound SQL workspace
The frontend SHALL provide `/sql-editor` as a protected desktop workspace whose data-source/database selection is reflected in safe URL parameters and whose tabs retain immutable connection bindings.

#### Scenario: Select a connection
- **WHEN** a user selects a visible data source and one of its databases
- **THEN** the workspace SHALL update `dataSourceId` and `database` query parameters and new tabs SHALL bind to that connection

#### Scenario: Open a connection URL
- **WHEN** a user opens a workspace URL containing connection parameters
- **THEN** the frontend SHALL reload server-authorized data-source/metadata state and SHALL treat a 404 as unavailable rather than trusting URL ownership

#### Scenario: Change connection with an existing tab
- **WHEN** the selected connection changes while the active tab has content or results
- **THEN** the frontend SHALL create or select a tab bound to the new connection and SHALL NOT silently mutate the old tab's binding

### Requirement: Lazy metadata resource browser
The workspace SHALL browse databases, tables/views, columns, primary keys, and indexes through the metadata APIs with search and layer-specific refresh.

#### Scenario: Expand resources
- **WHEN** a user selects a data source, expands a database, and opens a table
- **THEN** the frontend SHALL lazily fetch databases, then tables/views, then table detail and show fields, key, index, type, nullability, and comments

#### Scenario: Change data source
- **WHEN** a different data source becomes active
- **THEN** stale metadata from the previous source SHALL be cleared from visible state and query keys SHALL remain source-scoped

#### Scenario: Use a table action
- **WHEN** a user double-clicks or invokes a table context action
- **THEN** the frontend SHALL support quoted-name insertion, name copy, `SELECT * ... LIMIT 100` generation, and structure viewing without executing automatically

### Requirement: Recoverable SQL tabs
The workspace SHALL start with a query tab, support multiple connection-bound tabs, confirm destructive close, and persist only bounded SQL draft state in Session Storage.

#### Scenario: Start the editor
- **WHEN** no recoverable draft exists
- **THEN** the workspace SHALL create `Query 1` with the current connection and empty SQL

#### Scenario: Reload drafts
- **WHEN** the page reloads with valid bounded draft state
- **THEN** tab names, SQL text, and non-sensitive connection identifiers MAY be restored while results, Tokens, and credentials SHALL NOT be restored

#### Scenario: Logout or receive 401
- **WHEN** authenticated teardown occurs
- **THEN** all editor draft, active execution, result, and query-cache state SHALL be cleared

#### Scenario: Close a non-empty tab
- **WHEN** a user closes a tab containing SQL
- **THEN** the frontend SHALL require confirmation before discarding it

### Requirement: Monaco MySQL editing
The frontend SHALL wrap Monaco with MySQL language behavior, formatting, metadata completion, and documented keyboard commands.

#### Scenario: Execute selection or current statement
- **WHEN** a user presses Cmd/Ctrl+Enter
- **THEN** the frontend SHALL execute the non-empty selection or the statement containing the cursor as determined by a scanner that ignores delimiters in quotes and comments

#### Scenario: Execute all text
- **WHEN** a user presses Cmd/Ctrl+Shift+Enter
- **THEN** the frontend SHALL execute the single statement or block multiple statements with “暂不支持批量执行”

#### Scenario: Save shortcut
- **WHEN** a user presses Cmd/Ctrl+S
- **THEN** the frontend SHALL prevent browser save and state that worksheet persistence is not available

#### Scenario: Request completion
- **WHEN** completion is requested for the active connection/database
- **THEN** Monaco SHALL offer known database, table/view, and column names without inserting credentials or untrusted executable snippets

### Requirement: Execution and cancellation interaction
The frontend SHALL execute with a fresh UUID, prevent duplicate submission per tab, cap active tabs at three executions, and expose cancellation.

#### Scenario: Run valid SQL
- **WHEN** connection requirements and non-empty single SQL are satisfied
- **THEN** the tab SHALL enter running state, send one non-retried execution request with a new UUID, and render the terminal result/error

#### Scenario: Stop an execution
- **WHEN** a user chooses Stop for a running tab
- **THEN** the frontend SHALL call the cancellation endpoint, abort the request where applicable, and restore runnable UI when the terminal state is known

#### Scenario: Leave during execution
- **WHEN** the workspace is closed or navigated away from with in-flight work
- **THEN** every owned fetch SHALL be aborted and local running state SHALL be cleared

#### Scenario: Complete a DDL
- **WHEN** a DDL execution succeeds
- **THEN** metadata for the active database SHALL be invalidated and refreshed

### Requirement: Accessible typed result presentation
The frontend SHALL distinguish result sets, update counts, and DDL messages and SHALL preserve special database-value semantics.

#### Scenario: Render a query result
- **WHEN** RESULT_SET data arrives
- **THEN** the frontend SHALL render a spreadsheet-style grid with typed column headers, a row-number column, sticky headers, virtualized/scrollable rows, selected-row and focused-cell chrome, a value panel for cell contents, client-side sort/filter/pin, row count, duration, truncation, a next-run row limit, and keyboard-accessible cell/row/result copy plus CSV export from the result footer

#### Scenario: Render special cell values
- **WHEN** a cell is NULL, empty string, long text, BIGINT/DECIMAL string, or binary descriptor
- **THEN** the UI SHALL visibly distinguish NULL from empty, preserve numeric strings, collapse long text with detail access, and show binary type/size without raw bytes

#### Scenario: Render an execution error
- **WHEN** execution fails with SQLState/MySQL code or a stable 404/409/422/429/504 code
- **THEN** the frontend SHALL show the safe mapped message and database codes while excluding stack, JDBC URL, credentials, and internal network details

### Requirement: Query export workflow
The frontend SHALL offer CSV export only for a successful query result and SHALL send only its execution ID and requested row limit.

#### Scenario: Start an export
- **WHEN** a user confirms that export re-executes the query
- **THEN** the frontend SHALL fetch the CSV without automatic retry, keep it as a Blob, derive a safe filename, and start download without converting the payload to text

#### Scenario: Cancel an export
- **WHEN** a user cancels an in-progress download or leaves the page
- **THEN** its AbortSignal SHALL stop the request and the UI SHALL return to an idle export state

### Requirement: Current-user history workspace
The frontend SHALL list/filter current-user history, display summaries, and reopen history SQL in a new tab.

#### Scenario: Filter and paginate history
- **WHEN** a user supplies keyword, connection, database, status, or type filters
- **THEN** the frontend SHALL request server-side filtered cursor pages ordered newest first

#### Scenario: Reopen available history
- **WHEN** history detail reports an available connection
- **THEN** the frontend SHALL open a new tab with the original SQL and connection without automatically executing it

#### Scenario: Reopen unavailable history
- **WHEN** history detail reports `connectionAvailable=false`
- **THEN** the frontend SHALL restore SQL only and require a new connection selection

### Requirement: SQL editor quality gates
The second-stage frontend SHALL extend production contract mocks and repeatable unit, component, E2E, static, and build checks.

#### Scenario: Verify editor behavior
- **WHEN** frontend verification runs
- **THEN** tests SHALL cover SQL extraction, special values, DDL metadata invalidation, SELECT/DML/DDL/error/cancel, CSV, metadata, and history core flows

