# Spec Delta

## ADDED Requirements

### Requirement: Engine collects table statistics
Each `EngineSupport` SHALL collect table statistics for `table-detail` using that engine's catalog, not a shared SQL dialect. The orchestrator SHALL attach the engine-neutral `stats` object (or `null` on collection failure) and SHALL NOT branch on engine id strings such as `MYSQL` or `POSTGRESQL` to issue `SHOW TABLE STATUS`.

#### Scenario: MySQL status through the engine
- **WHEN** table-detail runs against a MYSQL data source
- **THEN** the MYSQL engine SHALL read table status with quoted identifiers and map it to the documented `stats` fields

#### Scenario: Orchestrator does not emit SHOW TABLE STATUS
- **WHEN** table-detail runs for any registered engine
- **THEN** metadata orchestration SHALL call the engine and SHALL NOT concatenate `SHOW TABLE STATUS` in a shared service class

### Requirement: Engine rewrites explain statements
Each `EngineSupport` SHALL rewrite a single user statement into a plan-only explain or an analyzed explain for its dialect. Plan-only rewrite SHALL NOT include ANALYZE. Analyzed rewrite SHALL include ANALYZE and SHALL use the engine's JSON or default plan format. The engine SHALL report whether a statement is already an analyzed explain. Execution orchestration SHALL call these engine methods and SHALL NOT assemble `EXPLAIN` / `EXPLAIN ANALYZE` / `EXPLAIN (FORMAT JSON)` by engine id strings.

#### Scenario: MySQL plan-only rewrite
- **WHEN** the MYSQL engine rewrites a `SELECT` for plan-only explain
- **THEN** the resulting text SHALL be a MySQL `EXPLAIN` that does not include `ANALYZE`

#### Scenario: Detect analyzed explain
- **WHEN** the engine is asked whether `EXPLAIN ANALYZE SELECT 1` or `EXPLAIN (ANALYZE, FORMAT JSON) SELECT 1` is an analyzed explain
- **THEN** it SHALL report that analyzed execution was requested
