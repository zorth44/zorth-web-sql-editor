# Local Authorization Service Specification

## Purpose

Define the repository's temporary development/test authorization facade: local-only startup, LDAP-compatible fake login and logout, the internal authorization context endpoint, and browser development interoperability.

## Requirements

### Requirement: Local-only authorization facade
The repository SHALL contain a clearly documented temporary authorization service for development and end-to-end testing that is never selected implicitly in production.

#### Scenario: Start locally
- **WHEN** a developer runs the documented Node command with optional environment configuration
- **THEN** the service SHALL listen on the configured loopback/development port and expose a health response without external packages or databases

#### Scenario: Use production configuration
- **WHEN** production SQL/Web configuration is built or deployed
- **THEN** it SHALL require explicit real authorization URLs and SHALL NOT silently start or route to the fake service

### Requirement: LDAP-compatible fake login and logout
The temporary service SHALL implement the frontend's frozen `/ldap/login` and `/logout` response shapes using ephemeral in-memory Tokens.

#### Scenario: Login with development credentials
- **WHEN** `/ldap/login` receives a non-empty username, compatible encoded password, and supported product type
- **THEN** it SHALL return body-level `code: 200` and a top-level opaque Token without echoing password data

#### Scenario: Reject malformed login
- **WHEN** required login fields are missing or invalid
- **THEN** it SHALL return a safe business failure and SHALL NOT issue a Token

#### Scenario: Logout
- **WHEN** `/logout` receives an issued Bearer Token
- **THEN** it SHALL invalidate that Token and return the compatible success body; subsequent context lookup SHALL be unauthorized

### Requirement: Internal authorization context fake
The temporary service SHALL implement `GET /internal/api/v1/auth/context` from the Bearer Token and configured internal-service key.

#### Scenario: Resolve a valid development Token
- **WHEN** both credentials are valid and the Token is unexpired
- **THEN** the service SHALL return user ID, username, display name, exactly one product ID/name, and a future ISO-8601 expiry

#### Scenario: Reject an invalid service key or Token
- **WHEN** the internal key is wrong or the Token is absent, unknown, logged out, or expired
- **THEN** it SHALL return a safe 401/403 response without exposing configured secrets or other Tokens

### Requirement: Browser development interoperability
The temporary service SHALL support explicitly configured development origins and standard CORS preflight for login/logout calls.

#### Scenario: Call from the configured Web origin
- **WHEN** the local Web application sends allowed JSON requests and Authorization headers
- **THEN** the service SHALL return the required CORS headers and process the request

#### Scenario: Call from an unconfigured origin
- **WHEN** an Origin is not in the configured development allow-list
- **THEN** the service SHALL omit CORS authorization and SHALL NOT broaden access with reflected arbitrary origins
