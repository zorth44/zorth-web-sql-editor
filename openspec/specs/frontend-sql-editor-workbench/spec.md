# Frontend SQL Editor Workbench Specification

## Purpose

Define the SQL workspace: the resource navigator and lazy metadata browsing for visible data sources, connection-bound recoverable tabs, Monaco MySQL editing, execution and cancellation interaction, result presentation, CSV export, and the current-user history workspace.
## Requirements
### Requirement: Data-source-rooted resource navigator
The SQL workspace sidebar SHALL list visible data sources as tree roots. Expanding a data source SHALL lazily load its databases; expanding a database SHALL lazily load tables and views. Each editor tab SHALL display its bound data source and database. The workspace SHALL NOT place data-source or database selectors above the tab bar; connection changes SHALL come from the resource tree. The active tab SHALL remain visually distinct from inactive tabs without relying on focus styling.

#### Scenario: Browse from a data source
- **WHEN** the resource tree is shown and the user has visible data sources
- **THEN** the tree SHALL render those data sources as top-level nodes, each labeled with name and host/port, and SHALL NOT render databases until their parent data source is expanded

#### Scenario: Expand a data source
- **WHEN** a user expands a data source node
- **THEN** the frontend SHALL fetch that source's databases on demand and show them as children of the data source

#### Scenario: Keep multiple data sources expanded
- **WHEN** a user expands a second data source while another remains expanded
- **THEN** both sources' loaded databases SHALL remain visible and query keys SHALL stay scoped by data source id

#### Scenario: Bind the editor from the tree
- **WHEN** a user selects a database or table in the tree while a SQL tab is active
- **THEN** the workspace SHALL bind the active tab to that data source and database, SHALL show that source name and database on the tab, and SHALL update URL parameters to match

#### Scenario: Select a connection on the welcome page
- **WHEN** a user selects a database in the tree while no editor tab is open
- **THEN** the workspace SHALL update URL parameters and tree highlight and SHALL NOT create a SQL tab

#### Scenario: Identify the active tab
- **WHEN** more than one SQL tab is open
- **THEN** the active tab SHALL use a contrasting background and a persistent brand indicator along its bottom edge, and inactive tabs SHALL remain visually receded

#### Scenario: Switch tabs
- **WHEN** a user activates another tab
- **THEN** the workspace SHALL restore that tab's bound data source and database in the URL, tree highlight, and status bar without changing other tabs' bindings

#### Scenario: Filter databases and tables under an expanded source
- **WHEN** a user expands a data source
- **THEN** the tree SHALL show two filters under that source, labeled as database-name and table-name filters, and SHALL NOT show a global data-source search
- **WHEN** the user types a database name in that source's database filter
- **THEN** only matching databases of that source SHALL remain visible
- **WHEN** the user types a table name in that source's table filter
- **THEN** tables and views under already-expanded databases of that source SHALL be filtered

#### Scenario: No visible data sources
- **WHEN** the current user has no visible data sources
- **THEN** the sidebar SHALL show an empty state that points to data-source management and SHALL NOT tell the user to pick a data source from the toolbar

#### Scenario: Open the workspace with no tabs
- **WHEN** the user enters `/sql-editor` with no recoverable draft
- **THEN** the main pane SHALL show a welcome page instead of an empty editor, and SHALL NOT auto-create a query tab or preselect a data source

#### Scenario: Return to the welcome page
- **WHEN** the user closes the last editor tab
- **THEN** the main pane SHALL show the welcome page again and SHALL NOT immediately create a replacement tab

### Requirement: Lazy metadata resource browser
The workspace SHALL browse data sources, databases, tables/views, columns, primary keys, and indexes through the metadata APIs with search and layer-specific refresh. Metadata caches SHALL be keyed by data source id so identically named databases on different sources stay isolated.

#### Scenario: Expand resources
- **WHEN** a user expands a data source, expands a database, and opens a table
- **THEN** the frontend SHALL lazily fetch databases for that source, then tables/views, then table detail and show fields, key, index, type, nullability, and comments

#### Scenario: Change data source
- **WHEN** a different data source becomes the active editor connection
- **THEN** already-loaded metadata for other expanded sources SHALL remain visible, newly requested metadata SHALL use the active source, and query keys SHALL remain source-scoped

#### Scenario: Use a table action
- **WHEN** a user double-clicks a table or view
- **THEN** the workspace SHALL open or focus an object tab for that table, default to the Data pane, load preview rows, and SHALL NOT show a Diagram pane
- **WHEN** the user selects the Properties pane
- **THEN** the frontend SHALL show table info, columns, keys, indexes, and DDL
- **WHEN** a user invokes a table context action
- **THEN** the frontend SHALL support quoted-name insertion, name copy, `SELECT * ... LIMIT 100` generation into a SQL tab, and opening structure in the Properties pane

### Requirement: Spreadsheet-style result grid selection
The frontend SHALL present RESULT_SET grids, including SQL execution results and table-object Data panes, as a pointer-driven spreadsheet: hover tracking, a single rectangular selection, and TSV copy of that selection. The grid SHALL keep value-panel, client-side sort/filter/pin, whole-result copy, and CSV export behaviors.

#### Scenario: Hover over the grid
- **WHEN** the pointer moves across a result grid cell
- **THEN** the frontend SHALL highlight that cell and its row while the pointer remains there, and SHALL NOT treat hover as a selection change

#### Scenario: Drag-select a rectangular range
- **WHEN** the user presses on a cell and drags to another cell before releasing
- **THEN** the grid SHALL select the inclusive rectangle between those cells in current visual column order and current filtered/sorted row order

#### Scenario: Extend the selection with Shift
- **WHEN** a selection anchor exists and the user Shift-clicks a cell or holds Shift while moving with arrow keys
- **THEN** the grid SHALL keep the anchor and set the selection rectangle to the inclusive range between the anchor and the new focus cell

#### Scenario: Select a row from the row-number column
- **WHEN** the user clicks a row number
- **THEN** the grid SHALL select every visual column in that displayed row
- **WHEN** the user drags across row numbers
- **THEN** the grid SHALL select the inclusive row range across every visual column

#### Scenario: Select a column from the header
- **WHEN** the user clicks a column header body
- **THEN** the grid SHALL select every displayed row in that visual column and SHALL NOT cycle sort from that click
- **WHEN** the user clicks the column type glyph or sort icon
- **THEN** the grid SHALL cycle that column's sort as before
- **WHEN** the user drags across column headers
- **THEN** the grid SHALL select those visual columns across every displayed row

#### Scenario: Copy the current selection
- **WHEN** the user presses Ctrl/Cmd+C or chooses copy-selection from the cell context menu
- **THEN** the frontend SHALL write the selected rectangle to the clipboard as TSV using each cell's display text, with tab-separated columns in visual order, newline-separated rows, no header row, `NULL` for null cells, and the existing binary descriptor for binary cells

#### Scenario: Share the grid between SQL results and table data
- **WHEN** a user inspects either a SQL RESULT_SET or a table-object Data pane
- **THEN** both views SHALL use the same grid selection and copy behavior

#### Scenario: Preserve existing result actions
- **WHEN** a RESULT_SET grid is showing a selection
- **THEN** the user SHALL still be able to open the value panel for the focused cell, copy the entire result from the footer, export CSV, and use column filter, pin, and context-menu sort

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

### Requirement: Recoverable SQL tabs
The workspace SHALL open on a welcome page when no editor tab is open, support multiple connection-bound tabs, confirm destructive close, and persist only bounded SQL draft state in Session Storage.

#### Scenario: Start the editor
- **WHEN** no recoverable draft exists
- **THEN** the workspace SHALL show a welcome page with an action to open a SQL editor and SHALL NOT auto-select a data source or create `Query 1`

#### Scenario: Close the last tab
- **WHEN** a user closes the last editor tab
- **THEN** the workspace SHALL return to the welcome page and SHALL NOT immediately create a replacement tab

#### Scenario: Open SQL from the welcome page
- **WHEN** a user chooses to open a SQL editor from the welcome page
- **THEN** the workspace SHALL create a query tab bound to the currently selected connection, which MAY be unbound until a data source and database are chosen in the resource tree

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
The frontend SHALL wrap Monaco with MySQL language behavior, formatting, metadata completion, and documented keyboard commands, and SHALL expose whether a selection exists so run affordances can label themselves.

#### Scenario: Execute selection or current statement
- **WHEN** a user presses Cmd/Ctrl+Enter
- **THEN** the frontend SHALL execute the non-empty selection or the statement containing the cursor as determined by a scanner that ignores delimiters in quotes and comments

#### Scenario: Execute a script
- **WHEN** a user presses Cmd/Ctrl+Shift+Enter
- **THEN** the frontend SHALL execute every statement in the selection when one exists, otherwise every statement in the editor, and SHALL NOT block multiple statements with “暂不支持批量执行”

#### Scenario: Label the run action from the selection
- **WHEN** the editor selection changes between empty and non-empty
- **THEN** the workspace run action SHALL relabel itself between running the whole editor and running the selection so the current target is visible without hovering

#### Scenario: Save shortcut
- **WHEN** a user presses Cmd/Ctrl+S
- **THEN** the frontend SHALL prevent browser save and state that worksheet persistence is not available

#### Scenario: Request completion
- **WHEN** completion is requested for the active connection/database
- **THEN** Monaco SHALL offer known database, table/view, and column names without inserting credentials or untrusted executable snippets

### Requirement: Execution and cancellation interaction
The frontend SHALL execute with a fresh UUID per statement, run multi-statement scripts serially within the owning tab, prevent duplicate submission per tab, cap active tabs at three executions, and expose cancellation for both single statements and scripts.

#### Scenario: Run valid SQL
- **WHEN** connection requirements and non-empty single SQL are satisfied
- **THEN** the tab SHALL enter running state, send one non-retried execution request with a new UUID, and render the terminal result/error

#### Scenario: Run a script serially
- **WHEN** a script with more than one statement starts in a tab
- **THEN** the frontend SHALL execute the statements in source order, sending the next request only after the previous one reaches a terminal state, SHALL count the whole script as one running execution for that tab, and SHALL show which statement of how many is currently running

#### Scenario: A script statement fails
- **WHEN** a statement in a running script fails, is cancelled, or times out
- **THEN** the frontend SHALL stop before the remaining statements, SHALL keep the results already produced, SHALL identify the failing statement and its error, and SHALL state that already-executed statements are not rolled back

#### Scenario: Stop an execution
- **WHEN** a user chooses Stop for a running tab
- **THEN** the frontend SHALL call the cancellation endpoint for the in-flight statement, abort the request where applicable, discard any statements still queued for that script, and restore runnable UI when the terminal state is known

#### Scenario: Leave during execution
- **WHEN** the workspace is closed or navigated away from with in-flight work
- **THEN** every owned fetch SHALL be aborted, queued script statements SHALL be discarded, and local running state SHALL be cleared

#### Scenario: Complete a DDL
- **WHEN** a DDL execution succeeds
- **THEN** metadata for the active database SHALL be invalidated and refreshed

### Requirement: Accessible typed result presentation
The frontend SHALL distinguish result sets, update counts, and DDL messages, SHALL present one result per executed statement together with a script summary, and SHALL preserve special database-value semantics.

#### Scenario: Render a query result
- **WHEN** RESULT_SET data arrives
- **THEN** the frontend SHALL render a spreadsheet-style grid with typed column headers, a row-number column, sticky headers, virtualized/scrollable rows, selected-row and focused-cell chrome, a value panel for cell contents, client-side sort/filter/pin, row count, duration, truncation, a next-run row limit, and keyboard-accessible cell/row/result copy plus CSV export from the result footer

#### Scenario: Render multiple statement results
- **WHEN** a script executed more than one statement
- **THEN** the result area SHALL offer one selectable result tab per statement that produced a result, each using the same grid behavior as a single execution, and SHALL keep the statement order of the script

#### Scenario: Render a script summary
- **WHEN** a script execution reaches a terminal state
- **THEN** the frontend SHALL show a summary listing every statement in order with its type, terminal status, duration, and returned or affected row count, and SHALL mark the failing statement when execution stopped early

#### Scenario: Render special cell values
- **WHEN** a cell is NULL, empty string, long text, BIGINT/DECIMAL string, or binary descriptor
- **THEN** the UI SHALL visibly distinguish NULL from empty, preserve numeric strings, collapse long text with detail access, and show binary type/size without raw bytes

#### Scenario: Render an execution error
- **WHEN** execution fails with SQLState/`vendorErrorCode` or a stable 404/409/422/429/504 code
- **THEN** the frontend SHALL show the safe mapped message and database codes while excluding stack, JDBC URL, credentials, internal network details, and any `mysqlErrorCode` field

### Requirement: Vendor-neutral error code types
Frontend contracts, mocks, and history detail rendering SHALL use `vendorErrorCode` and SHALL NOT read or display `mysqlErrorCode`.

#### Scenario: Type and mock the history and execution error payload
- **WHEN** TypeScript contracts or MSW handlers describe a failed execution or history detail
- **THEN** they SHALL expose `vendorErrorCode: number | null` and SHALL NOT include `mysqlErrorCode`

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

### Requirement: Script execution boundaries
The frontend SHALL split script text with a scanner that ignores delimiters inside strings, quoted identifiers, and comments, SHALL execute each resulting statement as an independent single-statement request, and SHALL declare the resulting session and transaction limits to the user.

#### Scenario: Split a script before execution
- **WHEN** a user runs text containing more than one non-empty statement
- **THEN** the frontend SHALL derive the statement list using the same delimiter rules as the backend scanner and SHALL send each statement as its own request with its own fresh UUID

#### Scenario: Warn about session-scoped statements
- **WHEN** a script to be executed contains a statement that depends on connection session state, such as `SET`, `CREATE TEMPORARY TABLE`, or `USE`
- **THEN** the frontend SHALL warn before execution that session state does not carry across statements because each statement borrows its own pooled connection

#### Scenario: Fall back when splitting is unreliable
- **WHEN** the scanner finishes with an unterminated string, quoted identifier, or comment
- **THEN** the frontend SHALL send the entire text as a single statement and SHALL let the backend return the authoritative error rather than rejecting the text locally

#### Scenario: Exceed the script statement cap
- **WHEN** a script splits into more statements than the configured maximum
- **THEN** the frontend SHALL refuse to start execution and SHALL state the cap and that the script must be split

