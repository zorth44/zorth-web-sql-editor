## ADDED Requirements

### Requirement: Engine declares streaming autocommit need
Each `EngineSupport` SHALL tell the SQL service whether JDBC streaming export requires `autoCommit=false` so a positive `streamingFetchSize()` can open a server cursor. The default SHALL be that autocommit may stay on. Export orchestration SHALL call this method and SHALL NOT branch on engine id strings such as `POSTGRESQL`.

#### Scenario: PostgreSQL requires autocommit off for streaming
- **WHEN** the POSTGRESQL engine is asked whether streaming export requires autocommit off
- **THEN** it SHALL report that autocommit must be off

#### Scenario: Default engines keep autocommit on for streaming
- **WHEN** an engine does not override the streaming autocommit requirement, including MYSQL
- **THEN** it SHALL report that autocommit may stay on, and export SHALL NOT set `autoCommit=false` for that engine
