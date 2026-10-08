## MODIFIED Requirements

### Requirement: Data-source request validation
The SQL service SHALL validate strict request DTOs and return `400 VALIDATION_FAILED` with stable field errors for malformed or unsupported values.

#### Scenario: Validate core fields
- **WHEN** a create or update is submitted for a host-based engine (`MYSQL`, `POSTGRESQL`, `GBASE_8A`, `HIVE`)
- **THEN** the service SHALL require `engine` to be a registered engine id, a trimmed 1–100 character name, a protocol-free DNS/IPv4/IPv6 host of at most 255 characters, port 1–65535, a trimmed 1–128 character username, timeout 1–30 seconds, default database within the selected engine's identifier limit, description of at most 500 characters, and a password of at most 1024 characters

#### Scenario: Validate Kerberos fields
- **WHEN** a create or update is submitted for `engine=HIVE_KERBEROS`
- **THEN** the service SHALL require the engine's descriptor-declared required fields (`environment`, `keytabFile`), SHALL validate `queueName` length when present, and SHALL NOT require `host`, `port`, `username`, `password`, or `sslMode`

#### Scenario: Require PostgreSQL default database
- **WHEN** a POSTGRESQL create or update omits `defaultDatabase`
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with a `defaultDatabase` field error

#### Scenario: Reject an unregistered engine
- **WHEN** a create or update submits `engine` that is absent from the engine registry
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row

#### Scenario: Reject unknown fields
- **WHEN** a write or connection-test JSON object contains an undeclared field, including `productId`, `productIds`, `userId`, or permission data
- **THEN** the service SHALL reject the request with a field error rather than silently discarding the field

#### Scenario: Normalize textual fields
- **WHEN** accepted textual configuration is persisted
- **THEN** the service SHALL trim name, host, username, default database, and description while preserving password bytes exactly as submitted

### Requirement: Connection-test engine field
Unsaved connection-test request bodies MAY include `engine`. When present it SHALL be a registered engine id; when absent the service SHALL keep the previous MYSQL default.

#### Scenario: Accept a registered engine on unsaved test
- **WHEN** `POST /api/v1/data-sources:test` or `POST /api/v1/data-sources/{id}:test` with a body includes any registered engine id, including `HIVE_KERBEROS`
- **THEN** the service SHALL accept the field and test using that engine

#### Scenario: Reject an unregistered engine on unsaved test
- **WHEN** a test body includes `engine` that is absent from the registry
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT open a target connection

### Requirement: Create a data source
The SQL service SHALL create data sources without implicitly testing their connections.

#### Scenario: Create valid configuration
- **WHEN** a valid create request is received
- **THEN** the service SHALL encrypt and persist the password for host-based engines (or persist no credential for `HIVE_KERBEROS`), set version 1, return `201` with the detail projection, and set `Location` to `/api/v1/data-sources/{id}`

#### Scenario: Create without password
- **WHEN** the create password is absent, null, or empty for a host-based engine
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with a password field error

#### Scenario: Create a Kerberos data source without a password
- **WHEN** the create has `engine=HIVE_KERBEROS`, a valid `environment` and `keytabFile`, and no password
- **THEN** the service SHALL create the row without a password credential and SHALL NOT return a password field error
