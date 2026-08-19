## MODIFIED Requirements

### Requirement: Safe SQL failure contract
The SQL service SHALL convert target SQL and execution failures into stable API errors without exposing credentials or internals.

#### Scenario: The target engine rejects a statement
- **WHEN** JDBC raises a syntax, permission, object, or other database error
- **THEN** the service SHALL return `422 SQL_EXECUTION_FAILED` with execution ID, SQLState, `vendorErrorCode` (the driver error code, formerly `mysqlErrorCode`), and a sanitized message, and SHALL persist FAILED history

#### Scenario: Observe execution
- **WHEN** execution activity is logged or measured
- **THEN** diagnostics SHALL contain safe IDs, statement hash/type, duration, status, counts, and pool metrics but SHALL exclude SQL text, Token, password, JDBC URL, and result values

## ADDED Requirements

### Requirement: Engine-dispatched metadata and statement scanning
Metadata listing and single-statement scanning SHALL use the data source's registered engine. For `MYSQL` the visible databases, system-schema hiding, `SHOW CREATE TABLE` DDL, backtick quoting, and comment/string-aware statement split SHALL match the existing MySQL behavior.

#### Scenario: Browse MySQL metadata through the engine
- **WHEN** `GET /api/v1/data-sources/{id}/databases`, `/tables`, or `/table-detail` runs against a MYSQL data source
- **THEN** the response SHALL remain the documented MySQL metadata contract, including hiding `information_schema`, `performance_schema`, `mysql`, and `sys` by default and returning table DDL from `SHOW CREATE TABLE`

#### Scenario: Reject extra statements with the engine scanner
- **WHEN** execution text contains more than one non-empty statement after the MYSQL engine ignores delimiters in strings, quoted identifiers, and comments
- **THEN** the service SHALL return `400 MULTI_STATEMENT_NOT_SUPPORTED` before target execution
