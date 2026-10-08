## MODIFIED Requirements

### Requirement: Data-source request validation
The SQL service SHALL validate strict request DTOs and return `400 VALIDATION_FAILED` with stable field errors for malformed or unsupported values.

#### Scenario: Validate Kerberos fields
- **WHEN** a create or update is submitted for `engine=HIVE_KERBEROS` or `engine=ICEBERG`
- **THEN** the service SHALL require the engine's descriptor-declared required fields (`environment`, `keytabFile`), SHALL validate `queueName` length when present, and SHALL NOT require `host`, `port`, `username`, `password`, or `sslMode`

#### Scenario: Reject an unregistered engine
- **WHEN** a create or update submits `engine` that is absent from the engine registry
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row

### Requirement: Connection-test engine field
Unsaved connection-test request bodies MAY include `engine`. When present it SHALL be a registered engine id; when absent the service SHALL keep the previous MYSQL default.

#### Scenario: Accept a registered engine on unsaved test
- **WHEN** `POST /api/v1/data-sources:test` or `POST /api/v1/data-sources/{id}:test` with a body includes any registered engine id, including `ICEBERG`
- **THEN** the service SHALL accept the field and test using that engine

#### Scenario: Reject an unregistered engine on unsaved test
- **WHEN** a test body includes `engine` that is absent from the registry
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT open a target connection
