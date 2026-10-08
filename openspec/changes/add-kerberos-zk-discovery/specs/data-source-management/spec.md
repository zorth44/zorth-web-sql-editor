## MODIFIED Requirements

### Requirement: Catalog-driven data-source form
The create and edit forms SHALL load `GET /api/v1/engines` and SHALL render engine, connection, and JDBC controls from the selected engine descriptor instead of a MySQL-only template.

#### Scenario: Load the catalog before create
- **WHEN** the user opens the create data-source page
- **THEN** the frontend SHALL fetch the engine catalog, default the type to the first registered engine (MYSQL), and initialize defaults from that descriptor

#### Scenario: Render Kerberos Hive fields from the descriptor
- **WHEN** Kerberos Hive (`HIVE_KERBEROS`) is selected
- **THEN** the form SHALL show an environment selector, a keytab file field, an optional queue field, and an optional default database, and SHALL NOT show host, port, username, password, or SSL controls

#### Scenario: Submit the selected engine
- **WHEN** the user creates, updates, or tests from the form
- **THEN** the request SHALL send `engine` equal to the selected catalog id, SHALL send only the connection fields that engine's descriptor declares, and SHALL NOT hard-code `'MYSQL'` in the mapper

### Requirement: Data-source form validation
The create/edit form SHALL validate name, description, and the selected engine's catalogued connection and property fields before sending a mutation.

#### Scenario: Validate core fields
- **WHEN** a host-based engine form is submitted
- **THEN** name SHALL be 1–100 characters, host SHALL contain no protocol, port SHALL be 1–65535, username SHALL be 1–128 characters, password SHALL be at most 1024 characters, timeout SHALL be 1–30 seconds, and description SHALL be at most 500 characters

#### Scenario: Validate Kerberos fields
- **WHEN** a `HIVE_KERBEROS` form is submitted
- **THEN** the frontend SHALL require the descriptor-declared required fields (environment, keytab file), SHALL NOT require a password, and SHALL validate the queue field length when present

#### Scenario: Validate create password
- **WHEN** a new host-based data source is submitted without a password
- **THEN** the frontend SHALL block submission and identify the password field

#### Scenario: Allow a duplicate name
- **WHEN** the entered name matches another data-source name
- **THEN** the frontend SHALL allow submission because IDs, not names, are unique
