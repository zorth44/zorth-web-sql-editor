## MODIFIED Requirements

### Requirement: Test unsaved connection configuration
The SQL service SHALL implement `POST /api/v1/data-sources:test` using a short-lived connection and without persisting request data or test state.

#### Scenario: Test valid new configuration
- **WHEN** valid connection fields including a non-empty password are submitted
- **THEN** the service SHALL enforce network and JDBC policy, attempt the connection for at most the requested 1–30 second timeout, close it, and return a connection-test result

#### Scenario: Reject descriptive or ownership fields
- **WHEN** an unsaved test request includes name, description, user, product, or permission fields
- **THEN** the service SHALL return `400 VALIDATION_FAILED` without attempting a connection

#### Scenario: Dispatch the submitted engine
- **WHEN** the test body includes a registered `engine`
- **THEN** the service SHALL build JDBC and classify failures through that engine

#### Scenario: Default MYSQL when engine is omitted
- **WHEN** the test body omits `engine`
- **THEN** the service SHALL dispatch MYSQL and SHALL still attempt the connection when the remaining fields are valid
