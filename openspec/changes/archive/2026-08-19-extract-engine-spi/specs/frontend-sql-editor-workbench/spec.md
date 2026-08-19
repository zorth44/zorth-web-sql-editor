## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Vendor-neutral error code types
Frontend contracts, mocks, and history detail rendering SHALL use `vendorErrorCode` and SHALL NOT read or display `mysqlErrorCode`.

#### Scenario: Type and mock the history and execution error payload
- **WHEN** TypeScript contracts or MSW handlers describe a failed execution or history detail
- **THEN** they SHALL expose `vendorErrorCode: number | null` and SHALL NOT include `mysqlErrorCode`
