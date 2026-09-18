# Spec Delta

## MODIFIED Requirements

### Requirement: Execution source attribution
The SQL service SHALL persist an execution `source` of `WEB_SQL_EDITOR`, `AI_AGENT`, `AI_AGENT_EXPLAIN`, or `AI_AGENT_EXPLAIN_ANALYZE` on every execution-history row. `POST /api/v1/sql/executions` MAY include `source` of `WEB_SQL_EDITOR` or `AI_AGENT`; when omitted or blank the service SHALL store `WEB_SQL_EDITOR`. `POST /api/v1/sql/explains` SHALL store `AI_AGENT_EXPLAIN` and SHALL NOT accept a client `source`. `POST /api/v1/sql/explains:analyze` SHALL store `AI_AGENT_EXPLAIN_ANALYZE` and SHALL NOT accept a client `source`. Export history SHALL store `WEB_SQL_EDITOR`. `source` is audit metadata and SHALL NOT authorize the statement or imply `readOnly`.

#### Scenario: Persist an Agent source
- **WHEN** a validated execution request includes `source` equal to `AI_AGENT`
- **THEN** the RUNNING history row SHALL store `AI_AGENT` and the current-user history list and detail SHALL return that value as `source`

#### Scenario: Default editor source
- **WHEN** a validated execution request omits `source`
- **THEN** the history row SHALL store `WEB_SQL_EDITOR`

#### Scenario: Persist a plan-only explain source
- **WHEN** a validated `POST /api/v1/sql/explains` request is accepted
- **THEN** the RUNNING history row SHALL store `AI_AGENT_EXPLAIN` and the current-user history list and detail SHALL return that value as `source`

#### Scenario: Persist an analyzed explain source
- **WHEN** a validated `POST /api/v1/sql/explains:analyze` request is accepted
- **THEN** the RUNNING history row SHALL store `AI_AGENT_EXPLAIN_ANALYZE` and the current-user history list and detail SHALL return that value as `source`

#### Scenario: Reject an unknown source
- **WHEN** a `POST /api/v1/sql/executions` request includes `source` that is not `WEB_SQL_EDITOR` or `AI_AGENT`
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with a field error on `source` and SHALL NOT insert history
