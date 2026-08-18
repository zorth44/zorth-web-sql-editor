## ADDED Requirements

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
