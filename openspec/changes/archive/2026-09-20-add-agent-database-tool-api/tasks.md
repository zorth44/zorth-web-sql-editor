## 1. Shared Safety and Execution Foundations

- [x] 1.1 Complete/integrate the plan-only portions of `add-agent-table-info-explain-apis` and expose reusable explain orchestration below its controller
- [x] 1.2 Extract a reusable Agent SQL safety evaluator from the existing scanner/classifier/engine rules that returns structured facts and accepts only one supported read-only SELECT without borrowing a target connection
- [x] 1.3 Invoke the same evaluator atomically inside Agent query and explain before history insertion, quota acquisition, or connection borrowing; add tests proving DML, DDL, ambiguous, analyzed, and multi-statement SQL fail closed
- [x] 1.4 Refactor the shared result collector/execution core to accept internal effective row, timeout, total-byte, and cell-byte limits while preserving existing Web SQL behavior

## 2. Metadata Capabilities

- [x] 2.1 Add an engine/catalog-level bounded cross-table column-search service with cursor pagination and no controller-level table-detail fan-out
- [x] 2.2 Add MySQL/GBase and PostgreSQL column-search implementations and tests for namespace semantics, identifier safety, canonical string JDBC types, comments, nullability, and pagination
- [x] 2.3 Define Agent datasource/table/column/table-detail DTOs and mappers that omit credentials, JDBC URLs, unsafe properties, and undocumented DDL

## 3. Agent API Adapter

- [x] 3.1 Create `com.bocsoft.sqleditor.agentapi` controllers, DTOs, mappers, configuration, and architecture rules under `/internal/api/v1/agent`
- [x] 3.2 Implement datasource list/detail, table list/search, table detail, and column-search endpoints by delegating to existing reusable services with strict JSON and bounded inputs
- [x] 3.3 Implement `sql/validate` with structured statement facts, referenced tables when known, warnings, and stable violations, and prove it performs no business SQL
- [x] 3.4 Implement normalized plan-only `sql/explain` using the shared explain path and engine-specific normalizers; reject ANALYZE/unsafe SQL and never return raw vendor plans
- [x] 3.5 Implement bounded `sql/query` over the shared execution core, force `readOnly=true` and `source=AI_AGENT`, reject client safety/identity fields, and return the canonical Agent query envelope

## 4. Limits, Errors, and Security

- [x] 4.1 Add Agent-specific hard maxima/defaults for rows, timeout, total result bytes, and per-cell bytes; clamp only downward and enforce them during JDBC reading
- [x] 4.2 Define stable correlated Agent error codes/mappings for authentication, visibility, invalid SQL, read-only and multi-statement rejection, unsupported explain, timeout, result limits, execution conflicts, and database failures
- [x] 4.3 Verify every endpoint requires the original Bearer identity and preserves request correlation; if an internal caller key is enabled, prove it is additive and cannot replace user/product authorization
- [x] 4.4 Add logging/audit/redaction tests ensuring Tokens, credentials, connection properties, raw plans, rows, and driver-sensitive messages do not leak; retain controlled SQL execution history behavior

## 5. Provider Contract and Real Integration Tests

- [x] 5.1 Add MVC contract tests for every endpoint, canonical success/error JSON, unknown-field rejection, strict limits, headers, and unchanged Web SQL contracts
- [x] 5.2 Extend Testcontainers integration tests with Auth WireMock, metadata MySQL, target MySQL, and supported PostgreSQL scenarios covering metadata, validation, explain, query types, truncation, and read-only rejection
- [x] 5.3 Add product-isolation and invalid/missing Bearer scenarios, dynamically creating datasources through the public management API rather than inserting provider-internal fixtures
- [x] 5.4 Document a deterministic packaged-service startup and seed flow for the sibling Agent black-box suite without adding a production authentication bypass

## 6. OpenAPI, Operations, and Validation

- [x] 6.1 Publish Agent endpoint OpenAPI descriptions/examples and document versioning, routing, configuration, error codes, limits, and the dependency on `add-agent-table-info-explain-apis`
- [x] 6.2 Run the full backend regression suite and confirm existing Web SQL Editor APIs and frontend contract tests remain unchanged
- [x] 6.3 Run the sibling `integrate-web-sql-database-service` black-box suite against the packaged provider build and record the compatible consumer/provider versions
- [x] 6.4 Run `openspec validate add-agent-database-tool-api --strict`
