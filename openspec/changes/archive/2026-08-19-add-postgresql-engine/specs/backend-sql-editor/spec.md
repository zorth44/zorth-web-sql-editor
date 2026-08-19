## ADDED Requirements

### Requirement: PostgreSQL metadata through the NAMESPACE adapter
`GET /api/v1/data-sources/{id}/databases` SHALL remain the NAMESPACE list endpoint for POSTGRESQL. For a POSTGRESQL data source the items SHALL be schemas in the pinned database, each with `kind=NAMESPACE`. Table and table-detail APIs SHALL keep using query parameter/field `database` as the parent schema name.

#### Scenario: List PostgreSQL schemas
- **WHEN** databases are listed for a visible POSTGRESQL data source
- **THEN** the service SHALL return schema names as NAMESPACE items, hide `information_schema` and `pg_`-prefixed schemas by default, and SHALL NOT fail solely because MYSQL catalog APIs are unused

#### Scenario: Execute with schema as database
- **WHEN** SQL execution against a POSTGRESQL data source includes `database` equal to a visible schema
- **THEN** the service SHALL apply that schema on the connection and SHALL accept a single statement after the POSTGRESQL scanner ignores delimiters inside dollar-quotes, strings, quoted identifiers, and comments
