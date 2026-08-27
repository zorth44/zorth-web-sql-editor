## ADDED Requirements

### Requirement: Current-user SQL script persistence
The SQL service SHALL persist named SQL scripts in the metadata database as current-user private rows. Script bodies SHALL be stored as SQL text in the metadata schema and SHALL NOT be written to the application host filesystem or an object store. Identity for authorization SHALL come only from the Bearer-derived current user.

#### Scenario: Create a script
- **WHEN** an authenticated user POSTs `/api/v1/sql/scripts` with a non-blank name of at most 100 characters and a non-blank statement whose UTF-8 size is within `sql-editor.execution.max-statement-bytes`
- **THEN** the service SHALL insert a row owned by that `user_id`, snapshot the current `product_id`, initialize `version` to 1, and return `201` with the script detail and a `Location` of `/api/v1/sql/scripts/{id}`

#### Scenario: Create an unbound script
- **WHEN** the create request omits `dataSourceId` or sends it null
- **THEN** the service SHALL store null connection fields and SHALL NOT require a NAMESPACE

#### Scenario: Bind a visible data source on create or update
- **WHEN** the request includes a `dataSourceId` that is visible in the current product
- **THEN** the service SHALL snapshot that source's current name and store the optional NAMESPACE in `database_name`

#### Scenario: Reject an invisible data source on write
- **WHEN** the request includes a `dataSourceId` that is unknown or not visible to the current product
- **THEN** the service SHALL return `404 DATA_SOURCE_NOT_FOUND` and SHALL NOT insert or update the script

#### Scenario: Reject an empty or oversized statement
- **WHEN** `statement` is missing, blank, or exceeds the configured maximum UTF-8 byte size
- **THEN** the service SHALL return `400 VALIDATION_FAILED` for a blank statement or `413 STATEMENT_TOO_LARGE` when over the byte limit, and SHALL NOT persist the row

#### Scenario: Enforce per-user quota
- **WHEN** creating a script would exceed `sql-editor.scripts.max-per-user` (default 200) for the current user
- **THEN** the service SHALL return `409 SCRIPT_QUOTA_EXCEEDED` and SHALL NOT insert the row

#### Scenario: Accept a duplicate name
- **WHEN** the owner creates or updates a script with a name that another of their scripts already uses
- **THEN** the service SHALL persist the row and SHALL NOT reject it as a name conflict

### Requirement: Current-user script list
The SQL service SHALL expose cursor-paginated script summaries ordered by `updated_at DESC, id DESC` and scoped strictly to the current user.

#### Scenario: Filter scripts
- **WHEN** a user supplies a keyword of at most 200 characters or a data-source or NAMESPACE filter
- **THEN** the service SHALL safely escape LIKE wildcards, match the keyword against name and statement text, apply every filter with current `user_id`, and return summaries that include a 240-character `statementSummary` without the full statement

#### Scenario: Continue script pagination
- **WHEN** a valid page Token bound to the same filters and page size is supplied
- **THEN** the service SHALL continue after its encoded `updated_at + id` boundary with a maximum page size of 100

#### Scenario: Attempt to list another user's scripts
- **WHEN** users share a product or a client submits another user identifier
- **THEN** only the authenticated user's rows SHALL be queried and no client-selected identity SHALL affect the result

### Requirement: Current-user script detail
The SQL service SHALL expose full statement text only to the script owner.

#### Scenario: Read own script
- **WHEN** the current user requests a script they own
- **THEN** the service SHALL return name, full statement, connection snapshots, `version`, timestamps, and `connectionAvailable` based on current product-scoped data-source visibility

#### Scenario: Read another user's or unknown script
- **WHEN** the script ID is unknown or belongs to another user
- **THEN** the service SHALL return `404 SCRIPT_NOT_FOUND`

#### Scenario: Read a script whose connection was removed
- **WHEN** the user still owns the script but its data source is deleted or no longer visible
- **THEN** name and SQL text SHALL remain readable and `connectionAvailable` SHALL be false without exposing current data-source details

### Requirement: Optimistic script updates and deletes
The SQL service SHALL update or delete a script only when the caller supplies the current `version`, and SHALL isolate those writes to the owning user.

#### Scenario: Update own script
- **WHEN** the owner PUTs `/api/v1/sql/scripts/{id}` with the current `version` and valid name and statement
- **THEN** the service SHALL replace name, statement, and connection binding, increment `version`, and return the updated detail

#### Scenario: Delete own script
- **WHEN** the owner DELETEs `/api/v1/sql/scripts/{id}` with the current `version`
- **THEN** the service SHALL remove that row and return `204`

#### Scenario: Conflict on stale version
- **WHEN** an update or delete supplies a `version` that is not the current row version
- **THEN** the service SHALL return `409 VERSION_CONFLICT` with `currentVersion`, `currentUpdatedAt`, and `currentUpdatedByName`, and SHALL NOT change the row

#### Scenario: Update or delete another user's script
- **WHEN** the script ID is unknown or belongs to another user
- **THEN** the service SHALL return `404 SCRIPT_NOT_FOUND` without revealing whether the id exists
