# Frontend SQL Editor Workbench Specification

## Purpose

Define the SQL workspace resource navigator and lazy metadata browsing behavior for visible data sources.

## Requirements

### Requirement: Data-source-rooted resource navigator
The SQL workspace sidebar SHALL list visible data sources as tree roots. Expanding a data source SHALL lazily load its databases; expanding a database SHALL lazily load tables and views. The editor toolbar data-source and database controls SHALL stay visible and SHALL stay in sync with the current tab binding.

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
- **THEN** the workspace SHALL bind the active tab to that data source and database and SHALL update the toolbar selectors and URL parameters to match

#### Scenario: Switch connection from the toolbar
- **WHEN** a user changes the toolbar data source or database selector
- **THEN** the workspace SHALL apply the same connection binding as tree selection and SHALL expand and highlight the matching tree nodes without collapsing other expanded data sources

#### Scenario: Search the navigator
- **WHEN** a user types in the sidebar search field
- **THEN** the tree SHALL filter data source names and hosts, and SHALL filter databases under already-expanded sources

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
- **WHEN** a user double-clicks or invokes a table context action
- **THEN** the frontend SHALL support quoted-name insertion, name copy, `SELECT * ... LIMIT 100` generation, and structure viewing without executing automatically
