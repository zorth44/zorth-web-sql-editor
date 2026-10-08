## ADDED Requirements

### Requirement: Hive JDBC configuration
The HIVE engine SHALL build target JDBC URLs as `jdbc:hive2://<host>:<port>/<database>` from structured fields, bracket IPv6 hosts, and load the Hive JDBC driver. It SHALL apply its property allow-list before non-overridable settings and SHALL NOT apply MySQL or PostgreSQL SSL flags.

#### Scenario: Build a Hive JDBC target
- **WHEN** a HIVE configuration with host, port, username, password, and database is built
- **THEN** every URL SHALL use the `jdbc:hive2://` scheme and SHALL bracket an IPv6 host

#### Scenario: Accept an allowed Hive property
- **WHEN** the engine is `HIVE` and `properties` contains `hive.metastore.uris` with a `thrift://host:port` list
- **THEN** the HIVE engine SHALL include it in the generated JDBC configuration

#### Scenario: Reject Hive-unsafe properties
- **WHEN** a HIVE `properties` map contains an undeclared key, credentials, SSL override, timeout override, a MySQL-only key, or a non-`thrift://` `hive.metastore.uris` value
- **THEN** the service SHALL return `400 VALIDATION_FAILED` and SHALL NOT open a connection

#### Scenario: Missing Hive driver is a sanitized connection failure
- **WHEN** the Hive JDBC driver is not on the classpath and a HIVE connection is attempted
- **THEN** the engine SHALL return `CONNECTION_FAILED` with a sanitized message that does not include the JDBC URL or target host
