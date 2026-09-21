# Proposal

## Why

Agent Platform (`bddf-agentscope`) already calls `/internal/api/v1/agent/**`, but three provider gaps block a complete Tool contract: agents cannot list namespaces (databases/schemas), optional `schema` on SQL bodies is rejected as `UNKNOWN_FIELD`, and explain responses omit the `riskLevel` signal consumers document and expect. Closing these gaps keeps the provider aligned with the cross-repo review without widening Agent API beyond read-only discovery, validate, plan-only explain, and bounded query.

## What Changes

- Add `GET /internal/api/v1/agent/data-sources/{id}/databases` that reuses `MetadataService.databases` (same NAMESPACE listing semantics as Web `GET /api/v1/data-sources/{id}/databases`).
- Accept optional `schema` on Agent SQL request DTOs (`AgentSqlRequest`, `AgentSqlExplainRequest`, `AgentSqlQueryRequest`) so strict JSON binding no longer fails with `400 VALIDATION_FAILED` / `UNKNOWN_FIELD`.
- Resolve `database` / `schema` into the existing single NAMESPACE selector used by Web SQL (`applyNamespace` / explain path); do not invent a second catalog+schema execution model.
- Add optional `riskLevel` (`LOW` | `MEDIUM` | `HIGH`) on `AgentSqlExplainResponse`, derived from plan evidence (`fullScan`, `estimatedRows`, findings) with documented rules; omit when unsupported or evidence is insufficient.
- Update `docs/agent-database-api.md` for the databases endpoint, `schema` on SQL bodies, and explain `riskLevel`.
- Extend Agent MVC / integration contract tests to cover the new endpoint, `schema` acceptance and namespace resolution, and `riskLevel` derivation.

## Capabilities

### New Capabilities

None. Behavior extends the existing Agent database Tool API.

### Modified Capabilities

- `backend-agent-database-api`: Add Agent databases/namespaces listing; accept and wire optional `schema` on validate/explain/query bodies into the existing NAMESPACE selector; return evidence-derived optional `riskLevel` on plan-only explain.

## Impact

- Backend: `com.bocsoft.sqleditor.agentapi` controllers, SQL/metadata DTOs, `AgentSqlService` / `AgentApiMapper`, and Agent contract/integration tests.
- Reused services: `MetadataService.databases`, existing explain/query namespace application via `database` / `applyNamespace` — no Web `/api/v1` contract changes.
- Docs: `docs/agent-database-api.md`.
- Consumers: sibling Agent Platform change `align-database-tools-sql-editor-contract` in `bddf-agentscope` (out of scope here).
- Out of scope: DML/DDL, connection CRUD, history, scripts, export, explain-analyze, and any Agent Platform tool implementation.
