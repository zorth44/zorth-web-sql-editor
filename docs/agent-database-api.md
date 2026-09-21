# Agent Database Tool API

Versioned Agent adapter under `/internal/api/v1/agent`. It reuses datasource, metadata, SQL safety, plan-only explain, and execution services. It does not open JDBC itself and does not change `/api/v1` Web SQL Editor contracts.

Depends on the plan-only explain path from `add-agent-table-info-explain-apis` (`EngineSupport.rewriteExplain`, `POST /api/v1/sql/explains`). Analyzed explain is not exposed on this Tool API.

## Authentication

Every call requires the original user `Authorization: Bearer` credential. Product visibility is derived from that credential. `X-Request-Id` is preserved or generated.

Optional additive caller key:

```yaml
sql-editor:
  agent-api:
    internal-caller-key-required: false
    internal-caller-key: ${SQL_EDITOR_AGENT_INTERNAL_CALLER_KEY:}
```

When `internal-caller-key-required` is true, requests must also send `X-Internal-Service-Key`. The key never replaces Bearer identity and cannot supply user or product fields.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/internal/api/v1/agent/data-sources` | Visible datasource summaries |
| GET | `/internal/api/v1/agent/data-sources/{id}` | Datasource detail without credentials/JDBC properties |
| GET | `/internal/api/v1/agent/data-sources/{id}/databases` | NAMESPACE listing (`keyword`, `pageSize`, `pageToken`, `includeSystem`) — MySQL catalogs or PostgreSQL schemas as `kind=NAMESPACE` |
| GET | `/internal/api/v1/agent/data-sources/{id}/tables` | Table search (`database`, `keyword`, `types`, `pageSize`, `pageToken`) |
| GET | `/internal/api/v1/agent/data-sources/{id}/columns` | Cross-table column search (`database`, `keyword` required) |
| GET | `/internal/api/v1/agent/data-sources/{id}/table-detail` | Columns, keys, indexes, optional `ddl`/`stats`, plus bounded `uniqueKeys`, outbound `foreignKeys`, per-section `coverage` and `appliedLimits` |
| GET | `/internal/api/v1/agent/data-sources/{id}/relationships` | One-hop imported/exported edges for one table (`database`, `table`, `direction`, `pageSize`, `pageToken`) |
| POST | `/internal/api/v1/agent/data-sources/{id}/sql/validate` | Non-executing safety facts |
| POST | `/internal/api/v1/agent/data-sources/{id}/sql/explain` | Normalized plan-only explain |
| POST | `/internal/api/v1/agent/data-sources/{id}/sql/query` | Bounded read-only SELECT |

Unknown JSON fields return `400 VALIDATION_FAILED` / `UNKNOWN_FIELD`. Query and explain bodies must not include `readOnly` or `source`. Query always executes with `readOnly=true` and history `source=AI_AGENT`. Explain history is `AI_AGENT_EXPLAIN`.

### SQL body namespace selectors

Validate, explain, and query bodies accept optional `database` and optional `schema`. Both are aliases for the single Web NAMESPACE selector (MySQL catalog or PostgreSQL schema) applied through `applyNamespace`.

Resolution:

1. Trim; blank is absent.
2. Only one present → use that value.
3. Both present and equal → use that value.
4. Both present and unequal → `400 VALIDATION_FAILED` / `CONFLICTING_NAMESPACE` (no silent preference).

Validate accepts `schema` for JSON binding but does not borrow a connection or apply NAMESPACE for safety evaluation.

### Explain `riskLevel`

When plan evidence is sufficient, explain responses include optional `riskLevel` (`LOW` | `MEDIUM` | `HIGH`). Classification is evidence-only (first match wins):

| `riskLevel` | Rule |
| --- | --- |
| omit / null | `supported=false`, or none of `fullScan`, `estimatedRows`, and findings are present |
| `HIGH` | `fullScan=true`, finding code `FULL_TABLE_SCAN` or `FULL_SCAN`, or `estimatedRows >= 100000` |
| `MEDIUM` | `estimatedRows >= 10000`, or other non-empty findings that did not classify HIGH |
| `LOW` | `supported=true` with explicit `fullScan=false` and no HIGH/MEDIUM triggers |

## Limits

```yaml
sql-editor:
  agent-api:
    default-row-limit: 200
    max-row-limit: 1000
    timeout-seconds: 15
    max-result-bytes: 1048576
    max-cell-bytes: 4096
```

A client may lower `maxRows` and `timeoutSeconds` only. Oversized cells become `{ "truncated": true, "byteLength": N }`. Row/byte caps stop collection and set `truncation`.

Metadata section and relationship page caps:

```yaml
sql-editor:
  agent-api:
    max-unique-keys: 32
    max-foreign-keys: 32
    max-indexes: 64
    max-relationships-per-page: 50
```

A client may request a smaller relationship `pageSize` but cannot raise the provider cap. Truncated sections and pages set `coverage=TRUNCATED` and include `appliedLimit`. Confirmed empty sections use `coverage=COMPLETE`. Engines that cannot supply unique/foreign-key/relationship metadata use `coverage=UNAVAILABLE` on table-detail sections, or `422 CAPABILITY_NOT_SUPPORTED` on `/relationships`. Ordinary indexes never create relationships. Unique keys use `UNIQUE_CONSTRAINT` only when the catalog proves a constraint; otherwise `UNIQUE_INDEX`.

### Table detail additions

`GET /internal/api/v1/agent/data-sources/{id}/table-detail` keeps existing column, primary-key, `ddl`, and `stats` fields. Additive structured fields:

```json
{
  "uniqueKeys": [
    { "name": "uk_email", "columns": ["email"], "evidence": "UNIQUE_CONSTRAINT" }
  ],
  "foreignKeys": [
    {
      "constraintName": "fk_customer",
      "columns": ["customer_id"],
      "targetDatabase": "sales",
      "targetTable": "customer",
      "targetColumns": ["id"],
      "evidence": "FOREIGN_KEY"
    }
  ],
  "coverage": {
    "uniqueKeys": { "status": "COMPLETE", "appliedLimit": 32 },
    "foreignKeys": { "status": "COMPLETE", "appliedLimit": 32 },
    "indexes": { "status": "COMPLETE", "appliedLimit": 64 }
  },
  "appliedLimits": {
    "uniqueKeys": 32,
    "foreignKeys": 32,
    "indexes": 64
  }
}
```

New structures omit raw DDL, default/check expressions, statistics, and vendor storage details. Web `/api/v1/**` table-detail is unchanged.

### Relationships

```http
GET /internal/api/v1/agent/data-sources/{id}/relationships
  ?database=<namespace>&table=<table>
  &direction=BOTH|OUTBOUND|INBOUND
  &pageSize=<1..provider-max>&pageToken=<opaque>
```

`database` and `table` are required. `direction` defaults to `BOTH`. `OUTBOUND` uses imported keys; `INBOUND` uses exported keys. Composite foreign keys are one ordered edge (equal-length source/target column arrays) and are never split across pages. Cursors bind datasource, product, namespace, table, direction, sort position, and expiry; tampering returns `400 VALIDATION_FAILED`.

```json
{
  "items": [
    {
      "sourceDatabase": "sales",
      "sourceTable": "orders",
      "sourceColumns": ["customer_id"],
      "targetDatabase": "sales",
      "targetTable": "customer",
      "targetColumns": ["id"],
      "direction": "OUTBOUND",
      "constraintName": "fk_customer",
      "evidence": "FOREIGN_KEY",
      "cardinality": "MANY_TO_ONE"
    }
  ],
  "nextPageToken": null,
  "coverage": "COMPLETE",
  "appliedLimit": 50
}
```

The operation does not recursively inspect adjacent tables. GBase 8a currently reports `CAPABILITY_NOT_SUPPORTED` until equivalent real-driver tests pass. Do not treat MySQL-family fallback as complete relationship metadata.

Compatible consumer change: `bddf-agentscope` / `add-schema-relationships-tools`.

## Error codes

| Code | HTTP | When |
| --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Missing/invalid Bearer, or missing internal caller key when required |
| `DATA_SOURCE_NOT_FOUND` | 404 | Datasource missing or in another product |
| `VALIDATION_FAILED` | 400 | Bounds, unknown fields, malformed JSON, conflicting `database`/`schema` |
| `MULTI_STATEMENT_NOT_SUPPORTED` | 400 | More than one statement |
| `READ_ONLY_VIOLATION` | 422 | DML/DDL/other at query time |
| `EXPLAIN_STATEMENT_NOT_SUPPORTED` | 422 | Not a supported SELECT, or SHOW/EXPLAIN as Agent SQL |
| `EXPLAIN_ANALYZE_NOT_ALLOWED` | 422 | ANALYZE submitted to validate/explain/query or read-only Web execution |
| `SQL_EXECUTION_TIMEOUT` | 504 | Target timeout |
| `EXECUTION_ID_CONFLICT` | 409 | Reused execution ID |
| `EXECUTION_LIMIT_EXCEEDED` | 429 | Concurrency quota |
| `SQL_EXECUTION_FAILED` | 422 | Target failure; Agent message is redacted |
| `CAPABILITY_NOT_SUPPORTED` | 422 | Engine cannot supply relationship metadata |

## Black-box seed flow (Agent repository)

This is a test profile, not a production authentication bypass. The packaged service still calls the Auth context URL.

1. Start Auth stub (WireMock) that maps a deterministic Bearer `token-a` to a single product, using the same contract as `BackendIntegrationTest`.
2. Start metadata MySQL (Flyway) and a target MySQL or PostgreSQL allowed by `sql-editor.network.allowed-cidrs`.
3. Start the packaged `zorth-web-sql-service` with:
   - `SQL_EDITOR_AUTH_CONTEXT_URL` pointing at the stub
   - `SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY` matching the stub
   - metadata JDBC URL and credential keys
4. Create a datasource through the public management API, not by inserting rows:

```http
POST /api/v1/data-sources
Authorization: Bearer token-a
Content-Type: application/json
```

Use the returned `id` as `{id}` for every Agent call.

5. Exercise Agent endpoints with the same Bearer and an `X-Request-Id` UUID. For query/explain generate a fresh `executionId` UUID.
6. Pin the provider version (Git SHA or image tag) in the Agent repository black-box profile. Do not compile against this service's Java classes.

Provider pin used by `bddf-agentscope` `-Pdatasource-blackbox`:

| Field | Value |
| --- | --- |
| Provider change | `add-agent-schema-relationships-api` |
| Git SHA | `9d420ea947bf9940954417e511d3aab009115631` (image built from this working tree) |
| Packaged image | `zorth-web-sql-service:add-agent-schema-relationships-api` |

Packaged image:

```bash
cd service
mvn -DskipTests clean package
docker build -t zorth-web-sql-service:<tag> .
```

The image listens on 8080. Target MySQL 8 with `sslMode=DISABLED` uses `allowPublicKeyRetrieval=true` so caching_sha2_password works from a container IP, not only localhost.

Compatible consumer change: `bddf-agentscope` / `add-schema-relationships-tools` (replaces the previous `align-database-tools-sql-editor-contract` consumer once both contract suites pass).
