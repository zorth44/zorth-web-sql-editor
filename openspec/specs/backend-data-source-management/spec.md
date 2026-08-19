# Backend Data Source Management Specification

## Purpose

Define product-owned data-source persistence, isolation, validation, cursor pagination, confidential projections, CRUD behavior, optimistic locking, and stable errors.

## Requirements

### Requirement: Product-owned data-source persistence
The SQL service SHALL persist each data source under the current authenticated product and SHALL use its UUID ID—not its name or connection fields—as its only identity.

#### Scenario: Create ownership from Session
- **WHEN** a user creates a data source
- **THEN** the service SHALL write `product_id`, creator, and updater fields from the authenticated context and SHALL ignore no client-selected ownership field

#### Scenario: Store duplicate configurations
- **WHEN** the same product or different products create data sources with duplicate names or connection fields
- **THEN** the service SHALL create independent records with different UUID IDs

#### Scenario: Store audit data in UTC
- **WHEN** a data source is created, updated, or its saved connection is tested
- **THEN** the service SHALL persist millisecond-precision UTC timestamps and return ISO-8601 UTC values

### Requirement: Product isolation
The SQL service SHALL scope every data-source query and mutation by both data-source ID and the current product ID.

#### Scenario: Access another product's ID
- **WHEN** a user lists, reads, tests, updates, or deletes a data source owned by another product
- **THEN** the service SHALL behave exactly as for an unknown ID and return `404 DATA_SOURCE_NOT_FOUND` where an item endpoint is involved

#### Scenario: List current product records
- **WHEN** an authenticated user requests the data-source collection
- **THEN** every returned record SHALL belong to the current product and no product identifier SHALL be returned

### Requirement: Data-source request validation
The SQL service SHALL validate strict request DTOs and return `400 VALIDATION_FAILED` with stable field errors for malformed or unsupported values.

#### Scenario: Validate core fields
- **WHEN** a create or update is submitted
- **THEN** the service SHALL require `engine` to be a registered engine id (currently only `MYSQL`), a trimmed 1–100 character name, a protocol-free DNS/IPv4/IPv6 host of at most 255 characters, port 1–65535, a trimmed 1–128 character username, timeout 1–30 seconds, default database of at most 64 characters, description of at most 500 characters, and a password of at most 1024 characters

#### Scenario: Reject an unregistered engine
- **WHEN** a create or update submits `engine` that is absent from the engine registry
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT persist the row

#### Scenario: Reject unknown fields
- **WHEN** a write or connection-test JSON object contains an undeclared field, including `productId`, `productIds`, `userId`, or permission data
- **THEN** the service SHALL reject the request with a field error rather than silently discarding the field

#### Scenario: Normalize textual fields
- **WHEN** accepted textual configuration is persisted
- **THEN** the service SHALL trim name, host, username, default database, and description while preserving password bytes exactly as submitted

### Requirement: Cursor-paginated data-source list
The SQL service SHALL expose `GET /api/v1/data-sources` with product-scoped keyword filtering and stable keyset pagination.

#### Scenario: Request the first page
- **WHEN** no page Token is supplied
- **THEN** the service SHALL return up to the requested page size ordered by `updated_at DESC, id DESC`, with a default size of 20 and a maximum of 100

#### Scenario: Filter by keyword
- **WHEN** a keyword of at most 100 characters is supplied
- **THEN** the service SHALL safely escape SQL wildcard characters and match the current product's name or host

#### Scenario: Continue with an opaque cursor
- **WHEN** a valid next-page Token is supplied with the same keyword and page size
- **THEN** the service SHALL continue strictly after the encoded `updated_at + id` boundary and return another opaque Token only if more records exist

#### Scenario: Reject a changed or tampered cursor
- **WHEN** the cursor signature is invalid or its bound keyword/page size differs from the request
- **THEN** the service SHALL return `400 VALIDATION_FAILED`

### Requirement: Data-source details and response confidentiality
The SQL service SHALL return the documented list and detail projections without exposing ownership internals or credentials.

#### Scenario: Return a list item
- **WHEN** a visible data source is listed
- **THEN** the response SHALL include operational connection fields, `passwordConfigured`, last-test summary, version, updater identity snapshot, and update time but SHALL omit detail-only fields

#### Scenario: Return detail
- **WHEN** a visible data source detail is requested
- **THEN** the response SHALL add timeout, allow-listed properties, description, last-test message, creator snapshot, and creation time

#### Scenario: Protect database credentials
- **WHEN** any success or error response is serialized
- **THEN** it MUST exclude plaintext password, ciphertext, IV, key version, product ID, and complete JDBC URL

### Requirement: Create a data source
The SQL service SHALL create data sources without implicitly testing their connections.

#### Scenario: Create valid configuration
- **WHEN** a valid create request with a non-empty password is received
- **THEN** the service SHALL encrypt and persist it with version 1, return `201` with the detail projection, and set `Location` to `/api/v1/data-sources/{id}`

#### Scenario: Create without password
- **WHEN** the create password is absent, null, or empty
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with a password field error

### Requirement: Replace a data source with optimistic locking
The SQL service SHALL implement `PUT /api/v1/data-sources/{id}` as a full replacement guarded by the submitted version.

#### Scenario: Replace the current version
- **WHEN** the ID/product/version match and the request is valid
- **THEN** the service SHALL replace configurable fields, increment version once, update updater audit fields, close the old pool after commit, and return the new detail

#### Scenario: Preserve the saved password
- **WHEN** an update password is absent, null, or empty
- **THEN** the service SHALL preserve the existing encrypted credential without exposing or decrypting it into the response

#### Scenario: Detect a concurrent update
- **WHEN** the ID belongs to the current product but the submitted version is stale
- **THEN** the service SHALL return `409 VERSION_CONFLICT` with `currentVersion`, `currentUpdatedAt`, and `currentUpdatedByName`

### Requirement: Delete a data source safely
The SQL service SHALL delete only the current product's matching ID and version and SHALL coordinate with running-task and pool lifecycle hooks.

#### Scenario: Delete the current version
- **WHEN** the ID/product/version match and no task is using the data source
- **THEN** the service SHALL commit deletion, close and remove its pool, and return `204` without a body

#### Scenario: Delete a stale version
- **WHEN** the ID belongs to the current product but the submitted delete version is stale
- **THEN** the service SHALL return `409 VERSION_CONFLICT` with the current safe conflict details

#### Scenario: Delete an in-use data source
- **WHEN** the running-task registry reports active work for the visible data source
- **THEN** the service SHALL preserve the record and return `409 DATA_SOURCE_IN_USE` with `runningTaskCount`

### Requirement: Connection-test engine field
Unsaved connection-test request bodies MAY include `engine`. When present it SHALL be a registered engine id; when absent the service SHALL keep the previous MYSQL default.

#### Scenario: Accept a registered engine on unsaved test
- **WHEN** `POST /api/v1/data-sources:test` or `POST /api/v1/data-sources/{id}:test` with a body includes `engine=MYSQL`
- **THEN** the service SHALL accept the field and test using the MYSQL engine

#### Scenario: Reject an unregistered engine on unsaved test
- **WHEN** a test body includes `engine` that is absent from the registry
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with an `engine` field error and SHALL NOT open a target connection

### Requirement: Unified data-source errors
The SQL service SHALL return direct success bodies and the documented `ApiError` structure for failures.

#### Scenario: Return validation errors
- **WHEN** one or more request fields are invalid
- **THEN** the error body SHALL include the final request ID, `VALIDATION_FAILED`, a safe message, and a stable `details.fieldErrors` array

#### Scenario: Handle unexpected failures
- **WHEN** an unhandled server failure occurs
- **THEN** the service SHALL return a safe 5xx `ApiError` without a stack trace, internal class name, credential, JDBC URL, or target-network detail


