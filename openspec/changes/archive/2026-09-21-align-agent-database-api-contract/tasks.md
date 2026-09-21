# Tasks

## 1. Agent databases listing

- [x] 1.1 Add `GET /internal/api/v1/agent/data-sources/{id}/databases` on `AgentMetadataController` delegating to `MetadataService.databases` with Web-equivalent query params (`keyword`, `pageSize`, `pageToken`, `includeSystem`) and verify the method compiles and is routed under `/internal/api/v1/agent`
- [x] 1.2 Map namespace items through `AgentApiMapper` to a stable Agent page DTO (`name`, `kind`) and verify mapper unit or contract coverage returns `kind=NAMESPACE` without credentials

## 2. Optional `schema` on SQL bodies

- [x] 2.1 Add optional `schema` getter/setter to `AgentSqlRequest`, `AgentSqlExplainRequest`, and `AgentSqlQueryRequest` and verify MVC contract tests accept bodies that include `schema` without `UNKNOWN_FIELD`
- [x] 2.2 Implement NAMESPACE resolution helper (trim; prefer single present field; equal both OK; unequal both → `VALIDATION_FAILED`) and wire it into `AgentSqlService` explain/query paths so the resolved value is passed as today's `database`/NAMESPACE argument; verify unit tests cover schema-only, database-only, equal, and conflict cases
- [x] 2.3 Confirm validate still evaluates SQL without borrowing a connection when `schema` is present and verify existing validate success/failure scenarios still pass

## 3. Explain `riskLevel`

- [x] 3.1 Add optional `riskLevel` to `AgentSqlExplainResponse` and derive it in `AgentApiMapper` (or helper) from `PlanEvidence` using the design rules (`HIGH` for fullScan/`FULL_TABLE_SCAN`/estimatedRows≥100000; `MEDIUM` for estimatedRows≥10000 or other findings; `LOW` for explicit non-full-scan; omit when unsupported/insufficient) and verify unit tests assert each band and omit cases
- [x] 3.2 Extend Agent explain contract fixtures/tests so a full-scan plan returns `riskLevel=HIGH` and unsupported/partial evidence omits or nulls `riskLevel`

## 4. Docs and regression

- [x] 4.1 Update `docs/agent-database-api.md` with the databases endpoint, `schema` on validate/explain/query bodies (NAMESPACE alias + conflict rule), and explain `riskLevel` derivation table; verify the doc lists the new path alongside existing Agent endpoints
- [x] 4.2 Run Agent-focused tests (`AgentApiContractTest` and related agentapi tests) and fix regressions so schema acceptance, databases listing, and riskLevel assertions pass
