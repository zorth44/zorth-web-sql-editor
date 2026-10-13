# sql-optimizer-ui Specification

## Purpose

Define the SQL optimizer UI: capability-gated navigation and creation, idempotent recovery, user-scoped task queries with opaque cursors and active polling, and evidence-based report rendering with terminal-only retry.

## Requirements
### Requirement: Capability gated navigation and creation
The UI SHALL expose the optimizer only with SQL_OPTIMIZE. Signed-in `/sql-optimizer` SHALL show the task list and a primary link to `/sql-optimizer/new`. That page SHALL submit one SQL with a visible datasource and explicit namespace through the shared API contract.

#### Scenario: Missing capability
- **WHEN** a signed-in user without SQL_OPTIMIZE visits the page
- **THEN** navigation is hidden and the page prevents API operations and explains lack of access

#### Scenario: Valid submission
- **WHEN** a valid single SQL is submitted from `/sql-optimizer/new`
- **THEN** the UI sends an Idempotency-Key and navigates to the accepted task ID only after 202

### Requirement: Idempotent recovery
The UI MUST retain the original operation payload/key after uncertain network failure and MUST not automatically resubmit mutations.

#### Scenario: Response lost
- **WHEN** creation or terminal retry loses its response
- **THEN** explicit recovery uses the same operation key to find the same task

#### Scenario: Input edited
- **WHEN** creation input changes after a failure
- **THEN** a new submission uses a new key and preserves the edited input on rejection

### Requirement: User scoped task queries
The UI SHALL provide status filtering, opaque cursor navigation and user/product-scoped queries, polling active tasks every approximately five seconds only while visible.

#### Scenario: Terminal task
- **WHEN** a task becomes SUCCEEDED or FAILED
- **THEN** detail polling stops and terminal content remains readable after navigation or refresh

#### Scenario: Network interruption
- **WHEN** a task query fails temporarily
- **THEN** the last known server state is preserved and a query error with refresh is displayed

### Requirement: Evidence based report and retry
The UI MUST render supported valid reports as plain text in verdict/issues/suggestions/limitations order, distinguish policy priority from performance severity, and permit retry only for terminal tasks.

#### Scenario: Evidence gaps
- **WHEN** a successful report contains no issues and nonempty limitations
- **THEN** the UI explains evidence gaps without claiming all rules passed

#### Scenario: Linked suggestion and malicious text
- **WHEN** a suggestion links an issue and content contains HTML
- **THEN** the user can locate that issue and HTML is shown as text without execution

#### Scenario: Unsupported report
- **WHEN** schema version or report shape is invalid
- **THEN** the UI displays a report format error without guessing report data

### Requirement: Acceptance boundaries
The change SHALL provide unit/component/API and browser evidence with production Mock disabled, SHALL record a Mock-disabled acceptance run against real services when they are available, and SHALL keep every item that run did not cover listed separately.

#### Scenario: Local delivery
- **WHEN** local implementation is delivered
- **THEN** typecheck, lint, tests, build and optimizer E2E results plus commits and recovery record are available

#### Scenario: Real stack delivery
- **WHEN** a Mock-disabled build runs against real task APIs, a real Agent Platform and a real target database
- **THEN** capability gating, creation, polling, report rendering, page close, refresh, retry, cross-user isolation, cursor contract and credential expiry are recorded with actual task identifiers, and uncovered production items such as identity adapter and credential custody stay listed as pending

