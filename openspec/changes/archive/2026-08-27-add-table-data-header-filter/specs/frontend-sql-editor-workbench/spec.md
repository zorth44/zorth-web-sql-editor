## ADDED Requirements

### Requirement: Table Data header filter and sort push-down
The table-object Data pane SHALL show a filter input under each result column header. Committing those inputs or changing table-Data sort SHALL rebuild the table preview as `SELECT * FROM namespace.table` with optional `WHERE` and optional `ORDER BY`, then execute it through the existing preview path and `rowLimit`. SQL RESULT_SET grids SHALL NOT show this header filter row and SHALL NOT rewrite the user's statement.

#### Scenario: Show filter inputs only on table Data
- **WHEN** a table-object Data pane is showing a RESULT_SET
- **THEN** the grid SHALL render one text input under each column header, aligned with that column including pinned columns
- **WHEN** a SQL RESULT_SET grid is showing
- **THEN** the grid SHALL NOT render header filter inputs and SHALL keep footer quick-filter and context-menu column filter

#### Scenario: Apply filters on Enter
- **WHEN** the user presses Enter in a table Data header filter input
- **THEN** the frontend SHALL parse every column's current filter draft, omit empty drafts, combine the rest with `AND`, and re-execute the table preview with that `WHERE` and the current table-Data sort
- **WHEN** every draft is empty
- **THEN** the preview SQL SHALL omit `WHERE`

#### Scenario: Keep uncommitted drafts off sort queries
- **WHEN** the user changes table-Data sort without pressing Enter in the filter row
- **THEN** the frontend SHALL re-execute using last-applied filters plus the new `ORDER BY`, and SHALL NOT apply filter drafts that have not been committed

#### Scenario: Push sort into ORDER BY
- **WHEN** the user cycles sort from the type glyph or chooses asc/desc/clear from the context menu on a table Data pane
- **THEN** the frontend SHALL re-execute the table preview with current applied filters and a single-column `ORDER BY` for that sort, or omit `ORDER BY` when sort is cleared
- **WHEN** the user commits filters while a sort is active
- **THEN** the same preview SQL SHALL include both `WHERE` and `ORDER BY`

#### Scenario: Parse a conservative filter language
- **WHEN** a filter draft is `NULL` or `NOT NULL` (case-insensitive)
- **THEN** the frontend SHALL emit `IS NULL` or `IS NOT NULL` for that column
- **WHEN** a draft starts with `>=`, `<=`, `<>`, `!=`, `=`, `>`, or `<`
- **THEN** the frontend SHALL use that comparison against the remaining value
- **WHEN** a string or other non-numeric column has a bare draft
- **THEN** the frontend SHALL emit `LIKE` with the value wrapped in `%`, wildcards inside the value escaped
- **WHEN** a number, date, or boolean column has a bare draft
- **THEN** the frontend SHALL emit equality against that value

#### Scenario: Reject invalid drafts without executing
- **WHEN** a number column draft is not a decimal number, a boolean draft is not `true`/`false`/`1`/`0`, or a binary column draft is not empty/`NULL`/`NOT NULL`
- **THEN** the frontend SHALL show an error on that filter input and SHALL NOT start a new execution

#### Scenario: Quote filter values as literals
- **WHEN** a committed filter needs a string or date literal
- **THEN** the frontend SHALL wrap it in single quotes and double any embedded quotes, and SHALL NOT concatenate the raw draft into SQL

#### Scenario: Refresh uses the active preview query
- **WHEN** the user refreshes a table Data pane that has applied filters or sort
- **THEN** the frontend SHALL re-execute the same `WHERE`/`ORDER BY` preview, not an unfiltered unordered `SELECT *`

#### Scenario: Preserve filter drafts across the Properties pane
- **WHEN** the user switches from Data to Properties and back on the same table tab
- **THEN** the header filter inputs and current sort SHALL remain

#### Scenario: Ignore the filter row for column selection
- **WHEN** the user presses on a table Data header filter input
- **THEN** the grid SHALL NOT treat that press as a column-header selection or sort gesture

## MODIFIED Requirements

### Requirement: Engine-specific identifier quoting
Insert-from-tree SQL and table DATA preview SHALL quote identifiers with the bound engine's `identifierQuote` (MYSQL backtick, POSTGRESQL double quote). Table Data filter and sort queries SHALL quote schema, table, and column identifiers the same way.

#### Scenario: Insert a PostgreSQL table preview
- **WHEN** the user inserts a preview SELECT from a POSTGRESQL table
- **THEN** the inserted SQL SHALL quote the schema and table with double quotes

#### Scenario: Quote columns in table Data filter queries
- **WHEN** the user applies a header filter or sort on a table Data pane
- **THEN** the generated preview SQL SHALL quote the NAMESPACE, table, and referenced column identifiers with the bound engine's `identifierQuote`

### Requirement: Spreadsheet-style result grid selection
The frontend SHALL present RESULT_SET grids, including SQL execution results and table-object Data panes, as a pointer-driven spreadsheet: hover tracking, a single rectangular selection, and TSV copy of that selection. The grid SHALL keep value-panel, pin, whole-result copy, and CSV export behaviors. SQL RESULT_SET grids SHALL keep client-side sort and filter. Table-object Data panes SHALL use header-filter and sort push-down instead of client-side column filter and client-side sort.

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
- **WHEN** the user interacts with a table Data header filter input
- **THEN** the grid SHALL NOT select that column or cycle sort

#### Scenario: Copy the current selection
- **WHEN** the user presses Ctrl/Cmd+C or chooses copy-selection from the cell context menu
- **THEN** the frontend SHALL write the selected rectangle to the clipboard as TSV using each cell's display text, with tab-separated columns in visual order, newline-separated rows, no header row, `NULL` for null cells, and the existing binary descriptor for binary cells

#### Scenario: Share the grid between SQL results and table data
- **WHEN** a user inspects either a SQL RESULT_SET or a table-object Data pane
- **THEN** both views SHALL use the same grid selection and copy behavior

#### Scenario: Preserve existing result actions
- **WHEN** a RESULT_SET grid is showing a selection
- **THEN** the user SHALL still be able to open the value panel for the focused cell, copy the entire result from the footer, export CSV, and use pin
- **WHEN** the RESULT_SET is from a SQL tab
- **THEN** the user SHALL still be able to use footer quick-filter, context-menu column filter, and context-menu sort
