## ADDED Requirements

### Requirement: GBase 8a engine registration
The SQL service SHALL register a `GBASE_8A` `EngineSupport` at startup in addition to `MYSQL` and `POSTGRESQL`. Target JDBC, metadata, statement scanning, connection-failure classification, and session restore for `engine=GBASE_8A` SHALL use that implementation. The implementation SHALL hang on the MySQL wire family and SHALL NOT change `EngineSupport` or orchestrators.

#### Scenario: Dispatch a saved GBase 8a data source
- **WHEN** a visible data source with `engine=GBASE_8A` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the GBASE_8A engine and SHALL assemble `jdbc:gbase://` URLs with the MYSQL family's SSL flags, property allow-list, catalog NAMESPACE, and statement scanner

#### Scenario: Reject 8c, 8s, Hive, or other unregistered engines
- **WHEN** a create or update submits `engine` other than `MYSQL`, `POSTGRESQL`, or `GBASE_8A`
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

### Requirement: GBase 8a is MYSQL_WIRE with catalog NAMESPACE
For GBASE_8A, `GET /api/v1/data-sources/{id}/databases` SHALL list catalogs. `defaultDatabase` SHALL remain optional. The engine SHALL NOT require a pinned database and SHALL NOT treat schema as the first tree layer.

#### Scenario: List catalogs as NAMESPACE items
- **WHEN** databases are listed for a visible GBASE_8A data source with `includeSystem=false`
- **THEN** the page SHALL return catalog names with `kind=NAMESPACE` using the same system-schema hiding as MYSQL

#### Scenario: Default database stays optional
- **WHEN** a GBASE_8A create, update, or unsaved test omits `defaultDatabase`
- **THEN** the service SHALL accept the request and SHALL NOT return a `defaultDatabase` field error

### Requirement: GBase 8a JDBC uses the official GBase connector
The GBASE_8A engine SHALL build `jdbc:gbase://` URLs and load `com.gbase.jdbc.Driver` from the vendor `gbase-connector-java` package. It SHALL apply MYSQL SSL flags and property allow-list, and classify protocol failures as MYSQL does. Default port in the catalog SHALL be 5258. The engine SHALL NOT use Oracle MySQL Connector/J for GBASE_8A URLs.

#### Scenario: Build a GBase 8a JDBC target
- **WHEN** a GBASE_8A configuration with host, port, username, and password is built
- **THEN** every URL SHALL use the `jdbc:gbase://` scheme, wrap IPv6 hosts in brackets, and SHALL apply the same non-overridable MYSQL security properties

#### Scenario: Catalog default port is 5258
- **WHEN** the GBASE_8A engine reports its descriptor
- **THEN** `defaultPort` SHALL be 5258 and the port connection field default SHALL be `5258`

#### Scenario: Missing official driver is a sanitized connection failure
- **WHEN** `com.gbase.jdbc.Driver` is not on the classpath and a GBASE_8A connection is attempted
- **THEN** the engine SHALL return `CONNECTION_FAILED` with a sanitized message that does not include the JDBC URL or target host
