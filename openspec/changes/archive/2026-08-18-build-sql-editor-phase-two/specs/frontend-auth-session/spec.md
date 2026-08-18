## MODIFIED Requirements

### Requirement: Session-backed authentication
The frontend SHALL establish an authenticated UI session only after the SQL service validates the Token and SHALL expose only capabilities returned for the deployed phase.

#### Scenario: Open a protected route with a Token
- **WHEN** a user opens a protected route and a stored Token exists
- **THEN** the frontend SHALL call `GET /api/v1/session` before rendering protected content

#### Scenario: Session validation succeeds
- **WHEN** Session validation returns a user, product, future `expiresAt`, and the phase-two capability set
- **THEN** the frontend SHALL allow protected navigation and expose user/product/capability context to the shell and workspace

#### Scenario: Session is expired or unauthorized
- **WHEN** Session validation returns 401 or `expiresAt` is not in the future
- **THEN** the frontend SHALL clear local authentication, editor drafts/results, and query state and redirect to `/login`

#### Scenario: A feature capability is absent
- **WHEN** Session validation succeeds without `DATA_SOURCE_MANAGE`, `SQL_EXECUTE`, `SQL_EXPORT`, or `HISTORY_READ`
- **THEN** the frontend SHALL hide or disable only the corresponding operation and show an explicit unavailable state while backend errors remain authoritative

### Requirement: Safe post-login redirects
The frontend MUST only restore internal relative routes after authentication and SHALL otherwise use the phase-two SQL editor landing page.

#### Scenario: Restore a safe route
- **WHEN** login succeeds with a saved internal relative route such as `/sql-editor?dataSourceId=abc&database=demo`
- **THEN** the frontend SHALL navigate to that route

#### Scenario: Reject an unsafe redirect
- **WHEN** a redirect is absolute, protocol-relative, malformed, or points to an authentication loop
- **THEN** the frontend SHALL ignore it and navigate to `/sql-editor`

### Requirement: Secure old-system Token bridge
The `/auth/bridge` route MUST accept Tokens only from the configured legacy-system window and origins and SHALL enter the phase-two default route after validation.

#### Scenario: Accept a valid bridge message
- **WHEN** a message comes from `window.opener`, its origin is allow-listed, and its payload is `{ type: "ZORTH_SQL_AUTH_TOKEN", version: 1, token: string }`
- **THEN** the frontend SHALL validate the Token with Session, store it, enter `/sql-editor`, and acknowledge without echoing the Token

#### Scenario: Reject an invalid bridge message
- **WHEN** the source, origin, version, type, or Token value does not match the bridge contract
- **THEN** the frontend SHALL reject the message without storing or logging the Token

#### Scenario: Reject an invalid bridged Token
- **WHEN** the message contract is valid but Session validation fails
- **THEN** the frontend SHALL clear the candidate Token and show a bridge authentication failure

