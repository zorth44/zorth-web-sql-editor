## MODIFIED Requirements

### Requirement: Navigable MySQL metadata
The SQL service SHALL expose paginated database, table/view, and table-detail APIs using safe JDBC metadata access.

#### Scenario: List databases
- **WHEN** `GET /api/v1/data-sources/{id}/databases` receives a valid keyword, page size, page Token, and `includeSystem` value
- **THEN** it SHALL return a stable name-ordered cursor page and SHALL hide `information_schema`, `performance_schema`, `mysql`, and `sys` by default

#### Scenario: List databases without a default database
- **WHEN** the saved data source has a null or blank `defaultDatabase` and the target MySQL account can see at least one non-system database
- **THEN** `GET /api/v1/data-sources/{id}/databases` SHALL return those visible databases and SHALL NOT return `METADATA_QUERY_FAILED` or an empty page solely because no default database is configured

#### Scenario: List tables and views
- **WHEN** `GET /api/v1/data-sources/{id}/tables` receives a visible database, optional keyword, and declared TABLE/VIEW types
- **THEN** it SHALL return up to 200 matching objects with database, name, type, and safe comment fields

#### Scenario: Read table structure
- **WHEN** `GET /api/v1/data-sources/{id}/table-detail` receives a visible database and table
- **THEN** it SHALL return ordered columns, primary-key fields, ordered indexes including uniqueness and type, and the table DDL from `SHOW CREATE TABLE`

#### Scenario: Reject unsafe metadata input
- **WHEN** a database/table identifier, type, page size, or cursor is malformed or tampered
- **THEN** the service SHALL return a stable validation/not-found error and SHALL NOT concatenate the value into executable SQL
