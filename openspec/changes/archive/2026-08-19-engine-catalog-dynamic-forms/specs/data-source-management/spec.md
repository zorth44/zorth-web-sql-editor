## ADDED Requirements

### Requirement: Catalog-driven data-source form
The create and edit forms SHALL load `GET /api/v1/engines` and SHALL render engine, connection, and JDBC controls from the selected engine descriptor instead of a MySQL-only template.

#### Scenario: Load the catalog before create
- **WHEN** the user opens the create data-source page
- **THEN** the frontend SHALL fetch the engine catalog, default the type to the sole registered engine (MYSQL), and initialize port, SSL, and JDBC defaults from that descriptor

#### Scenario: Render MYSQL fields from the descriptor
- **WHEN** MYSQL is selected
- **THEN** the form SHALL show a type select populated from catalog `displayName` values, connection fields for host/port/username/password/`defaultDatabase`/sslMode/timeout using descriptor labels, and only the MYSQL `propertyFields`

#### Scenario: Submit the selected engine
- **WHEN** the user creates, updates, or tests from the form
- **THEN** the request SHALL send `engine` equal to the selected catalog id and SHALL NOT hard-code `'MYSQL'` in the mapper

## MODIFIED Requirements

### Requirement: Data-source list presentation
The data-source list SHALL present the phase-one operational fields and actions.

#### Scenario: Render a populated list
- **WHEN** the list API returns items
- **THEN** each row SHALL show name, the engine `displayName` from the catalog (falling back to the raw `engine` id), host and port, default database when present, last test status/time, updated time, and `updatedByName`

#### Scenario: Protect sensitive values
- **WHEN** a list or detail response is rendered
- **THEN** the frontend SHALL never display a database password or raw updater/creator user ID

#### Scenario: Render available actions
- **WHEN** Session includes `DATA_SOURCE_MANAGE`
- **THEN** each row SHALL expose test, edit, and delete actions and SHALL NOT expose a phase-two editor action

### Requirement: Data-source form validation
The create/edit form SHALL validate name, description, and the selected engine's catalogued connection and property fields before sending a mutation.

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
- **WHEN** the user edits engine, default namespace, SSL, or JDBC properties
- **THEN** engine SHALL be chosen from the catalog, default namespace SHALL be the `DEFAULT_NAMESPACE` field (`defaultDatabase`) as manual text, SSL SHALL be one of the selected engine's catalogued values (MYSQL: DISABLED/PREFERRED/REQUIRED), and the form SHALL expose only that engine's `propertyFields` with catalogued values

#### Scenario: Exclude fixed or unsafe JDBC controls
- **WHEN** the JDBC property controls are rendered or a request is mapped
- **THEN** keys absent from the selected engine's `propertyFields`, including `useUnicode`, `allowPublicKeyRetrieval`, SSL flags, credentials, timeouts, and other undeclared properties, SHALL NOT be user-configurable or submitted through `properties`

#### Scenario: Map backend field errors
- **WHEN** a write returns `VALIDATION_FAILED.details.fieldErrors`
- **THEN** the frontend SHALL associate recognized errors with their form controls and display unrecognized errors in a safe summary

### Requirement: Test connection configurations
The frontend SHALL distinguish testing a new form, an unsaved edit form, and a saved configuration.

#### Scenario: Test a new form
- **WHEN** the user tests a valid new data-source form
- **THEN** the frontend SHALL call `POST /api/v1/data-sources:test` with connection fields and the selected `engine`, including the required password and form timeout

#### Scenario: Test an unsaved edit form
- **WHEN** the user tests edits before saving
- **THEN** the frontend SHALL call `POST /api/v1/data-sources/{id}:test` with the current connection fields and selected `engine` and SHALL allow an empty password to reuse the stored credential

#### Scenario: Test a saved list item
- **WHEN** the user invokes test from a list row
- **THEN** the frontend SHALL call `POST /api/v1/data-sources/{id}:test` without a body and invalidate that data source's list/detail state after the result

#### Scenario: Connection test succeeds
- **WHEN** a test result has `status: SUCCESS`
- **THEN** the frontend SHALL display the safe success message, server version, and duration

#### Scenario: Connection test fails
- **WHEN** a test result has `status: FAILED`
- **THEN** the frontend SHALL display the safe message and stable `failureCode` without treating the HTTP 200 response as a successful connection

#### Scenario: Connection test is pending
- **WHEN** a connection test is in flight
- **THEN** the frontend SHALL wait according to the form's 1–30 second timeout, provide pending feedback, and SHALL NOT automatically retry
