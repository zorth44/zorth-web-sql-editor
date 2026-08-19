## ADDED Requirements

### Requirement: Catalog-driven NAMESPACE navigator
The resource tree SHALL treat the first layer under a data source as `NAMESPACE` and SHALL load it through the selected engine's catalog `listEndpoint`. For MYSQL that endpoint is the existing databases API. Tree labels and filter placeholders SHALL come from that engine's `resourceTree` entry.

#### Scenario: Expand MYSQL namespaces through databases
- **WHEN** the user expands a data source whose `engine` is MYSQL
- **THEN** the frontend SHALL call `GET /api/v1/data-sources/{id}/databases` and render each item as a NAMESPACE node using the catalog NAMESPACE label

#### Scenario: Bind the editor from a NAMESPACE node
- **WHEN** the user selects a NAMESPACE or a table under it
- **THEN** the workspace SHALL bind the active tab using the existing `dataSourceId` and `database` URL parameters, where `database` holds the NAMESPACE name

#### Scenario: Ignore unknown tree kinds
- **WHEN** a catalog `resourceTree` contains a kind other than NAMESPACE, TABLE, or VIEW
- **THEN** the frontend SHALL skip that level without failing the tree render

### Requirement: Editor language from engine catalog
The SQL editor SHALL set Monaco language from the bound data source engine's `editorLanguage`. MYSQL SHALL use `mysql`. If the catalog is unavailable or the language is not registered, the editor SHALL fall back to `mysql` and remain editable.

#### Scenario: Open a MYSQL-bound tab
- **WHEN** the active tab is bound to a MYSQL data source
- **THEN** Monaco SHALL use language `mysql`

#### Scenario: Open an unbound welcome-created tab
- **WHEN** a SQL tab has no data source yet
- **THEN** the editor SHALL use `mysql` until a data source is bound

## MODIFIED Requirements

### Requirement: Data-source-rooted resource navigator
The SQL workspace sidebar SHALL list visible data sources as tree roots. Expanding a data source SHALL lazily load its NAMESPACE children; expanding a NAMESPACE SHALL lazily load tables and views. Each editor tab SHALL display its bound data source and NAMESPACE name. The workspace SHALL NOT place data-source or NAMESPACE selectors above the tab bar; connection changes SHALL come from the resource tree. The active tab SHALL remain visually distinct from inactive tabs without relying on focus styling.

#### Scenario: Browse from a data source
- **WHEN** the resource tree is shown and the user has visible data sources
- **THEN** the tree SHALL render those data sources as top-level nodes, each labeled with name and host/port, and SHALL NOT render NAMESPACE children until their parent data source is expanded

#### Scenario: Expand a data source
- **WHEN** a user expands a data source node
- **THEN** the frontend SHALL fetch that source's NAMESPACE list on demand through the catalog `listEndpoint` and show them as children of the data source

#### Scenario: Keep multiple data sources expanded
- **WHEN** a user expands a second data source while another remains expanded
- **THEN** both sources' loaded NAMESPACE children SHALL remain visible and query keys SHALL stay scoped by data source id

#### Scenario: Bind the editor from the tree
- **WHEN** a user selects a NAMESPACE or table in the tree while a SQL tab is active
- **THEN** the workspace SHALL bind the active tab to that data source and NAMESPACE, SHALL show that source name and NAMESPACE on the tab, and SHALL update URL parameters `dataSourceId` and `database` to match

#### Scenario: Select a connection on the welcome page
- **WHEN** a user selects a NAMESPACE in the tree while no editor tab is open
- **THEN** the workspace SHALL update URL parameters and tree highlight and SHALL NOT create a SQL tab

#### Scenario: Identify the active tab
- **WHEN** more than one SQL tab is open
- **THEN** the active tab SHALL use a contrasting background and a persistent brand indicator along its bottom edge, and inactive tabs SHALL remain visually receded

#### Scenario: Switch tabs
- **WHEN** a user activates another tab
- **THEN** the workspace SHALL restore that tab's bound data source and NAMESPACE in the URL, tree highlight, and status bar without changing other tabs' bindings

#### Scenario: Filter databases and tables under an expanded source
- **WHEN** a user expands a data source
- **THEN** the tree SHALL show two filters under that source, labeled from the catalog NAMESPACE and table-level `filterLabel` values, and SHALL NOT show a global data-source search
- **WHEN** the user types a NAMESPACE name in that source's NAMESPACE filter
- **THEN** only matching NAMESPACE children of that source SHALL remain visible
- **WHEN** the user types a table name in that source's table filter
- **THEN** tables and views under already-expanded NAMESPACE children of that source SHALL be filtered

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
The workspace SHALL browse data sources, NAMESPACE children, tables/views, columns, primary keys, and indexes through the metadata APIs with search and layer-specific refresh. Metadata caches SHALL be keyed by data source id so identically named NAMESPACE values on different sources stay isolated.

#### Scenario: Expand resources
- **WHEN** a user expands a data source, expands a NAMESPACE, and opens a table
- **THEN** the frontend SHALL lazily fetch NAMESPACE children for that source through the existing databases API, then tables/views, then table detail and show fields, key, index, type, nullability, and comments

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

### Requirement: Monaco MySQL editing
The frontend SHALL wrap Monaco with the bound engine's catalog language (MYSQL: `mysql`), formatting, metadata completion, and documented keyboard commands, and SHALL expose whether a selection exists so run affordances can label themselves.

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
- **WHEN** completion is requested for the active connection/NAMESPACE
- **THEN** Monaco SHALL offer known NAMESPACE, table/view, and column names without inserting credentials or untrusted executable snippets
