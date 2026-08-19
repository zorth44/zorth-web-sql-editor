## MODIFIED Requirements

### Requirement: Current-user history detail
The SQL service SHALL expose full statement text and execution summary only to the history creator.

#### Scenario: Read own history detail
- **WHEN** the current user requests an execution they created
- **THEN** the service SHALL return SQL text, connection snapshots, status/result summary, `sqlState`, `vendorErrorCode` when a vendor code was recorded, and `connectionAvailable` based on current product-scoped data-source visibility, without returning result rows and without a `mysqlErrorCode` field

#### Scenario: Read another user's or unknown detail
- **WHEN** the history ID is unknown or belongs to another user
- **THEN** the service SHALL return `404 EXECUTION_NOT_FOUND`

#### Scenario: Read history for a removed connection
- **WHEN** the user still owns history but its data source is deleted or no longer visible
- **THEN** SQL text SHALL remain readable and `connectionAvailable` SHALL be false without exposing current data-source details

## ADDED Requirements

### Requirement: Vendor-neutral execution error persistence
The SQL service SHALL persist driver error codes in `vendor_error_code` and SHALL NOT persist or serialize them as `mysql_error_code` / `mysqlErrorCode`.

#### Scenario: Persist a failed execution
- **WHEN** target JDBC fails with a non-zero driver error code
- **THEN** the history row SHALL store that value in `vendor_error_code` and the detail API SHALL return it as `vendorErrorCode`
