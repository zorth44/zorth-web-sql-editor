## MODIFIED Requirements

### Requirement: Catalog-driven data-source form
The create and edit forms SHALL load `GET /api/v1/engines` and SHALL render engine, connection, and JDBC controls from the selected engine descriptor instead of a MySQL-only template.

#### Scenario: Load the catalog before create
- **WHEN** the user opens the create data-source page
- **THEN** the frontend SHALL fetch the engine catalog, default the type to the first registered engine (MYSQL), and initialize port, SSL, and JDBC defaults from that descriptor

#### Scenario: Render MYSQL fields from the descriptor
- **WHEN** MYSQL is selected
- **THEN** the form SHALL show a type select populated from catalog `displayName` values, connection fields for host/port/username/password/`defaultDatabase`/sslMode/timeout using descriptor labels, and only the MYSQL `propertyFields`

#### Scenario: Render POSTGRESQL fields from the descriptor
- **WHEN** PostgreSQL is selected
- **THEN** the form SHALL use port default 5432, require `defaultDatabase`, show only POSTGRESQL `propertyFields`, and SHALL NOT keep MYSQL JDBC keys such as `serverTimezone`

#### Scenario: Submit the selected engine
- **WHEN** the user creates, updates, or tests from the form
- **THEN** the request SHALL send `engine` equal to the selected catalog id and SHALL NOT hard-code `'MYSQL'` in the mapper

## ADDED Requirements

### Requirement: PostgreSQL default database is required on the form
When the selected engine marks `defaultDatabase` required, the frontend SHALL block create/update/test until that field is non-empty.

#### Scenario: Block empty PostgreSQL default database
- **WHEN** POSTGRESQL is selected and `defaultDatabase` is blank
- **THEN** the frontend SHALL identify the default database field and SHALL NOT send the request
