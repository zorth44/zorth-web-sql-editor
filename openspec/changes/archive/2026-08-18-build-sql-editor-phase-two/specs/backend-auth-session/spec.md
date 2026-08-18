## MODIFIED Requirements

### Requirement: Current Session API
The SQL service SHALL expose `GET /api/v1/session` as the authenticated frontend projection of the current authorization context and the capabilities delivered by phase two.

#### Scenario: Read the current Session
- **WHEN** an authenticated user requests `/api/v1/session`
- **THEN** the service SHALL return the documented user and product objects, `expiresAt` from the Token context, and exactly `DATA_SOURCE_MANAGE`, `SQL_EXECUTE`, `SQL_EXPORT`, and `HISTORY_READ`

#### Scenario: Session response minimizes identity data
- **WHEN** the Session is returned
- **THEN** it SHALL exclude the Bearer Token, internal-service key, passwords, menus, phone numbers, and authorization-service legacy fields

