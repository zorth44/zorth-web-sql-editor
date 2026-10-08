## MODIFIED Requirements

### Requirement: Catalog-driven data-source form
The create and edit forms SHALL load `GET /api/v1/engines` and SHALL render engine, connection, and JDBC controls from the selected engine descriptor instead of a MySQL-only template.

#### Scenario: Render Iceberg fields from the descriptor
- **WHEN** Iceberg (`ICEBERG`) is selected
- **THEN** the form SHALL show an environment selector, a keytab file field, an optional queue field, and an optional default database, matching the Kerberos Hive field shape, and SHALL NOT show host, port, username, password, or SSL controls

#### Scenario: Submit the selected engine
- **WHEN** the user creates, updates, or tests from the form
- **THEN** the request SHALL send `engine` equal to the selected catalog id, SHALL send only the connection fields that engine's descriptor declares, and SHALL NOT hard-code `'MYSQL'` in the mapper
