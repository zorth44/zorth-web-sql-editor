# Spec Delta

## ADDED Requirements

### Requirement: PostgreSQL table statistics
For `engine=POSTGRESQL`, table-detail `stats` SHALL come from PostgreSQL catalog statistics such as `pg_class` / `pg_stat` relation size and estimated live rows. The engine SHALL NOT run `SHOW TABLE STATUS` or `SHOW CREATE TABLE`. `estimatedRows` SHALL be treated as an estimate. When the relation is missing or statistics are unavailable, `stats` SHALL be `null` without failing column metadata.

#### Scenario: Read estimated rows for a PostgreSQL table
- **WHEN** table-detail is requested for a visible POSTGRESQL table
- **THEN** `stats.estimatedRows` SHALL reflect PostgreSQL estimated cardinality when available, and `stats.dataBytes` / `stats.indexBytes` SHALL reflect relation size when available

#### Scenario: PostgreSQL views without heap status
- **WHEN** table-detail is requested for a PostgreSQL view whose storage statistics are not applicable
- **THEN** the response SHALL still return columns and `ddl`, and `stats` SHALL either be `null` or contain null size fields rather than failing the request

### Requirement: PostgreSQL explain rewrite
The POSTGRESQL engine SHALL rewrite plan-only explain to `EXPLAIN (FORMAT JSON)` wrapping the user statement, and analyzed explain to `EXPLAIN (ANALYZE, FORMAT JSON)` wrapping the user statement. It SHALL treat `EXPLAIN ANALYZE` and `EXPLAIN (ANALYZE ...)` as analyzed explains. Dollar-quoted statements SHALL remain subject to the existing PostgreSQL single-statement scanner.

#### Scenario: Plan-only JSON explain
- **WHEN** `POST /api/v1/sql/explains` runs against a POSTGRESQL data source with `SELECT 1`
- **THEN** the executed statement SHALL be a PostgreSQL `EXPLAIN (FORMAT JSON)` of that query and SHALL NOT include `ANALYZE`

#### Scenario: Analyzed JSON explain
- **WHEN** explain-analyze is enabled and `POST /api/v1/sql/explains:analyze` runs against a POSTGRESQL data source with `SELECT 1`
- **THEN** the executed statement SHALL be a PostgreSQL `EXPLAIN (ANALYZE, FORMAT JSON)` of that query
