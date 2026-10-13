## MODIFIED Requirements

### Requirement: Current Session API
The SQL service SHALL expose `GET /api/v1/session` as the authenticated frontend projection of the current authorization context and the capabilities delivered by the deployed editor phase, including script management.

#### Scenario: Read the current Session
- **WHEN** an authenticated user requests `/api/v1/session`
- **THEN** the service SHALL return the documented user and product objects, `expiresAt` from the Token context, and `DATA_SOURCE_MANAGE`, `SQL_EXECUTE`, `SQL_EXPORT`, `HISTORY_READ`, and `SCRIPT_MANAGE`; it SHALL additionally include `SQL_OPTIMIZE` only when the optimization feature and verified integration are enabled and both configured user and product allowlists permit the principal

#### Scenario: Session response minimizes identity data
- **WHEN** the Session is returned
- **THEN** it SHALL exclude the Bearer Token, internal-service key, passwords, menus, phone numbers, and authorization-service legacy fields

### Requirement: Authentication secret confidentiality
Authentication processing MUST prevent Tokens and internal-service credentials from entering application logs, metrics labels, error bodies, ordinary persistence, or health details; the sole exception SHALL be isolated authenticated-encryption task credentials with independent versioned keys, explicit expiry, task-bound purpose and terminal cleanup.

#### Scenario: Diagnose an authorization failure
- **WHEN** an authorization call fails or an authenticated request is rejected
- **THEN** logs SHALL contain the request ID and safe failure classification but SHALL NOT contain the raw Token or internal-service key
