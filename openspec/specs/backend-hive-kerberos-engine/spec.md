# Backend Hive Kerberos Engine Specification

## Purpose

Define the `HIVE_KERBEROS` engine: registration, short-lived Kerberos + ZooKeeper service-discovery connections, its descriptor, and passwordless creation.

## Requirements

### Requirement: HIVE_KERBEROS engine registration
The SQL service SHALL register a `HIVE_KERBEROS` `EngineSupport` at startup in addition to `MYSQL`, `POSTGRESQL`, `GBASE_8A`, and `HIVE`. Target JDBC, metadata, statement scanning, connection-failure classification, and session restore for `engine=HIVE_KERBEROS` SHALL use that implementation. It SHALL reuse the Hive engine's catalog and scanner and SHALL connect through the Kerberos + ZooKeeper connector.

#### Scenario: Dispatch a saved HIVE_KERBEROS data source
- **WHEN** a visible data source with `engine=HIVE_KERBEROS` is tested, browsed, executed against, or used for export
- **THEN** the service SHALL use the HIVE_KERBEROS engine, delegate catalog/scan to the Hive engine, and connect through the Kerberos + ZooKeeper connector

#### Scenario: Reject HIVE_KERBEROS-like unregistered engines
- **WHEN** a create or update submits `engine` other than a registered id
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row or open a target connection

### Requirement: HIVE_KERBEROS descriptor declares Kerberos fields
The `HIVE_KERBEROS` descriptor SHALL declare `family=HIVE_WIRE`, `editorLanguage=hive`, a backtick `identifierQuote`, and `connectionFields` of `environment` (SELECT, required), `keytabFile` (TEXT, required), `queueName` (TEXT, optional), and optional `defaultDatabase`. It SHALL NOT declare `host`, `port`, `username`, `password`, or `sslMode`.

#### Scenario: Render the Kerberos connection form
- **WHEN** the catalog returns HIVE_KERBEROS
- **THEN** the connection fields SHALL be `environment`, `keytabFile`, `queueName`, and `defaultDatabase`, and SHALL NOT include host, port, username, password, or sslMode

#### Scenario: Kerberos engine uses short-lived connections
- **WHEN** the HIVE_KERBEROS engine is asked how connections are acquired
- **THEN** it SHALL report `requiresHostResolution()=false` and `usesPooledConnections()=false`

### Requirement: HIVE_KERBEROS accepts no password
Creating, updating, or testing a `HIVE_KERBEROS` data source SHALL NOT require a database password, because authentication is by keytab. The service SHALL NOT return a `password` field error for `HIVE_KERBEROS` requests.

#### Scenario: Create a HIVE_KERBEROS data source without a password
- **WHEN** a create request has `engine=HIVE_KERBEROS` with a valid environment and keytab file and no password
- **THEN** the service SHALL accept and persist the row with no password credential

#### Scenario: Test a HIVE_KERBEROS configuration
- **WHEN** an unsaved test uses `engine=HIVE_KERBEROS` with a valid environment and keytab file
- **THEN** the service SHALL attempt a Kerberos connection without requiring a password
