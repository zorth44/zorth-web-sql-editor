## ADDED Requirements

### Requirement: Hive engine registration
The SQL service SHALL register a `HIVE` `EngineSupport` at startup in addition to `MYSQL`, `POSTGRESQL`, and `GBASE_8A`. Target JDBC, metadata, statement scanning, connection-failure classification, and session restore for `engine=HIVE` SHALL use that implementation. The implementation SHALL declare the `HIVE_WIRE` family and SHALL NOT change `EngineSupport` or orchestrators.

#### Scenario: Dispatch a saved Hive data source
- **WHEN** a visible data source with `engine=HIVE` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the HIVE engine, assemble `jdbc:hive2://` URLs, and use the Hive catalog and statement scanner

#### Scenario: Reject unregistered engines
- **WHEN** a create or update submits `engine` other than `MYSQL`, `POSTGRESQL`, `GBASE_8A`, or `HIVE`
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

### Requirement: Hive catalog uses metastore SHOW statements
For `HIVE`, `GET /api/v1/data-sources/{id}/databases` SHALL list databases through `show databases`. Tables SHALL be listed after selecting the database with `use <db>` and issuing `show tables`. Table detail SHALL read columns with `desc <table>` and DDL with `show create table`. The engine SHALL NOT issue MySQL `information_schema` queries. `defaultDatabase` SHALL remain optional and the first tree layer SHALL be `NAMESPACE` (database).

#### Scenario: List databases
- **WHEN** databases are listed for a visible HIVE data source with `includeSystem=false`
- **THEN** the page SHALL return database names obtained from `show databases` as `kind=NAMESPACE` items

#### Scenario: List tables in a database
- **WHEN** tables are listed for a visible HIVE data source and database
- **THEN** the engine SHALL issue `use <database>` followed by `show tables` and return the object names

#### Scenario: Read table detail
- **WHEN** table detail is requested for a HIVE table
- **THEN** the engine SHALL return columns from `desc <table>` and DDL from `show create table`, and SHALL NOT fabricate primary keys, indexes, or foreign keys

#### Scenario: Default database stays optional
- **WHEN** a HIVE create, update, or unsaved test omits `defaultDatabase`
- **THEN** the service SHALL accept the request and SHALL NOT return a `defaultDatabase` field error

### Requirement: Hive relationship metadata is unavailable
The HIVE engine SHALL report `supportsRelationshipMetadata()=false` and SHALL return `TableConstraintMetadata.unavailable()` so metadata orchestration never returns `COMPLETE` relationship coverage for Hive from an assumed schema.

#### Scenario: Request relationships for Hive
- **WHEN** relationships are requested for a visible HIVE data source
- **THEN** the service SHALL return the capability-unavailable result and SHALL NOT fall back to MYSQL relationship behavior
