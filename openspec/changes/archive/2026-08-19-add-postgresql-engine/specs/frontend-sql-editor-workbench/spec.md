## MODIFIED Requirements

### Requirement: Catalog-driven NAMESPACE navigator
The resource tree SHALL treat the first layer under a data source as `NAMESPACE` and SHALL load it through the selected engine's catalog `listEndpoint`. For MYSQL and POSTGRESQL that endpoint is the existing databases API. Tree labels and filter placeholders SHALL come from that engine's `resourceTree` entry.

#### Scenario: Expand MYSQL namespaces through databases
- **WHEN** the user expands a data source whose `engine` is MYSQL
- **THEN** the frontend SHALL call `GET /api/v1/data-sources/{id}/databases` and render each item as a NAMESPACE node using the catalog NAMESPACE label

#### Scenario: Expand POSTGRESQL schemas through databases
- **WHEN** the user expands a data source whose `engine` is POSTGRESQL
- **THEN** the frontend SHALL call the same databases API, render each item as a NAMESPACE node, and SHALL use the POSTGRESQL catalog labels (模式 / 筛选模式)

#### Scenario: Bind the editor from a NAMESPACE node
- **WHEN** the user selects a NAMESPACE or a table under it
- **THEN** the workspace SHALL bind the active tab using the existing `dataSourceId` and `database` URL parameters, where `database` holds the NAMESPACE name (MySQL catalog or PostgreSQL schema)

#### Scenario: Ignore unknown tree kinds
- **WHEN** a catalog `resourceTree` contains a kind other than NAMESPACE, TABLE, or VIEW
- **THEN** the frontend SHALL skip that level without failing the tree render

### Requirement: Editor language from engine catalog
The SQL editor SHALL set Monaco language from the bound data source engine's `editorLanguage`. MYSQL SHALL use `mysql`. POSTGRESQL SHALL use `pgsql`. If the catalog is unavailable or the language is not registered, the editor SHALL fall back to `mysql` and remain editable.

#### Scenario: Open a MYSQL-bound tab
- **WHEN** the active tab is bound to a MYSQL data source
- **THEN** Monaco SHALL use language `mysql`

#### Scenario: Open a POSTGRESQL-bound tab
- **WHEN** the active tab is bound to a POSTGRESQL data source
- **THEN** Monaco SHALL use language `pgsql`

#### Scenario: Open an unbound welcome-created tab
- **WHEN** a SQL tab has no data source yet
- **THEN** the editor SHALL use `mysql` until a data source is bound

## ADDED Requirements

### Requirement: Engine-specific identifier quoting
Insert-from-tree SQL and table DATA preview SHALL quote identifiers with the bound engine's `identifierQuote` (MYSQL backtick, POSTGRESQL double quote).

#### Scenario: Insert a PostgreSQL table preview
- **WHEN** the user inserts a preview SELECT from a POSTGRESQL table
- **THEN** the inserted SQL SHALL quote the schema and table with double quotes
