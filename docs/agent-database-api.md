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
| GET | `/internal/api/v1/agent/data-sources/{id}/tables` | Table search (`database`, `keyword`, `types`, `pageSize`, `pageToken`) |
| GET | `/internal/api/v1/agent/data-sources/{id}/columns` | Cross-table column search (`database`, `keyword` required) |
| GET | `/internal/api/v1/agent/data-sources/{id}/table-detail` | Columns, keys, indexes, optional `ddl`/`stats` |
| POST | `/internal/api/v1/agent/data-sources/{id}/sql/validate` | Non-executing safety facts |
| POST | `/internal/api/v1/agent/data-sources/{id}/sql/explain` | Normalized plan-only explain |
| POST | `/internal/api/v1/agent/data-sources/{id}/sql/query` | Bounded read-only SELECT |

Unknown JSON fields return `400 VALIDATION_FAILED` / `UNKNOWN_FIELD`. Query and explain bodies must not include `readOnly` or `source`. Query always executes with `readOnly=true` and history `source=AI_AGENT`. Explain history is `AI_AGENT_EXPLAIN`.

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

## Error codes

| Code | HTTP | When |
| --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Missing/invalid Bearer, or missing internal caller key when required |
| `DATA_SOURCE_NOT_FOUND` | 404 | Datasource missing or in another product |
| `VALIDATION_FAILED` | 400 | Bounds, unknown fields, malformed JSON |
| `MULTI_STATEMENT_NOT_SUPPORTED` | 400 | More than one statement |
| `READ_ONLY_VIOLATION` | 422 | DML/DDL/other at query time |
| `EXPLAIN_STATEMENT_NOT_SUPPORTED` | 422 | Not a supported SELECT, or SHOW/EXPLAIN as Agent SQL |
| `EXPLAIN_ANALYZE_NOT_ALLOWED` | 422 | ANALYZE submitted to validate/explain/query or read-only Web execution |
| `SQL_EXECUTION_TIMEOUT` | 504 | Target timeout |
| `EXECUTION_ID_CONFLICT` | 409 | Reused execution ID |
| `EXECUTION_LIMIT_EXCEEDED` | 429 | Concurrency quota |
| `SQL_EXECUTION_FAILED` | 422 | Target failure; Agent message is redacted |

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

Packaged image:

```bash
cd service
mvn -DskipTests clean package
docker build -t zorth-web-sql-service:<tag> .
```

The image listens on 8080. Target MySQL 8 with `sslMode=DISABLED` uses `allowPublicKeyRetrieval=true` so caching_sha2_password works from a container IP, not only localhost.

Compatible consumer change: `bddf-agentscope` / `integrate-web-sql-database-service`.

Local black-box (2026-09-20):

| Side | Version |
| --- | --- |
| Provider image | `zorth-web-sql-service:add-agent-database-tool-api` |
| Provider commit | `3502c10add31146cc41cca86da06a10fcf1611b8` (working tree: `add-agent-database-tool-api`) |
| Consumer commit | `a47b43c59e651f7e2e3d19d849fa83fcd7ec5812` (working tree: `integrate-web-sql-database-service`) |
| Result | `mvn test -Pdatasource-blackbox` — 4 tests passed |
