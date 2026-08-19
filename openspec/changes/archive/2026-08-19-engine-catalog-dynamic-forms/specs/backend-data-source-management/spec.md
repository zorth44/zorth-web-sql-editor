## ADDED Requirements

### Requirement: Connection-test engine field
Unsaved connection-test request bodies MAY include `engine`. When present it SHALL be a registered engine id; when absent the service SHALL keep the previous MYSQL default.

#### Scenario: Accept a registered engine on unsaved test
- **WHEN** `POST /api/v1/data-sources:test` or `POST /api/v1/data-sources/{id}:test` with a body includes `engine=MYSQL`
- **THEN** the service SHALL accept the field and test using the MYSQL engine

#### Scenario: Reject an unregistered engine on unsaved test
- **WHEN** a test body includes `engine` that is absent from the registry
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT open a target connection
