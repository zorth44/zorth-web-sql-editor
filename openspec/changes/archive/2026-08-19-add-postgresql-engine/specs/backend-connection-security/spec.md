## ADDED Requirements

### Requirement: PostgreSQL JDBC configuration
The POSTGRESQL engine SHALL build target JDBC URLs from structured fields, apply its property allow-list before non-overridable driver settings, and map SSL mode without claiming CA or hostname verification.

#### Scenario: Accept an allowed PostgreSQL property
- **WHEN** the engine is `POSTGRESQL` and `properties` contains a catalogued key such as `ApplicationName`, `stringtype`, `tcpKeepAlive`, or `reWriteBatchedInserts` with an allowed value
- **THEN** the POSTGRESQL engine SHALL include it in the generated JDBC configuration

#### Scenario: Reject PostgreSQL-unsafe properties
- **WHEN** a POSTGRESQL `properties` map contains `currentSchema`, a socket/ssl factory, MySQL-only keys, or any undeclared key
- **THEN** the service SHALL return `400 VALIDATION_FAILED` and SHALL NOT open a connection

#### Scenario: Map PostgreSQL SSL mode
- **WHEN** the engine is `POSTGRESQL` and SSL mode is `DISABLED`, `PREFERRED`, or `REQUIRED`
- **THEN** the engine SHALL apply `sslmode=disable|prefer|require` respectively and SHALL NOT claim CA or hostname verification

#### Scenario: Format a PostgreSQL IPv6 URL
- **WHEN** the pinned target address is IPv6 and the engine is POSTGRESQL
- **THEN** the JDBC URL SHALL be `jdbc:postgresql://[<ipv6>]:<port>/<database>`
