# Backend Iceberg Engine Specification

## Purpose

Define the `ICEBERG` engine: registration, short-lived Kerberos + ZooKeeper service-discovery connections, its descriptor and keytab directory, and passwordless creation.

## Requirements

### Requirement: ICEBERG engine registration
The SQL service SHALL register an `ICEBERG` `EngineSupport` at startup in addition to `MYSQL`, `POSTGRESQL`, `GBASE_8A`, `HIVE`, and `HIVE_KERBEROS`. Target JDBC, metadata, statement scanning, connection-failure classification, and session restore for `engine=ICEBERG` SHALL use that implementation. It SHALL reuse the Hive engine's catalog and scanner and SHALL connect through the Kerberos + ZooKeeper connector.

#### Scenario: Dispatch a saved Iceberg data source
- **WHEN** a visible data source with `engine=ICEBERG` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the ICEBERG engine, delegate catalog/scan to the Hive engine, and connect through the Kerberos + ZooKeeper connector

#### Scenario: Reject unregistered engines
- **WHEN** a create or update submits `engine` other than a registered id
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

### Requirement: ICEBERG descriptor and keytab directory
The `ICEBERG` descriptor SHALL declare `family=HIVE_WIRE`, `editorLanguage=hive`, a backtick `identifierQuote`, `defaultPort=10000`, and `connectionFields` of `environment` (SELECT, required), `keytabFile` (TEXT, required), `queueName` (TEXT, optional), and optional `defaultDatabase`. It SHALL resolve its keytab file under the `iceberg` subdirectory of the configured keytab base path and SHALL NOT declare `host`, `port`, `username`, `password`, or `sslMode`.

#### Scenario: Render the Iceberg connection form
- **WHEN** the catalog returns ICEBERG
- **THEN** the connection fields SHALL be `environment`, `keytabFile`, `queueName`, and `defaultDatabase`, matching HIVE_KERBEROS, and SHALL NOT include host, port, username, password, or sslMode

#### Scenario: Resolve the Iceberg keytab directory
- **WHEN** an ICEBERG data source with environment `E` and keytab file name `f` connects
- **THEN** the connector SHALL load `<keytab-base-path>/iceberg/f` and SHALL NOT expose that path in any response or log

#### Scenario: Iceberg uses short-lived connections
- **WHEN** the ICEBERG engine is asked how connections are acquired
- **THEN** it SHALL report `requiresHostResolution()=false` and `usesPooledConnections()=false`

### Requirement: ICEBERG accepts no password
Creating, updating, or testing an `ICEBERG` data source SHALL NOT require a database password, because authentication is by keytab. The service SHALL NOT return a `password` field error for `ICEBERG` requests.

#### Scenario: Create an Iceberg data source without a password
- **WHEN** a create request has `engine=ICEBERG` with a valid environment and keytab file and no password
- **THEN** the service SHALL accept and persist the row with no password credential
