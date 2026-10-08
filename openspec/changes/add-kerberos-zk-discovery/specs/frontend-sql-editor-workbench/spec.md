## MODIFIED Requirements

### Requirement: Catalog-driven NAMESPACE navigator
The resource tree SHALL treat the first layer under a data source as `NAMESPACE` and SHALL load it through the selected engine's catalog `listEndpoint`. For MYSQL, POSTGRESQL, GBASE_8A, HIVE, and HIVE_KERBEROS that endpoint is the existing databases API. Tree labels and filter placeholders SHALL come from that engine's `resourceTree` entry.

#### Scenario: Expand a Kerberos Hive data source
- **WHEN** the user expands a data source whose `engine` is HIVE_KERBEROS
- **THEN** the frontend SHALL call the same databases API and render each item as a NAMESPACE node using the Hive-family catalog labels (数据库 / 筛选数据库)

#### Scenario: Bind the editor from a NAMESPACE node
- **WHEN** the user selects a NAMESPACE or a table under it
- **THEN** the workspace SHALL bind the active tab using the existing `dataSourceId` and `database` URL parameters, where `database` holds the NAMESPACE name

#### Scenario: Ignore unknown tree kinds
- **WHEN** a catalog `resourceTree` contains a kind other than NAMESPACE, TABLE, or VIEW
- **THEN** the frontend SHALL skip that level without failing the tree render

### Requirement: Editor language from engine catalog
The SQL editor SHALL set Monaco language from the bound data source engine's `editorLanguage`. MYSQL and GBASE_8A SHALL use `mysql`. POSTGRESQL SHALL use `pgsql`. HIVE and HIVE_KERBEROS SHALL use `hive`. If the catalog is unavailable or the language is not registered, the editor SHALL fall back to `mysql` and remain editable.

#### Scenario: Open a Kerberos Hive-bound tab
- **WHEN** the active tab is bound to a HIVE_KERBEROS data source
- **THEN** Monaco SHALL use language `hive`

#### Scenario: Open an unbound welcome-created tab
- **WHEN** a SQL tab has no data source yet
- **THEN** the editor SHALL use `mysql` until a data source is bound
