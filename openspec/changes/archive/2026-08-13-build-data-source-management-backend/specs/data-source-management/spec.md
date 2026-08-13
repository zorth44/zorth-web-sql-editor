## MODIFIED Requirements

### Requirement: Data-source form validation
The create/edit form SHALL validate the documented MYSQL connection and descriptive fields before sending a mutation.

#### Scenario: Validate core fields
- **WHEN** the form is submitted
- **THEN** name SHALL be 1–100 characters, host SHALL contain no protocol, port SHALL be 1–65535, username SHALL be 1–128 characters, password SHALL be at most 1024 characters, timeout SHALL be 1–30 seconds, and description SHALL be at most 500 characters

#### Scenario: Validate create password
- **WHEN** a new data source is submitted without a password
- **THEN** the frontend SHALL block submission and identify the password field

#### Scenario: Allow a duplicate name
- **WHEN** the entered name matches another data-source name
- **THEN** the frontend SHALL allow submission because IDs, not names, are unique

#### Scenario: Select connection options
- **WHEN** the user edits engine, default database, SSL, or JDBC properties
- **THEN** engine SHALL remain fixed to MYSQL, default database SHALL be manual text in phase one, SSL SHALL be one of DISABLED/PREFERRED/REQUIRED, and the form SHALL expose only `serverTimezone`, `characterSetResults`, `zeroDateTimeBehavior`, `tinyInt1isBit`, and `sendFractionalSeconds` with documented values

#### Scenario: Exclude fixed or unsafe JDBC controls
- **WHEN** the JDBC property controls are rendered or a request is mapped
- **THEN** `useUnicode`, `allowPublicKeyRetrieval`, SSL flags, credentials, timeouts, and other undeclared properties SHALL NOT be user-configurable or submitted through `properties`

#### Scenario: Map backend field errors
- **WHEN** a write returns `VALIDATION_FAILED.details.fieldErrors`
- **THEN** the frontend SHALL associate recognized errors with their form controls and display unrecognized errors in a safe summary

### Requirement: Database credential confidentiality
Database passwords MUST remain transient and absent from rendered/cached/persisted application state.

#### Scenario: Render API data
- **WHEN** list/detail data enters Vue Query or components
- **THEN** it SHALL contain only `passwordConfigured` and SHALL NOT synthesize or display a password value

#### Scenario: Finish a password-bearing request
- **WHEN** a create, update, or connection-test request settles
- **THEN** the frontend SHALL clear transient password request/form values as soon as the active workflow no longer needs them

#### Scenario: Inspect browser persistence
- **WHEN** a user creates, edits, tests, logs out, or receives 401
- **THEN** Local Storage, Session Storage, persisted query caches, logs, and telemetry SHALL contain no database password

#### Scenario: Use development API mocks
- **WHEN** an MSW handler receives a password-bearing create, update, or test request
- **THEN** it SHALL NOT copy the password into Mock database state, success responses, errors, logs, or request-independent fixtures

