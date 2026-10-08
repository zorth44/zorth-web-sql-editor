## MODIFIED Requirements

### Requirement: Catalog-driven data-source form
The create and edit forms SHALL load `GET /api/v1/engines` and SHALL render engine, connection, and JDBC controls from the selected engine descriptor instead of a MySQL-only template.

#### Scenario: Load the catalog before create
- **WHEN** the user opens the create data-source page
- **THEN** the frontend SHALL fetch the engine catalog, default the type to the first registered engine (MYSQL), and initialize defaults from that descriptor

#### Scenario: Initialize fields from the selected descriptor
- **WHEN** the user selects any engine in the catalog
- **THEN** the form model SHALL initialize, render, and serialize exactly the connection fields that engine descriptor declares, and the form, model, and validation SHALL NOT hard-code any engine-specific connection field name

#### Scenario: Render MYSQL fields from the descriptor
- **WHEN** MYSQL is selected
- **THEN** the form SHALL show a type select populated from catalog `displayName` values, connection fields for host/port/username/password/`defaultDatabase`/sslMode/timeout using descriptor labels, and only the MYSQL `propertyFields`

#### Scenario: Render POSTGRESQL fields from the descriptor
- **WHEN** PostgreSQL is selected
- **THEN** the form SHALL use port default 5432, require `defaultDatabase`, show only POSTGRESQL `propertyFields`, and SHALL NOT keep MYSQL JDBC keys such as `serverTimezone`

#### Scenario: Render GBase 8a fields from the descriptor
- **WHEN** GBase 8a is selected
- **THEN** the form SHALL use port default 5258, keep `defaultDatabase` optional, show MYSQL-family `propertyFields`, and SHALL NOT keep POSTGRESQL JDBC keys such as `ApplicationName`

#### Scenario: Render Hive fields from the descriptor
- **WHEN** Hive is selected
- **THEN** the form SHALL use port default 10000, keep `defaultDatabase` optional, show only the Hive `propertyFields` (such as `hive.metastore.uris`), and SHALL NOT keep POSTGRESQL JDBC keys such as `ApplicationName`

#### Scenario: Render Kerberos Hive fields from the descriptor
- **WHEN** Kerberos Hive (`HIVE_KERBEROS`) is selected
- **THEN** the form SHALL show an environment selector, a keytab file field, an optional queue field, and an optional default database, and SHALL NOT show host, port, username, password, or SSL controls

#### Scenario: Render Iceberg fields from the descriptor
- **WHEN** Iceberg (`ICEBERG`) is selected
- **THEN** the form SHALL show an environment selector, a keytab file field, an optional queue field, and an optional default database, matching the Kerberos Hive field shape, and SHALL NOT show host, port, username, password, or SSL controls

#### Scenario: Submit the selected engine
- **WHEN** the user creates, updates, or tests from the form
- **THEN** the request SHALL send `engine` equal to the selected catalog id, SHALL send only the connection fields that engine's descriptor declares, and SHALL NOT hard-code `'MYSQL'` in the mapper
