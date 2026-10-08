## MODIFIED Requirements

### Requirement: Data-source request validation
The SQL service SHALL validate strict request DTOs and return `400 VALIDATION_FAILED` with stable field errors for malformed or unsupported values.

#### Scenario: Validate core fields
- **WHEN** a create or update is submitted
- **THEN** the service SHALL require `engine` to be a registered engine id (`MYSQL`, `POSTGRESQL`, or `GBASE_8A`), a trimmed 1–100 character name, a protocol-free DNS/IPv4/IPv6 host of at most 255 characters, port 1–65535, a trimmed 1–128 character username, timeout 1–30 seconds, default database within the selected engine's identifier limit, description of at most 500 characters, and a password of at most 1024 characters

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
- **WHEN** `POST /api/v1/data-sources:test` or `POST /api/v1/data-sources/{id}:test` with a body includes `engine=MYSQL`, `engine=POSTGRESQL`, or `engine=GBASE_8A`
- **THEN** the service SHALL accept the field and test using that engine

#### Scenario: Reject an unregistered engine on unsaved test
- **WHEN** a test body includes `engine` that is absent from the registry
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT open a target connection
