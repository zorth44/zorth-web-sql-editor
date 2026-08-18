# Frontend SQL Editor Workbench Specification

## Purpose

Define the SQL workspace resource navigator and lazy metadata browsing behavior for visible data sources.

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
