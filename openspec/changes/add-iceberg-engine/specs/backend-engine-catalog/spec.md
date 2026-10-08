## MODIFIED Requirements

### Requirement: Authenticated engine catalog
The SQL service SHALL expose `GET /api/v1/engines` to authenticated callers and SHALL return one descriptor per registered `EngineSupport` without opening a target connection.

#### Scenario: List registered engines
- **WHEN** an authenticated user calls `GET /api/v1/engines`
- **THEN** the service SHALL return `{ items }` containing every registered engine in registry order, currently `MYSQL`, `POSTGRESQL`, `GBASE_8A`, `HIVE`, `HIVE_KERBEROS`, then `ICEBERG`, and SHALL NOT require `DATA_SOURCE_MANAGE`

#### Scenario: Reject an unauthenticated catalog request
- **WHEN** the request has no valid session
- **THEN** the service SHALL return `401` and SHALL NOT return engine descriptors

#### Scenario: Catalog does not leak credentials or JDBC URLs
- **WHEN** the catalog is serialized
- **THEN** each item SHALL omit passwords, complete JDBC URLs, CIDR policy, keytab paths, realms, zk quorum, and encryption material

### Requirement: Engine descriptor shape
Each catalog item SHALL describe how to render a connection form, JDBC property controls, the resource tree, and the editor language for that engine.

#### Scenario: Return the ICEBERG descriptor
- **WHEN** the registry contains ICEBERG
- **THEN** that item SHALL include `id=ICEBERG`, `displayName` for Iceberg, `family=HIVE_WIRE`, `defaultPort=10000`, `editorLanguage=hive`, a backtick `identifierQuote`, and `connectionFields` of `environment` (SELECT, required), `keytabFile` (TEXT, required), `queueName` (TEXT, optional), and optional `defaultDatabase`, matching the HIVE_KERBEROS field shape

#### Scenario: Declare NAMESPACE as the first tree level
- **WHEN** a descriptor is returned
- **THEN** `resourceTree` SHALL use product kinds `NAMESPACE`, `TABLE`, and `VIEW` (and MAY later include `PARTITION`) and SHALL NOT use a vendor-only kind such as `CATALOG` or `SCHEMA`

#### Scenario: Map DEFAULT_NAMESPACE to defaultDatabase
- **WHEN** any engine lists the default database field
- **THEN** that field SHALL have `name=defaultDatabase` and `kind=DEFAULT_NAMESPACE` and SHALL NOT introduce a new persisted JSON field
