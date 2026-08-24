## ADDED Requirements

### Requirement: Execution source attribution
The SQL service SHALL persist an execution `source` of `WEB_SQL_EDITOR` or `AI_AGENT` on every execution-history row. `POST /api/v1/sql/executions` MAY include `source`; when omitted or blank the service SHALL store `WEB_SQL_EDITOR`. Export history SHALL store `WEB_SQL_EDITOR`. `source` is audit metadata and SHALL NOT authorize the statement or imply `readOnly`.

#### Scenario: Persist an Agent source
- **WHEN** a validated execution request includes `source` equal to `AI_AGENT`
- **THEN** the RUNNING history row SHALL store `AI_AGENT` and the current-user history list and detail SHALL return that value as `source`

#### Scenario: Default editor source
- **WHEN** a validated execution request omits `source`
- **THEN** the history row SHALL store `WEB_SQL_EDITOR`

#### Scenario: Reject an unknown source
- **WHEN** a request includes `source` that is not `WEB_SQL_EDITOR` or `AI_AGENT`
- **THEN** the service SHALL return `400 VALIDATION_FAILED` with a field error on `source` and SHALL NOT insert history

### Requirement: Trusted client IP capture
When inserting a RUNNING execution-history row, the SQL service SHALL store `client_ip`. If the peer address is in `sql-editor.http.trusted-proxy-cidrs`, the service SHALL use the leftmost non-empty `X-Forwarded-For` hop or `X-Real-IP` when that header is the only forwarded address. Otherwise it SHALL store `HttpServletRequest.getRemoteAddr()`. The value SHALL be truncated to 64 characters. History list and detail APIs SHALL NOT return `client_ip`.

#### Scenario: Direct caller without trusted proxies
- **WHEN** `trusted-proxy-cidrs` is empty or the peer is not in the list
- **THEN** the history row SHALL store the servlet remote address and SHALL ignore any `X-Forwarded-For` value supplied by the caller

#### Scenario: Trusted gateway forwards a client address
- **WHEN** the peer address is in `trusted-proxy-cidrs` and `X-Forwarded-For` contains a client address
- **THEN** the history row SHALL store that client address, truncated to 64 characters

#### Scenario: History APIs omit client IP
- **WHEN** the current user reads history list or detail
- **THEN** the JSON SHALL include `source` when present and SHALL NOT include `clientIp` or `client_ip`
