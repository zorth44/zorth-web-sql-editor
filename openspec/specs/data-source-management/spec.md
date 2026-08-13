# Data Source Management Specification

## Purpose

Define the phase-one frontend behavior for listing, creating, editing, testing, and deleting product-scoped data sources safely.

## Requirements

### Requirement: Product-scoped data-source consumption
The frontend SHALL consume the backend-filtered data-source collection without implementing client-side product authorization or binding.

#### Scenario: Request data sources
- **WHEN** an authenticated user opens the data-source list
- **THEN** the frontend SHALL request `/api/v1/data-sources` without a product identifier and render exactly the returned records

#### Scenario: Submit a data-source write
- **WHEN** the frontend creates, updates, tests, or deletes a data source
- **THEN** no request type or payload SHALL contain `productId`, `productIds`, `userId`, or client-selected permission fields

#### Scenario: Display duplicate names
- **WHEN** two returned records have the same name but different IDs
- **THEN** the frontend SHALL render and operate on both records independently using their IDs

### Requirement: Data-source list presentation
The data-source list SHALL present the phase-one operational fields and actions.

#### Scenario: Render a populated list
- **WHEN** the list API returns items
- **THEN** each row SHALL show name, MYSQL type, host and port, default database when present, last test status/time, updated time, and `updatedByName`

#### Scenario: Protect sensitive values
- **WHEN** a list or detail response is rendered
- **THEN** the frontend SHALL never display a database password or raw updater/creator user ID

#### Scenario: Render available actions
- **WHEN** Session includes `DATA_SOURCE_MANAGE`
- **THEN** each row SHALL expose test, edit, and delete actions and SHALL NOT expose a phase-two editor action

### Requirement: Server-side keyword filtering
The frontend SHALL delegate cross-page filtering to the backend.

#### Scenario: Enter a keyword
- **WHEN** the user changes the name/host keyword
- **THEN** the frontend SHALL debounce and send it as the list `keyword`, reset to the first cursor, and SHALL NOT filter only the currently loaded rows

#### Scenario: Clear a keyword
- **WHEN** the user clears the filter
- **THEN** the frontend SHALL request the unfiltered first page

### Requirement: Cursor navigation
The frontend SHALL support previous/next navigation over the backend's opaque cursor sequence.

#### Scenario: Move to the next page
- **WHEN** a response contains `nextPageToken` and the user chooses next
- **THEN** the frontend SHALL request that token and retain the prior cursor for backward navigation

#### Scenario: Move to the previous page
- **WHEN** the user chooses previous after visiting later pages
- **THEN** the frontend SHALL restore the prior cursor from its cursor stack

#### Scenario: Query shape changes
- **WHEN** keyword or page size changes
- **THEN** the frontend SHALL discard the cursor stack and request the first page

#### Scenario: Last page is reached
- **WHEN** `nextPageToken` is null
- **THEN** the next-page control SHALL be disabled

### Requirement: Detail-driven editing
The edit page SHALL load authoritative detail by ID rather than deriving a form from a list row.

#### Scenario: Open an editable data source
- **WHEN** the user navigates to `/data-sources/:id/edit`
- **THEN** the frontend SHALL call `GET /api/v1/data-sources/{id}` and populate all non-password form fields from that response

#### Scenario: Detail is unavailable
- **WHEN** detail loading returns `404 DATA_SOURCE_NOT_FOUND`
- **THEN** the frontend SHALL show “data source does not exist or is no longer visible” and return to the list without claiming a permission denial

#### Scenario: Password is configured
- **WHEN** detail returns `passwordConfigured: true`
- **THEN** the password input SHALL remain empty and indicate that leaving it empty preserves the saved password

### Requirement: Data-source form validation
The create/edit form SHALL validate the documented MYSQL connection and descriptive fields before sending a mutation.

#### Scenario: Validate core fields
- **WHEN** the form is submitted
- **THEN** name SHALL be 1–100 characters, host SHALL contain no protocol, port SHALL be 1–65535, username SHALL be 1–128 characters, password SHALL be at most 1024 characters, timeout SHALL be 1–30 seconds, and description SHALL be at most 500 characters

#### Scenario: Validate create password
- **WHEN** a new data source is submitted without a password
- **THEN** the frontend SHALL block submission and identify the password field

#### Scenario: Allow a duplicate name
- **WHEN** the entered name matches another data-source name
- **THEN** the frontend SHALL allow submission because IDs, not names, are unique

#### Scenario: Select connection options
- **WHEN** the user edits engine, default database, SSL, or JDBC properties
- **THEN** engine SHALL remain fixed to MYSQL, default database SHALL be manual text in phase one, SSL SHALL be one of DISABLED/PREFERRED/REQUIRED, and the form SHALL expose only `serverTimezone`, `characterSetResults`, `zeroDateTimeBehavior`, `tinyInt1isBit`, and `sendFractionalSeconds` with documented values

#### Scenario: Exclude fixed or unsafe JDBC controls
- **WHEN** the JDBC property controls are rendered or a request is mapped
- **THEN** `useUnicode`, `allowPublicKeyRetrieval`, SSL flags, credentials, timeouts, and other undeclared properties SHALL NOT be user-configurable or submitted through `properties`

#### Scenario: Map backend field errors
- **WHEN** a write returns `VALIDATION_FAILED.details.fieldErrors`
- **THEN** the frontend SHALL associate recognized errors with their form controls and display unrecognized errors in a safe summary

### Requirement: Create a data source
The frontend SHALL create a data source with the frozen API contract and refresh visible server state.

#### Scenario: Create succeeds
- **WHEN** a valid create form is submitted and the API returns 201
- **THEN** the frontend SHALL navigate to `/data-sources`, invalidate list queries, and render the created record when returned by the next list request

#### Scenario: Create is pending
- **WHEN** the create request is in flight
- **THEN** the frontend SHALL disable duplicate save/test submission and SHALL NOT automatically retry the request

### Requirement: Update with optimistic locking
The frontend SHALL send a full update containing the loaded configuration version and handle concurrent changes explicitly.

#### Scenario: Preserve the existing password
- **WHEN** an edit form is saved with an empty password
- **THEN** the full PUT request SHALL include the loaded `version` and SHALL omit the password or send its documented empty preserve value

#### Scenario: Replace the password
- **WHEN** a user enters a non-empty edit password
- **THEN** the PUT request SHALL send that password once and SHALL clear it from local form state after completion

#### Scenario: Update succeeds
- **WHEN** the API returns an updated detail response
- **THEN** the frontend SHALL invalidate list/detail queries and return to the list

#### Scenario: Version conflict occurs
- **WHEN** the API returns `409 VERSION_CONFLICT`
- **THEN** the frontend SHALL show who/when updated the record from safe error details and offer to reload current detail instead of silently overwriting it

### Requirement: Test connection configurations
The frontend SHALL distinguish testing a new form, an unsaved edit form, and a saved configuration.

#### Scenario: Test a new form
- **WHEN** the user tests a valid new data-source form
- **THEN** the frontend SHALL call `POST /api/v1/data-sources:test` with connection fields only, including the required password and form timeout

#### Scenario: Test an unsaved edit form
- **WHEN** the user tests edits before saving
- **THEN** the frontend SHALL call `POST /api/v1/data-sources/{id}:test` with the current connection fields and SHALL allow an empty password to reuse the stored credential

#### Scenario: Test a saved list item
- **WHEN** the user invokes test from a list row
- **THEN** the frontend SHALL call `POST /api/v1/data-sources/{id}:test` without a body and invalidate that data source's list/detail state after the result

#### Scenario: Connection test succeeds
- **WHEN** a test result has `status: SUCCESS`
- **THEN** the frontend SHALL display the safe success message, server version, and duration

#### Scenario: Connection test fails
- **WHEN** a test result has `status: FAILED`
- **THEN** the frontend SHALL display the safe message and stable `failureCode` without treating the HTTP 200 response as a successful connection

#### Scenario: Connection test is pending
- **WHEN** a connection test is in flight
- **THEN** the frontend SHALL wait according to the form's 1–30 second timeout, provide pending feedback, and SHALL NOT automatically retry

### Requirement: Delete by ID with typed confirmation
The frontend SHALL require explicit typed confirmation and SHALL delete only the selected ID/version.

#### Scenario: Confirmation name does not match
- **WHEN** the entered confirmation text differs from the displayed data-source name
- **THEN** the delete submission SHALL remain disabled

#### Scenario: Delete a duplicate-named record
- **WHEN** the confirmation matches and multiple records share the same name
- **THEN** the frontend SHALL send DELETE only for the selected row's ID and version

#### Scenario: Delete succeeds
- **WHEN** DELETE returns 204
- **THEN** the frontend SHALL close the dialog, invalidate list/detail queries, and show a success notification

#### Scenario: Data source is in use
- **WHEN** DELETE returns `409 DATA_SOURCE_IN_USE`
- **THEN** the frontend SHALL keep the record, show `details.runningTaskCount`, and allow the dialog to be closed without retrying automatically

### Requirement: Safe data-source error and retry behavior
The frontend SHALL map API failures consistently and retry only safe reads under the documented conditions.

#### Scenario: Detail network failure
- **WHEN** a detail GET fails because of a transient network error
- **THEN** the frontend MAY retry it at most once

#### Scenario: Mutation or test failure
- **WHEN** create, update, delete, or connection test fails
- **THEN** the frontend SHALL NOT retry automatically

#### Scenario: Authentication expires during a data-source action
- **WHEN** an action returns 401
- **THEN** the frontend SHALL clear authentication, remember only the safe current route, and navigate to login

### Requirement: Database credential confidentiality
Database passwords MUST remain transient and absent from rendered/cached/persisted application state.

#### Scenario: Render API data
- **WHEN** list/detail data enters Vue Query or components
- **THEN** it SHALL contain only `passwordConfigured` and SHALL NOT synthesize or display a password value

#### Scenario: Finish a password-bearing request
- **WHEN** a create, update, or connection-test request settles
- **THEN** the frontend SHALL clear transient password request/form values as soon as the active workflow no longer needs them

#### Scenario: Inspect browser persistence
- **WHEN** a user creates, edits, tests, logs out, or receives 401
- **THEN** Local Storage, Session Storage, persisted query caches, logs, and telemetry SHALL contain no database password

#### Scenario: Use development API mocks
- **WHEN** an MSW handler receives a password-bearing create, update, or test request
- **THEN** it SHALL NOT copy the password into Mock database state, success responses, errors, logs, or request-independent fixtures
