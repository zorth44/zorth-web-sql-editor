## Why

The Agent database API exposes columns, primary keys, and indexes but does not provide canonical unique-key/foreign-key completeness or a bounded way to discover tables related to a known table. The companion Agent Platform change needs authoritative provider metadata so models can build multi-table joins without parsing DDL or guessing from names and indexes.

## What Changes

- Enrich the Agent `table-detail` response with bounded unique keys, outbound foreign keys, cropped indexes, and explicit `COMPLETE` / `TRUNCATED` / `UNAVAILABLE` coverage.
- Add an authenticated, cursor-paged `GET /internal/api/v1/agent/data-sources/{id}/relationships` endpoint for a known namespace/table and `BOTH` / `OUTBOUND` / `INBOUND` one-hop directions.
- Extend engine/catalog metadata capabilities to return canonical ordered composite key pairs, uniqueness evidence, and stable relationship ordering without exposing raw JDBC objects.
- Require MySQL and PostgreSQL real-database coverage; GBase 8a must either pass equivalent tests or report capability-not-supported.
- Preserve existing Web SQL Editor `/api/v1/**` behavior and Agent datasource authorization, safe errors, limits, and observability.

## Capabilities

### New Capabilities

None. Constraint and relationship discovery extend existing metadata and Agent API capabilities.

### Modified Capabilities

- `backend-agent-database-api`: Add enriched table-detail fields and the bounded relationship endpoint consumed by Agent Platform.
- `backend-engine-spi`: Add engine-neutral constraint, index, relationship, coverage, and capability contracts implemented by database engines.
- `backend-sql-editor`: Extend reusable metadata services with bounded imported/exported relationship discovery while retaining product isolation.

## Impact

- Service packages: `agentapi`, `metadata`, engine SPI plus MySQL/PostgreSQL/GBase adapters, configuration, cursor handling, tests, and API documentation.
- Remote API: additive fields on Agent table detail and one additive versioned Agent endpoint; no existing request or Web API is removed or renamed.
- Companion consumer: `bddf-agentscope` change `add-schema-relationships-tools` adds platform DTOs, Gateway support, enriched `get_table_schema`, and `get_table_relationships`.
- Security/performance: one-table/one-hop scope, hard section/page limits, no recursive graph traversal, no DDL parsing, and no credentials/default expressions/raw vendor metadata in Agent responses.
