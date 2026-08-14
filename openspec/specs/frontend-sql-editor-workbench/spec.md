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
- **WHEN** a user selects a database or table in the tree
- **THEN** the workspace SHALL bind the active tab to that data source and database, SHALL show that source name and database on the tab, and SHALL update URL parameters to match

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
