## Why

The service's HTTP API was designed for the Web SQL Editor, while an Agent consumer needs a smaller, stable, strictly read-only capability contract with normalized metadata, validation, explain, and bounded query results. Reusing browser DTOs directly would couple two different clients and leaves the current Agent unable to call several required capabilities.

## What Changes

- Add a dedicated `agentapi` adapter package with versioned Agent-facing controllers, request/response DTOs, mappers, and error contracts; it delegates to existing datasource, metadata, engine, and execution services and never owns JDBC or persistence logic.
- Require the original user Bearer Token and request ID on every Agent API call so existing product visibility and user authorization remain authoritative; an internal-service credential, if deployed, is additive and never replaces user identity.
- Expose Agent-oriented datasource listing/detail, table search, table detail, and cross-table column search without changing existing Web SQL Editor endpoints.
- Expose authoritative SQL validation for exactly one read-only statement, returning structured facts and violations without executing business SQL.
- Expose normalized plan-only explain by adapting the plan execution supplied by `add-agent-table-info-explain-apis`; analyzed explain is not exposed through this Tool API.
- Expose bounded read-only query execution by adapting the existing `SqlExecutionService`, forcing `readOnly=true` and `source=AI_AGENT`, and returning a stable Agent result envelope.
- Add OpenAPI examples, contract tests, Testcontainers integration coverage, and a repeatable black-box test fixture used by the Agent repository.

## Capabilities

### New Capabilities

- `backend-agent-database-api`: Versioned Agent-facing database capability API, authorization model, stable metadata/safety/explain/query contracts, and black-box test fixture.

### Modified Capabilities

None. Existing Web SQL Editor API behavior remains unchanged; this capability delegates to it at the application/service boundary.

## Impact

- Backend packages: new `com.bocsoft.sqleditor.agentapi` controllers/DTOs/mappers and reusable application services where current logic is trapped behind Web controllers.
- Existing services reused: auth context, datasource visibility, `MetadataService`, engine SPI, SQL scanner/classifier, `SqlExecutionService`, history, limits, cancellation, and the plan-only explain implementation from `add-agent-table-info-explain-apis`.
- API surface: new versioned Agent endpoints; no breaking change to `/api/v1/data-sources`, `/api/v1/sql/executions`, or Web frontend contracts.
- Consumers: `/Users/zorth/Code/ai/bddf-agentscope` change `integrate-web-sql-database-service`.
- Tests/deployment: Auth WireMock plus Testcontainers metadata and target databases; optional internal-service-key configuration if the deployment requires service-to-service caller authentication.
