## MODIFIED Requirements

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
- **THEN** the service SHALL discard that connection instead of returning it to the pool with a leftover `USE`

#### Scenario: Test a connection
- **WHEN** either connection-test endpoint runs
- **THEN** it SHALL use and close a short-lived connection and SHALL NOT create or populate a dynamic business pool
