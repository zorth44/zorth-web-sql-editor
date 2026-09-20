## Context

The service currently exposes browser-oriented datasource, metadata, and SQL execution APIs. The Agent consumer already has nine Database Tools but assumes additional endpoints and different DTOs. The service remains the authority for Bearer-derived product visibility, database credentials, dialects, SQL classification, execution limits, history, and JDBC resources. The active change `add-agent-table-info-explain-apis` supplies table statistics and plan execution primitives and is a prerequisite for normalized Agent explain.

## Goals / Non-Goals

**Goals:**

- Provide a narrow versioned HTTP contract covering datasource discovery, table/column metadata, validation, plan-only explain, and bounded read-only query execution.
- Isolate Agent wire DTOs in a dedicated adapter package while reusing existing application/domain services.
- Enforce the user Bearer authorization and all safety controls on every call.
- Supply deterministic provider-side integration fixtures for consumer black-box tests.

**Non-Goals:**

- Replacing or versioning the Web SQL Editor APIs.
- Implementing an Agent runtime, MCP server, prompt, or Tool annotations.
- Exposing DML/DDL, analyzed explain, raw vendor plans, arbitrary SQL rewrites, or datasource credentials.
- Forking connection pools, execution history, cancellation, quota, or engine implementations.

## Decisions

### Add a dedicated `agentapi` adapter package and URL space

The new API uses `/internal/api/v1/agent/data-sources...` and package `com.bocsoft.sqleditor.agentapi`. Controllers, wire DTOs, validation annotations, mappers, and OpenAPI examples live there. They call reusable services; no class in `agentapi` opens JDBC connections or accesses MyBatis mappers.

This is preferred over adding Agent-specific flags and response shapes to Web controllers. An optional internal caller key may protect routing, but every request still requires the original user Bearer Token and derives product visibility from it.

### Reuse Web capabilities below the controller boundary

Datasource list/detail and table detail delegate to existing services. SQL execution is refactored only as needed to accept an internal command with effective row/time/byte/cell limits; Web and Agent controllers then call the same execution core. Agent query always forces `readOnly=true` and `source=AI_AGENT`, and clients cannot submit those fields.

Column search becomes a reusable metadata capability implemented by the engine/catalog boundary using bounded JDBC metadata queries. It does not fan out through HTTP table-detail calls.

### Define an explicit Agent wire contract

Endpoints are:

```text
GET  /internal/api/v1/agent/data-sources
GET  /internal/api/v1/agent/data-sources/{id}
GET  /internal/api/v1/agent/data-sources/{id}/tables
GET  /internal/api/v1/agent/data-sources/{id}/columns
GET  /internal/api/v1/agent/data-sources/{id}/table-detail
POST /internal/api/v1/agent/data-sources/{id}/sql/validate
POST /internal/api/v1/agent/data-sources/{id}/sql/explain
POST /internal/api/v1/agent/data-sources/{id}/sql/query
```

Agent DTOs are stable and JSON-strict. JDBC types are names, identifiers/counts use widths that cannot overflow normal JDBC values, and errors use the existing correlated error envelope with documented Agent codes.

### Make validation and execution share one authoritative policy

A reusable SQL safety evaluator uses the existing scanner/classifier and engine rules. It returns structured facts and violations for validation and is invoked again inside query and explain before acquiring execution resources. Only exactly one supported read-only SELECT is accepted for Agent validation/execution; unknown, contradictory, or multiple statements fail closed. The exact SQL is executed unchanged.

### Normalize plan-only explain at the engine boundary

The existing explain change rewrites and executes dialect-specific plan-only statements. Engine-specific plan normalizers convert the returned plan into optional estimated rows, full-scan evidence, index names, and table plan entries. Raw MySQL/PostgreSQL/GBase plan payloads never leave the service. Unsupported or incomplete evidence is represented explicitly rather than guessed. Analyze is not available in this API.

### Enforce Agent limits during result collection

The execution core accepts server-clamped limits for rows, timeout, total serialized bytes, and per-cell bytes. Limits are applied while reading JDBC results, before constructing an oversized response. Provider configuration defines hard maxima; a request can only lower row count. Oversized text/binary values are safely represented and truncation metadata explains which bound applied.

### Publish provider-owned compatibility fixtures

MVC contract tests lock every path, header, request, response, unknown-field rejection, and error. Testcontainers tests use Auth WireMock, metadata MySQL, and target MySQL/PostgreSQL to exercise the real controller stack. A documented test profile provides a deterministic Token and seed API flow so the Agent repository can create a datasource dynamically; it does not expose a production-only test bypass.

## Risks / Trade-offs

- [Duplicate-looking HTTP resources] → Keep Agent endpoints in a clearly separate versioned namespace and delegate below controllers.
- [Safety classification is not a full SQL parser] → Fail closed on unknowns, reuse the same evaluator at validation and execution, and retain JDBC read-only controls.
- [Plan formats vary] → Normalize inside engine implementations and return absent evidence instead of parsing in the Agent client.
- [Smaller Agent byte/cell limits require execution refactoring] → Extend the shared collector with internal effective limits rather than post-truncating a large materialized Web response.
- [Existing explain change lands later] → Implement metadata/validation/query independently, but do not mark normalized explain complete until its prerequisite is integrated.

## Migration Plan

1. Complete or integrate the plan-only portions of `add-agent-table-info-explain-apis`.
2. Add shared safety/metadata/execution primitives and provider-side tests without exposing endpoints.
3. Add `agentapi` controllers, DTOs, mappings, docs, and OpenAPI.
4. Run provider Testcontainers tests, then the sibling Agent black-box profile against the packaged service.
5. Deploy endpoints disabled from public routing; enable only the internal route used by Agent Platform.

Rollback removes or unroutes the Agent namespace. Existing Web APIs and persisted data remain compatible; query history created with `source=AI_AGENT` remains valid.

## Open Questions

- Whether production additionally requires an internal-service-key header is a deployment choice; if enabled, it MUST be additive to Bearer authorization and documented in both repositories.

