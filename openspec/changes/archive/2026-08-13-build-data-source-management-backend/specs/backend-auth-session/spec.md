## ADDED Requirements

### Requirement: Bearer Token authentication
The SQL service SHALL require an `Authorization: Bearer` Token for every `/api/v1/**` request and SHALL derive identity only from the external authorization context service.

#### Scenario: Authenticate a valid Token
- **WHEN** a request carries a Token that the authorization context service resolves successfully
- **THEN** the SQL service SHALL establish an immutable current-user context containing user ID, username, display name, the unique product ID and name, and Token expiry

#### Scenario: Reject a missing or invalid Token
- **WHEN** the Bearer Token is missing, malformed, expired, logged out, or rejected by the authorization context service
- **THEN** the SQL service SHALL return `401 UNAUTHENTICATED` without invoking the requested business operation

#### Scenario: Ignore client-selected identity
- **WHEN** a client submits a user ID, product ID, product list, or permission field in parameters or JSON
- **THEN** the SQL service SHALL NOT use that value as authentication or authorization evidence

### Requirement: External authorization context contract
The SQL service SHALL consume `GET /internal/api/v1/auth/context` using the incoming Bearer Token and configured internal-service credential without depending on authorization-service Java packages, databases, or Redis keys.

#### Scenario: Resolve context remotely
- **WHEN** authentication requires an uncached context
- **THEN** the SQL service SHALL call the configured authorization endpoint with the original Bearer Token and `X-Internal-Service-Key` and project only the documented context fields

#### Scenario: Authorization context is structurally invalid
- **WHEN** the authorization endpoint returns a successful status but omits a unique user, unique product, or valid expiry
- **THEN** the SQL service SHALL return `409 USER_PRODUCT_CONTEXT_INVALID` and SHALL NOT create an authenticated context

#### Scenario: Authorization service reports invalid product cardinality
- **WHEN** the authorization endpoint returns `USER_PRODUCT_CONTEXT_INVALID` with a product count
- **THEN** the SQL service SHALL preserve that stable code and safe product-count detail in its response

### Requirement: Token context cache
The SQL service SHALL cache successful authorization contexts by the SHA-256 digest of the Token for at most 60 seconds without storing or logging the raw Token.

#### Scenario: Reuse an unexpired cached context
- **WHEN** the same Token is used while its cache entry and Token expiry are both still valid
- **THEN** the SQL service SHALL reuse the cached context without extending either expiry

#### Scenario: Cached Token has expired
- **WHEN** `tokenExpiresAt` is no longer in the future even though the cache TTL has not elapsed
- **THEN** the SQL service SHALL evict the cache entry and return `401 UNAUTHENTICATED`

#### Scenario: Authorization service is unavailable with no cache
- **WHEN** context resolution times out or fails and no valid cached context exists
- **THEN** the SQL service SHALL fail closed with `503 AUTH_SERVICE_UNAVAILABLE`

### Requirement: Current Session API
The SQL service SHALL expose `GET /api/v1/session` as the authenticated frontend projection of the current authorization context.

#### Scenario: Read the current Session
- **WHEN** an authenticated user requests `/api/v1/session`
- **THEN** the service SHALL return the documented user and product objects, `expiresAt` from the Token context, and exactly the `DATA_SOURCE_MANAGE` capability for phase one

#### Scenario: Session response minimizes identity data
- **WHEN** the Session is returned
- **THEN** it SHALL exclude the Bearer Token, internal-service key, passwords, menus, phone numbers, and authorization-service legacy fields

### Requirement: Correlated API requests
The SQL service SHALL assign a safe request identifier to every request and make it available in the response and diagnostic context.

#### Scenario: Accept a valid request identifier
- **WHEN** `X-Request-Id` contains a valid UUID
- **THEN** the service SHALL reuse it and return it in the response header

#### Scenario: Replace an invalid request identifier
- **WHEN** `X-Request-Id` is absent or invalid
- **THEN** the service SHALL generate a UUID and return the generated value in the response header and any error body

### Requirement: Authentication secret confidentiality
Authentication processing MUST prevent Tokens and internal-service credentials from entering application logs, metrics labels, error bodies, persistence, or health details.

#### Scenario: Diagnose an authorization failure
- **WHEN** an authorization call fails or an authenticated request is rejected
- **THEN** logs SHALL contain the request ID and safe failure classification but SHALL NOT contain the raw Token or internal-service key

