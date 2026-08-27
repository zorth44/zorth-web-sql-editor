## ADDED Requirements

### Requirement: PostgreSQL export uses a server cursor
PostgreSQL CSV export SHALL run the replayed statement with `autoCommit=false` and a positive `streamingFetchSize()` so the JDBC driver fetches through a server cursor instead of buffering the complete result set.

#### Scenario: Export a large PostgreSQL result
- **WHEN** CSV export replays a successful POSTGRESQL RESULT_SET statement
- **THEN** the engine SHALL require autocommit off, the export SHALL set `autoCommit=false` before executing, SHALL use a positive fetch size, and SHALL NOT rely on the driver materializing every row before the first CSV record is written

#### Scenario: Return the PostgreSQL connection after export
- **WHEN** a PostgreSQL export finishes, fails, is cancelled, or hits a limit
- **THEN** the service SHALL restore `autoCommit=true` before the connection is reused, rolling back if the stream did not complete successfully
