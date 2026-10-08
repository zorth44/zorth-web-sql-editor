## MODIFIED Requirements

### Requirement: Authenticated engine catalog
The SQL service SHALL expose `GET /api/v1/engines` to authenticated callers and SHALL return one descriptor per registered `EngineSupport` without opening a target connection.

#### Scenario: List registered engines
- **WHEN** an authenticated user calls `GET /api/v1/engines`
- **THEN** the service SHALL return `{ items }` containing every registered engine in registry order, currently `MYSQL`, `POSTGRESQL`, `GBASE_8A`, `HIVE`, then `HIVE_KERBEROS`, and SHALL NOT require `DATA_SOURCE_MANAGE`

#### Scenario: Reject an unauthenticated catalog request
- **WHEN** the request has no valid session
- **THEN** the service SHALL return `401` and SHALL NOT return engine descriptors

#### Scenario: Catalog does not leak credentials or JDBC URLs
- **WHEN** the catalog is serialized
- **THEN** each item SHALL omit passwords, complete JDBC URLs, CIDR policy, keytab paths, realms, zk quorum, and encryption material

### Requirement: Engine descriptor shape
Each catalog item SHALL describe how to render a connection form, JDBC property controls, the resource tree, and the editor language for that engine.

#### Scenario: Return the MYSQL descriptor
- **WHEN** the registry contains MYSQL
- **THEN** that item SHALL include `id=MYSQL`, `displayName`, `family=MYSQL_WIRE`, `defaultPort=3306`, `editorLanguage=mysql`, capability flags matching the MYSQL engine, `connectionFields` whose `name` values are the existing request fields `host`, `port`, `username`, `password`, `defaultDatabase`, `sslMode`, and `connectTimeoutSeconds`, `propertyFields` for the current MYSQL JDBC allow-list only, and `resourceTree` whose first level is `kind=NAMESPACE` with `listEndpoint=databases`

#### Scenario: Return the HIVE_KERBEROS descriptor
- **WHEN** the registry contains HIVE_KERBEROS
- **THEN** that item SHALL include `id=HIVE_KERBEROS`, `displayName` for Kerberos Hive, `family=HIVE_WIRE`, `editorLanguage=hive`, a backtick `identifierQuote`, and `connectionFields` of `environment` (SELECT, required), `keytabFile` (TEXT, required), `queueName` (TEXT, optional), and optional `defaultDatabase`, and SHALL NOT include `host`, `port`, `username`, `password`, or `sslMode`

#### Scenario: Declare NAMESPACE as the first tree level
- **WHEN** a descriptor is returned
- **THEN** `resourceTree` SHALL use product kinds `NAMESPACE`, `TABLE`, and `VIEW` (and MAY later include `PARTITION`) and SHALL NOT use a vendor-only kind such as `CATALOG` or `SCHEMA`

#### Scenario: Map DEFAULT_NAMESPACE to defaultDatabase
- **WHEN** any engine lists the default database field
- **THEN** that field SHALL have `name=defaultDatabase` and `kind=DEFAULT_NAMESPACE` and SHALL NOT introduce a new persisted JSON field
