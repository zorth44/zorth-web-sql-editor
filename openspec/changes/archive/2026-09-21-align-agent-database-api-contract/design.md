# Design

## Context

See proposal.md for motivation. The Agent adapter under `com.bocsoft.sqleditor.agentapi` already lists datasources/tables/columns, validates SQL, runs plan-only explain, and executes bounded read-only queries. Web metadata already exposes `GET /api/v1/data-sources/{id}/databases` via `MetadataService.databases`. Agent SQL DTOs only declare `database` (plus execution fields), while Agent Platform may send optional `schema`. Jackson is configured with `FAIL_ON_UNKNOWN_PROPERTIES`, so undeclared `schema` becomes `400 VALIDATION_FAILED` / `UNKNOWN_FIELD`. Explain returns plan evidence but no `riskLevel`. Consumer docs expect `LOW`|`MEDIUM`|`HIGH`.

Web SQL uses a single NAMESPACE selector named `database` (MySQL catalog or PostgreSQL schema) applied through `EngineSupport.applyNamespace`. This change must reuse that model.

## Goals / Non-Goals

**Goals:**

- Mirror Web databases listing on the Agent URL space without duplicating JDBC logic.
- Make optional `schema` a first-class Agent wire field that maps into the existing NAMESPACE selector.
- Publish a stable, evidence-only `riskLevel` on Agent explain responses.
- Keep auth (Bearer + optional `X-Internal-Service-Key`) and read-only Agent policy unchanged.

**Non-Goals:**

- Changing Web `/api/v1` request/response shapes.
- Adding a two-level catalog+schema execution API beyond the existing NAMESPACE field.
- Implementing Agent Platform tools, DML/DDL, connection CRUD, history/scripts/export, or explain-analyze on Agent API.
- Fabricating risk when plan evidence is missing.

## Decisions

### 1. Agent databases endpoint delegates to `MetadataService`

Add `GET /internal/api/v1/agent/data-sources/{id}/databases` on `AgentMetadataController` with the same query parameters as Web (`keyword`, `pageSize`, `pageToken`, `includeSystem`). Map `CursorPage<DatabaseItem>` through `AgentApiMapper` into a thin Agent DTO (or reuse `DatabaseItem` if it already matches the Tool contract: `name`, `kind`). Prefer reuse of the shared page type already used by other Agent metadata endpoints.

**Alternative considered:** Point agents at Web `/api/v1/.../databases` — rejected; Agent consumers are locked to `/internal/api/v1/agent/**` and product auth/routing for that namespace.

### 2. `schema` is an alias for the NAMESPACE selector, not a second dimension

Add optional `schema` to `AgentSqlRequest`, `AgentSqlExplainRequest`, and `AgentSqlQueryRequest`.

Resolve effective NAMESPACE for explain and query:

1. Trim; treat blank as absent.
2. If only one of `database` / `schema` is present → use that value as the NAMESPACE (same as Web `database`).
3. If both present and equal → use that value.
4. If both present and unequal → `ApiException.validation` → `400 VALIDATION_FAILED` (do not guess).

Validate continues to ignore namespace for safety evaluation (no connection), but must accept `schema` in JSON so binding succeeds.

**Alternative considered:** Engine-specific preference (MySQL prefer `database`, PostgreSQL prefer `schema`) when both differ — rejected for this change; it invents semantics Web does not have and can silently mis-route. Agents that today send `database=business` + `schema=public` for PostgreSQL must list namespaces and pass the NAMESPACE (schema name) in either field consistently; documenting this in `docs/agent-database-api.md` is part of the change.

**Alternative considered:** Ignore `schema` after accepting it — rejected; consumer sends it expecting selection effect on explain/query.

### 3. Derive `riskLevel` in the Agent mapper from plan evidence

Extend `AgentSqlExplainResponse` with optional `String riskLevel`. Compute in `AgentApiMapper.explain` (or a small pure helper) from `PlanEvidence`:

| `riskLevel` | Rule (first match wins, top to bottom) |
| --- | --- |
| omit / null | `supported=false`, or none of `fullScan`, `estimatedRows`, and risk-bearing findings are present |
| `HIGH` | `fullScan=true`, or any finding code `FULL_TABLE_SCAN`, or `estimatedRows >= 100000` |
| `MEDIUM` | `estimatedRows >= 10000`, or any non-empty findings that did not already classify HIGH |
| `LOW` | `supported=true` with `fullScan=false` (explicit) and no HIGH/MEDIUM triggers |

Do not invent estimates or full-scan flags; only classify from fields already returned. Document the table in `docs/agent-database-api.md`.

**Alternative considered:** Leave risk classification to the consumer — rejected; consumer contract and fixtures already expect provider `riskLevel`, and black-box tests accept provider-supplied values.

### 4. Docs and contract tests as the compatibility surface

Update `docs/agent-database-api.md` endpoint table and SQL body / explain response sections. Extend `AgentApiContractTest` (and integration coverage where databases listing or risk classification needs a real engine) so unknown-field rejection still applies to truly unknown keys, while `schema` succeeds.

## Risks / Trade-offs

- [Agents still send mismatched `database` + `schema`] → Explicit validation error plus docs pointing at NAMESPACE listing; no silent preference.
- [Risk thresholds are heuristic] → Document fixed thresholds; omit risk when evidence is thin; do not change plan normalizers to invent evidence.
- [Duplicate databases endpoint paths] → Accept duplication of URL surface; single service implementation shared with Web.

## Migration Plan

1. Land Agent DTO/controller/mapper changes and contract tests.
2. Update `docs/agent-database-api.md`.
3. Consumer `bddf-agentscope` change `align-database-tools-sql-editor-contract` adopts list-databases, keeps sending optional `schema`, and surfaces `riskLevel` (separate repo).
4. Rollback: remove or unroute the new Agent databases mapping and revert DTO fields; Web APIs unaffected.

## Open Questions

None for this change. Threshold constants for `MEDIUM`/`HIGH` estimated-row cutovers are fixed above and can be tuned later without changing the field contract.
