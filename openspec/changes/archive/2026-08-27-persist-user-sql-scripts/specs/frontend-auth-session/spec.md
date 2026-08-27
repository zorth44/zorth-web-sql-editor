## MODIFIED Requirements

### Requirement: Session-backed authentication
The frontend SHALL establish an authenticated UI session only after the SQL service validates the Token and SHALL expose only capabilities returned for the deployed phase.

#### Scenario: Open a protected route with a Token
- **WHEN** a user opens a protected route and a stored Token exists
- **THEN** the frontend SHALL call `GET /api/v1/session` before rendering protected content

#### Scenario: Session validation succeeds
- **WHEN** Session validation returns a user, product, future `expiresAt`, and the deployed capability set
- **THEN** the frontend SHALL allow protected navigation and expose user/product/capability context to the shell and workspace

#### Scenario: Session is expired or unauthorized
- **WHEN** Session validation returns 401 or `expiresAt` is not in the future
- **THEN** the frontend SHALL clear local authentication, editor drafts/results, and query state and redirect to `/login`

#### Scenario: A feature capability is absent
- **WHEN** Session validation succeeds without `DATA_SOURCE_MANAGE`, `SQL_EXECUTE`, `SQL_EXPORT`, `HISTORY_READ`, or `SCRIPT_MANAGE`
- **THEN** the frontend SHALL hide or disable only the corresponding operation and show an explicit unavailable state while backend errors remain authoritative
