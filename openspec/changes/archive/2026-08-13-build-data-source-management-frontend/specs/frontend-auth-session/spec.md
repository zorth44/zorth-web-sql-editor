## ADDED Requirements

### Requirement: LDAP login compatibility
The frontend SHALL authenticate through `POST /ldap/login` using the authorization service's current request and response conventions.

#### Scenario: Submit LDAP credentials
- **WHEN** a user submits a valid username and password
- **THEN** the frontend SHALL send the username, configured `productType`, and the UTF-8 Base64 password followed by a 12-character random suffix

#### Scenario: Interpret authorization business failure
- **WHEN** the authorization service returns HTTP 200 with a body-level `code` other than 200
- **THEN** the frontend SHALL treat the login as failed, display the safe `msg`, and SHALL NOT store a Token

#### Scenario: Complete Token login
- **WHEN** the authorization service returns `code: 200` and a top-level `token`
- **THEN** the frontend SHALL store the Token according to the user's remember choice and validate it through `GET /api/v1/session`

### Requirement: Multi-account login handling
The frontend SHALL support selecting an already-bound local account without implementing account binding or account creation.

#### Scenario: Select a bound account
- **WHEN** login returns `needSelectAccount: true` with `bindAccounts`
- **THEN** the frontend SHALL present those accounts and resubmit the LDAP login with the selected `selectUserId`

#### Scenario: Local account binding is required
- **WHEN** login returns `needBind: true`
- **THEN** the frontend SHALL explain that binding must be completed in the legacy portal and provide the configured legacy-portal navigation

#### Scenario: Cancel account selection
- **WHEN** the user cancels or leaves the account-selection flow
- **THEN** the frontend SHALL clear all volatile password and encoded-credential state

### Requirement: Sensitive login data minimization
The frontend MUST minimize and discard sensitive authorization request and response data.

#### Scenario: Sanitize a login response
- **WHEN** an authorization response contains `ldapUser`, `pwd`, or any unrecognized legacy fields
- **THEN** the authorization adapter SHALL expose only the allow-listed login result fields and SHALL discard the remaining values

#### Scenario: Avoid password persistence
- **WHEN** login, account selection, cancellation, or failure occurs
- **THEN** neither the clear password nor its encoded representation SHALL appear in Local Storage, Session Storage, cookies, query cache, logs, telemetry, or route state

### Requirement: Exclusive Token storage
The frontend SHALL keep at most one local Token copy and SHALL use the remember choice only to select its storage lifetime.

#### Scenario: Login without remember me
- **WHEN** login succeeds with remember me disabled
- **THEN** the frontend SHALL clear both Token stores and write the Token only to Session Storage

#### Scenario: Login with remember me
- **WHEN** login succeeds with remember me enabled
- **THEN** the frontend SHALL clear both Token stores and write the Token only to Local Storage

#### Scenario: Logout or unauthorized response
- **WHEN** logout begins or a SQL API returns HTTP 401
- **THEN** the frontend SHALL clear Tokens from both storage locations and clear authenticated client/query state

### Requirement: Session-backed authentication
The frontend SHALL establish an authenticated UI session only after the SQL service validates the Token.

#### Scenario: Open a protected route with a Token
- **WHEN** a user opens a protected route and a stored Token exists
- **THEN** the frontend SHALL call `GET /api/v1/session` before rendering protected content

#### Scenario: Session validation succeeds
- **WHEN** Session validation returns a user, product, future `expiresAt`, and `DATA_SOURCE_MANAGE`
- **THEN** the frontend SHALL allow phase-one protected navigation and expose the user/product context to the shell

#### Scenario: Session is expired or unauthorized
- **WHEN** Session validation returns 401 or `expiresAt` is not in the future
- **THEN** the frontend SHALL clear local authentication and redirect to `/login`

#### Scenario: Management capability is absent
- **WHEN** Session validation succeeds without `DATA_SOURCE_MANAGE`
- **THEN** the frontend SHALL hide management actions and show an explicit unavailable/forbidden state instead of rendering an operable form

### Requirement: Safe post-login redirects
The frontend MUST only restore internal relative routes after authentication.

#### Scenario: Restore a safe route
- **WHEN** login succeeds with a saved internal relative route such as `/data-sources/abc/edit`
- **THEN** the frontend SHALL navigate to that route

#### Scenario: Reject an unsafe redirect
- **WHEN** a redirect is absolute, protocol-relative, malformed, or points to an authentication loop
- **THEN** the frontend SHALL ignore it and navigate to `/data-sources`

### Requirement: Authenticated request headers
Every SQL service request SHALL carry the active Bearer Token and an independently generated request identifier.

#### Scenario: Send a SQL API request
- **WHEN** the frontend sends a request to the SQL service
- **THEN** it SHALL include `Authorization: Bearer <token>` and a UUID `X-Request-Id`

#### Scenario: Send an authorization login request
- **WHEN** the frontend calls anonymous `/ldap/login`
- **THEN** it SHALL NOT attach a stale Bearer Token

### Requirement: Secure old-system Token bridge
The `/auth/bridge` route MUST accept Tokens only from the configured legacy-system window and origins.

#### Scenario: Accept a valid bridge message
- **WHEN** a message comes from `window.opener`, its origin is allow-listed, and its payload is `{ type: "ZORTH_SQL_AUTH_TOKEN", version: 1, token: string }`
- **THEN** the frontend SHALL validate the Token with Session, store it, enter `/data-sources`, and acknowledge without echoing the Token

#### Scenario: Reject an invalid bridge message
- **WHEN** the source, origin, version, type, or Token value does not match the bridge contract
- **THEN** the frontend SHALL reject the message without storing or logging the Token

#### Scenario: Reject an invalid bridged Token
- **WHEN** the message contract is valid but Session validation fails
- **THEN** the frontend SHALL clear the candidate Token and show a bridge authentication failure

### Requirement: Best-effort logout
The frontend SHALL clear its local session even if authorization-service logout fails.

#### Scenario: Logout succeeds
- **WHEN** the user chooses logout and `POST /logout` returns body-level `code: 200`
- **THEN** the frontend SHALL clear all local authentication/query state and navigate to `/login`

#### Scenario: Logout fails
- **WHEN** `POST /logout` fails by network, HTTP, or business code
- **THEN** the frontend SHALL still clear all local authentication/query state and navigate to `/login`
