# Backend Connection Security Specification

## Purpose

Define credential encryption, safe JDBC construction, target-network enforcement, connection testing, sanitized results, and dynamic pool lifecycle requirements.

## Requirements

### Requirement: Versioned credential encryption
The SQL service MUST encrypt every persisted database password with AES-256-GCM, a random 96-bit IV per encryption, and an explicit key version supplied from external secret configuration.

#### Scenario: Persist a new credential
- **WHEN** a data source is created or receives a replacement password
- **THEN** the service SHALL persist ciphertext, IV, and the configured current key version and SHALL NOT persist plaintext

#### Scenario: Required key is missing
- **WHEN** the current key is absent, malformed, not 256 bits, or an existing row references an unavailable key version
- **THEN** the service SHALL fail startup or the affected operation closed and SHALL never fall back to plaintext

#### Scenario: Rotate credential keys
- **WHEN** a new current key version is configured alongside old decryption keys
- **THEN** new encryption SHALL use the new version while existing credentials remain decryptable and can be re-encrypted through the documented rotation operation

### Requirement: Safe JDBC configuration
The SQL service SHALL build target JDBC URLs exclusively through the data source's registered engine from structured validated fields and SHALL apply user properties before that engine's non-overridable security properties.

#### Scenario: Accept an allowed MySQL property
- **WHEN** the engine is `MYSQL` and `properties` contains `serverTimezone`, `characterSetResults`, `zeroDateTimeBehavior`, `tinyInt1isBit`, or `sendFractionalSeconds` with an allowed value
- **THEN** the MYSQL engine SHALL include it in the generated JDBC configuration

#### Scenario: Reject an unknown or unsafe property
- **WHEN** `properties` contains an undeclared key, invalid value, credentials, SSL override, timeout override, multi-query/local-file/deserialization option, proxy/socket factory, connection attribute, or session initialization
- **THEN** the service SHALL return `400 VALIDATION_FAILED` and SHALL NOT open a connection

#### Scenario: Enforce fixed MySQL security properties
- **WHEN** a MYSQL JDBC configuration is built
- **THEN** it SHALL end with non-overridable `allowMultiQueries=false`, `allowLoadLocalInfile=false`, `allowUrlInLocalInfile=false`, `autoDeserialize=false`, `useUnicode=true`, and `characterEncoding=UTF-8`

#### Scenario: Map the SSL mode
- **WHEN** the engine is `MYSQL` and SSL mode is `DISABLED`, `PREFERRED`, or `REQUIRED`
- **THEN** the MYSQL engine SHALL apply the documented Connector/J flags and SHALL NOT claim CA or hostname verification

#### Scenario: Format an IPv6 address
- **WHEN** the pinned target address is IPv6
- **THEN** the JDBC URL SHALL surround it with brackets

#### Scenario: Orchestrators do not hardcode the MySQL URL
- **WHEN** a JDBC target is built for any saved or tested configuration
- **THEN** data-source, pool, and connection-test orchestrators SHALL call the registered engine and SHALL NOT concatenate a `jdbc:mysql://` prefix themselves

### Requirement: Target network policy
The SQL service SHALL resolve a submitted host once, evaluate every A and AAAA result against configured allowed and denied CIDRs, and connect only to an address from that evaluated result set.

#### Scenario: Resolve only allowed addresses
- **WHEN** every resolved address is allowed and none is denied
- **THEN** the service SHALL pin an address from that resolution for the connection attempt without resolving the hostname again

#### Scenario: Resolve any forbidden address
- **WHEN** any resolved address falls outside allowed CIDRs or inside denied CIDRs
- **THEN** the service SHALL reject the request before connecting with `400 VALIDATION_FAILED` and a safe host field error

#### Scenario: Network policy is unsafe or DNS fails
- **WHEN** allowed CIDRs are empty, CIDR configuration is invalid, the host cannot be resolved, or resolution returns no address
- **THEN** the service SHALL fail startup for invalid policy or reject the operation safely without revealing internal network topology

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

### Requirement: Test a visible saved data source
The SQL service SHALL implement both saved and unsaved-edit modes of `POST /api/v1/data-sources/{id}:test` without changing configuration versions.

#### Scenario: Test the persisted configuration
- **WHEN** a visible data-source test is requested with no body
- **THEN** the service SHALL decrypt and test the saved configuration and persist safe last-test status, time, and message without incrementing version

#### Scenario: Test unsaved edits
- **WHEN** a full connection body is supplied for a visible data source
- **THEN** the service SHALL test that body, reuse the saved password when its password is absent/null/empty, and SHALL NOT save form fields or last-test state

### Requirement: Stable and sanitized connection results
The SQL service SHALL return HTTP 200 for both successful target connections and attempted target-connection failures, using stable result fields and failure codes.

#### Scenario: Connection succeeds
- **WHEN** MySQL accepts the connection
- **THEN** the result SHALL contain `SUCCESS`, server version, duration, safe success message, and null `failureCode`

#### Scenario: Connection attempt fails
- **WHEN** MySQL authentication, refusal, timeout, database selection, TLS, or another connection operation fails after validation
- **THEN** the result SHALL contain `FAILED`, duration, a sanitized message, and the corresponding `AUTHENTICATION_FAILED`, `CONNECTION_REFUSED`, `CONNECTION_TIMEOUT`, `DATABASE_NOT_FOUND`, `TLS_FAILED`, or `CONNECTION_FAILED` code

#### Scenario: Sanitize a driver exception
- **WHEN** a JDBC or network exception is handled
- **THEN** neither response nor log SHALL expose the password, complete JDBC URL, stack trace, connection properties, or unnecessary internal address details

### Requirement: Dynamic pool lifecycle foundation
The SQL service SHALL provide bounded, lazy Hikari pools keyed by data-source ID for later SQL consumers while keeping connection tests outside those pools.

#### Scenario: Create a target pool lazily
- **WHEN** an internal business consumer first requests a saved data source pool
- **THEN** the manager SHALL decrypt current configuration, enforce network policy, and create a pool with maximum size 5, minimum idle 0, bounded global pool/connection limits, and configured idle retirement

#### Scenario: Configuration changes or deletion commits
- **WHEN** a data-source update or deletion transaction commits
- **THEN** the manager SHALL close and remove the old pool so the next consumer cannot use stale credentials or network settings

#### Scenario: Return a borrowed connection
- **WHEN** an internal consumer finishes using a pooled connection
- **THEN** it SHALL rollback defensively when needed, restore auto-commit, restore the configured catalog only when `defaultDatabase` is non-empty, clear warnings, and release resources with deterministic close semantics

#### Scenario: Return a connection with no default database
- **WHEN** the saved data source has a null or blank `defaultDatabase`
- **THEN** the service SHALL NOT call `setCatalog(null)` or `setCatalog("")`, and SHALL NOT fail a successful consumer result because catalog restore is impossible on Connector/J

#### Scenario: Discard a switched catalog when no default database exists
- **WHEN** a request changed the connection catalog and the saved data source has no default database
- **THEN** the service SHALL evict that connection from the target Hikari pool instead of returning it with a leftover `USE`, and SHALL NOT abort the physical JDBC connection in a way that leaves a closed connection reusable from the pool

#### Scenario: Test a connection
- **WHEN** either connection-test endpoint runs
- **THEN** it SHALL use and close a short-lived connection and SHALL NOT create or populate a dynamic business pool

### Requirement: Connection secret confidentiality
The SQL service MUST keep database passwords transient outside encrypted persistence.

#### Scenario: Observe service state
- **WHEN** operators inspect API responses, logs, metrics, actuator endpoints, caches, exceptions, configuration diagnostics, or object string representations
- **THEN** no plaintext database password, encryption key, IV/key combination, or complete credential-bearing JDBC URL SHALL be present


