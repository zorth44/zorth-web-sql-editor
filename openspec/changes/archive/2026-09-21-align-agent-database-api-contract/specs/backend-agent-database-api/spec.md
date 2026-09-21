# Spec Delta: backend-agent-database-api

## MODIFIED Requirements

### Requirement: Agent API exposes bounded datasource and metadata discovery
The API SHALL expose datasource list/detail, namespace (databases) listing, table list/search, table detail, and cross-table column search for visible datasources. `GET /internal/api/v1/agent/data-sources/{id}/databases` SHALL reuse the same NAMESPACE listing semantics as Web `GET /api/v1/data-sources/{id}/databases` (MySQL catalogs or PostgreSQL schemas as `kind=NAMESPACE`), including optional keyword/pagination/`includeSystem` query parameters supported by the shared metadata service. Responses SHALL use stable cursor pagination and Agent DTOs, SHALL represent JDBC types by canonical names, and MUST NOT include passwords, encrypted credentials, JDBC URLs, raw DDL unless explicitly documented for table detail, or connection properties not required by the Tool contract.

#### Scenario: Datasources and tables are discovered
- **WHEN** an authorized Agent lists datasources and searches tables with bounded page sizes
- **THEN** stable summaries and cursor tokens are returned without credential material

#### Scenario: Namespaces are listed
- **WHEN** an authorized Agent calls `GET /internal/api/v1/agent/data-sources/{id}/databases` for a visible datasource
- **THEN** the service returns a cursor page of NAMESPACE items with the same engine-specific listing and system-namespace hiding behavior as the Web databases endpoint

#### Scenario: Table detail is requested
- **WHEN** an authorized Agent requests a visible table
- **THEN** the response contains normalized columns, keys, indexes, optional safe DDL/statistics, and canonical string JDBC type names

#### Scenario: Columns are searched
- **WHEN** an authorized Agent searches a column keyword within a namespace
- **THEN** the engine/catalog layer returns a bounded page of matching table-column summaries without controller-level table-detail fan-out

### Requirement: Agent SQL validation is authoritative and non-executing
`POST /internal/api/v1/agent/data-sources/{id}/sql/validate` SHALL inspect the exact input using a reusable service-side safety evaluator and SHALL return `valid`, statement type, read-only status, multi-statement status, referenced tables when known, warnings, and violations. The request body SHALL accept optional `database` and optional `schema` as known JSON fields; presence of `schema` MUST NOT cause `UNKNOWN_FIELD`. Optional namespace fields do not change validation's non-executing nature. `valid=true` SHALL require exactly one supported read-only SELECT. Validation MUST NOT borrow a target connection or execute business SQL.

#### Scenario: One SELECT is valid
- **WHEN** a syntactically supported single SELECT is validated
- **THEN** the response reports `valid=true`, `statementType=SELECT`, `readOnly=true`, and `multiStatement=false`

#### Scenario: Optional schema is accepted
- **WHEN** a validate body includes optional `schema` (alone or with `database`)
- **THEN** the request is accepted by JSON binding and is not rejected solely for an unknown `schema` field

#### Scenario: Write or multiple statements are validated
- **WHEN** SQL is DML, DDL, procedural, ambiguous, or contains more than one statement
- **THEN** validation returns `valid=false` with stable violations and executes no business SQL

### Requirement: Agent explain returns normalized plan-only evidence
`POST /internal/api/v1/agent/data-sources/{id}/sql/explain` SHALL independently apply the Agent safety evaluator to the exact SQL, obtain only a plan-only explain through the engine and shared explain execution path, and return an engine-neutral result. The request body SHALL accept optional `database` and optional `schema`; the service SHALL resolve them into the single existing NAMESPACE selector used by Web SQL execution/explain (`database` / `applyNamespace`) and MUST NOT invent a separate catalog-plus-schema execution model. The response SHALL include optional `riskLevel` with value `LOW`, `MEDIUM`, or `HIGH` when plan evidence is sufficient to classify risk, and SHALL omit or null `riskLevel` when explain is unsupported or evidence is insufficient. The endpoint MUST NOT accept or execute analyzed explain and MUST NOT return raw vendor plans.

#### Scenario: Safe SELECT is explained
- **WHEN** one supported read-only SELECT is submitted
- **THEN** the service returns supported status and only evidence-backed optional estimates, full-scan indicators, indexes, per-table plans, findings, and optional `riskLevel`

#### Scenario: Schema selects the namespace
- **WHEN** explain supplies `schema` without `database`, or supplies equal `database` and `schema` values
- **THEN** the shared explain path applies that value as the NAMESPACE selector exactly as Web SQL uses `database`

#### Scenario: Conflicting namespace selectors are rejected
- **WHEN** explain supplies non-blank `database` and `schema` with unequal values
- **THEN** the service returns `400 VALIDATION_FAILED` without borrowing a target connection for explain

#### Scenario: Full-scan evidence yields HIGH risk
- **WHEN** a supported explain reports `fullScan=true` or a `FULL_TABLE_SCAN` finding
- **THEN** the response includes `riskLevel=HIGH`

#### Scenario: Analyze or unsafe SQL is submitted
- **WHEN** the request contains analyzed explain, DML, DDL, ambiguous SQL, or multiple statements
- **THEN** the service rejects it before target execution and creates no analyzed execution

#### Scenario: Engine cannot normalize evidence
- **WHEN** plan-only explain is unsupported or lacks portable details
- **THEN** the response explicitly reports unsupported or absent fields and does not fabricate risk or estimates

### Requirement: Agent query execution is strictly read-only and bounded
`POST /internal/api/v1/agent/data-sources/{id}/sql/query` SHALL accept the exact SQL, optional namespace selectors (`database` and/or `schema`), client-generated execution ID, and bounded row request. Optional `schema` SHALL be a known JSON field and MUST NOT cause `UNKNOWN_FIELD`. The service SHALL resolve `database`/`schema` into the single existing NAMESPACE selector used by Web SQL execution and MUST reject unequal non-blank pairs with `400 VALIDATION_FAILED`. The service SHALL ignore no safety-relevant field: it MUST force `readOnly=true` and `source=AI_AGENT`, re-evaluate the exact SQL atomically before acquiring execution resources, and execute it unchanged through the shared execution core. The client MUST NOT be able to submit `readOnly` or `source` fields.

#### Scenario: SELECT executes successfully
- **WHEN** an authorized request supplies one supported read-only SELECT and a unique execution ID
- **THEN** the shared execution core returns ordered columns and rows, counts, duration, truncation/masking/findings as applicable, and history records `source=AI_AGENT`

#### Scenario: Schema selects the query namespace
- **WHEN** a query body includes optional `schema` that resolves to a valid NAMESPACE
- **THEN** execution applies that NAMESPACE through the shared execution path as Web SQL does with `database`

#### Scenario: Direct write bypass is attempted
- **WHEN** validation was skipped or a different write/multi-statement SQL reaches query execution
- **THEN** execution-time safety rejects it before target execution, history insertion, or connection borrowing

#### Scenario: Unknown request field attempts to weaken policy
- **WHEN** the query body contains `readOnly`, `source`, identity, credential, or another unknown field
- **THEN** strict JSON binding returns a correlated validation error
