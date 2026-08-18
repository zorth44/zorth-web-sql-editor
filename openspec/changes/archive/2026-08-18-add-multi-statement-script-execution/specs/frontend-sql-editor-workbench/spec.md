## ADDED Requirements

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

## MODIFIED Requirements

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
- **WHEN** execution fails with SQLState/MySQL code or a stable 404/409/422/429/504 code
- **THEN** the frontend SHALL show the safe mapped message and database codes while excluding stack, JDBC URL, credentials, and internal network details
