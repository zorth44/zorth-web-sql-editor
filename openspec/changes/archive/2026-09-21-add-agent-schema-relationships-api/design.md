## Context

SQL Editor owns datasource authorization, JDBC connections, engine-specific metadata, stable cursor paging, and the versioned Agent database API. Its Agent `table-detail` currently maps normalized columns, primary keys, indexes, optional DDL, and statistics. It does not distinguish unique constraints from other indexes, expose outbound foreign keys with ordered column pairs, or provide reverse/inbound relationship lookup.

The companion consumer change `bddf-agentscope/add-schema-relationships-tools` will enrich `get_table_schema` and add `get_table_relationships`. This provider change must supply authoritative structured facts; the consumer must not parse DDL, query JDBC, or infer joins from naming conventions.

## Goals / Non-Goals

**Goals:**

- Add engine-neutral models and SPI operations for bounded unique keys, foreign keys, indexes, and one-hop relationships.
- Enrich only the versioned Agent table-detail DTO with model-safe structured metadata and explicit completeness.
- Add a product-authorized, stable cursor-paged Agent relationship endpoint for one known table.
- Preserve ordered composite keys and make unsupported/truncated metadata distinguishable from confirmed absence.
- Verify MySQL and PostgreSQL behavior against real databases.

**Non-Goals:**

- Changing existing Web SQL Editor `/api/v1/**` response contracts or UI behavior.
- Returning raw JDBC objects, raw DDL/default/check expressions, statistics, storage parameters, or credentials through the new Agent fields.
- Guessing relationships from matching names, types, comments, or ordinary indexes.
- Recursive graph traversal, arbitrary path search, a semantic catalog, or manual relationship authoring.
- SQL generation, index recommendations, or changes to query/explain execution.

## Decisions

### Extend engine/catalog metadata behind a narrow reusable service

`EngineSupport` will expose canonical metadata needed by the shared metadata service: table constraints/indexes and one-hop imported/exported relationships. Engine implementations own JDBC metadata quirks; Agent controllers only call the shared service and map canonical models.

This follows the existing engine dispatch boundary. Putting `DatabaseMetaData` calls in `agentapi` was rejected because it would duplicate connection, authorization, dialect, and resource-lifecycle behavior.

### Keep Web table-detail stable and enrich the Agent adapter

The existing Web table-detail contract remains unchanged. The Agent mapper receives a canonical metadata aggregate and emits cropped `uniqueKeys`, outbound `foreignKeys`, `indexes`, and per-section coverage. Existing Agent column/primary-key fields remain additive-compatible.

The Agent response omits DDL, default/check expressions, statistics, and vendor storage details from the new model-facing structures even when the reusable Web service can obtain those values.

### Define one-hop relationships relative to the requested table

The additive endpoint is:

```text
GET /internal/api/v1/agent/data-sources/{id}/relationships
    ?database=<namespace>&table=<table>
    &direction=BOTH|OUTBOUND|INBOUND
    &pageSize=<1..provider-max>&pageToken=<opaque>
```

An edge always has canonical source and target coordinates. `direction` describes the edge relative to the requested table. `OUTBOUND` uses imported keys; `INBOUND` uses exported keys; `BOTH` merges them under a stable provider sort before paging. The operation never recursively loads adjacent tables.

### Preserve composite relationships as atomic ordered edges

Rows returned by JDBC metadata are grouped by constraint identity and ordered by `KEY_SEQ`. Source and target column arrays must be nonempty, equal in length, and kept together as one edge. Sorting and pagination occur after grouping so a composite foreign key cannot be split across pages.

Where JDBC omits a constraint name, the provider creates only an internal stable grouping key from safe coordinates and sequence; it does not expose a fabricated business constraint name.

### Separate relationship evidence from index evidence

Initial relationship evidence is `FOREIGN_KEY`. Optional cardinality is emitted only when target/source uniqueness is proven by primary-key or unique constraint/index metadata. Ordinary indexes never create relationships. Unique metadata records evidence as `UNIQUE_CONSTRAINT` where the engine can prove a constraint and `UNIQUE_INDEX` otherwise.

This avoids overstating portable JDBC metadata. Engines that cannot distinguish constraint and index report the weaker truthful evidence.

### Make completeness explicit and bounded

Every enhanced section and relationship page uses coverage `COMPLETE`, `TRUNCATED`, or `UNAVAILABLE`, plus `appliedLimit` when bounded. `COMPLETE` empty is confirmed absence. Unsupported drivers/engines use `UNAVAILABLE` or stable `CAPABILITY_NOT_SUPPORTED`, never `COMPLETE` empty.

Provider configuration adds hard caps for unique keys, foreign keys, indexes, and relationship page size. A client may request fewer relationships but cannot raise the cap. Opaque relationship cursors bind datasource, product-visible query scope, namespace, table, direction, sort position, and expiry/signature using existing cursor infrastructure.

### Engine rollout contract

MySQL and PostgreSQL are required to pass MVC and Testcontainers integration scenarios. GBase 8a may implement the same SPI only if its official driver returns reliable metadata; otherwise its adapter reports capability-not-supported. No generic MySQL-family fallback may claim completeness for GBase without tests.

## Risks / Trade-offs

- [JDBC metadata differs by engine/driver version] → Normalize inside each engine adapter and gate required engines with container tests.
- [Reverse key lookup is expensive] → Restrict scope to one visible table, one hop, hard limits, stable paging, and short-lived metadata connection use.
- [Unique index is mistaken for a declared constraint] → Preserve `UNIQUE_INDEX` as weaker evidence instead of relabeling it.
- [Composite key rows paginate incorrectly] → Group and validate full constraints before sorting/paging atomic edges.
- [Empty results hide unsupported metadata] → Require explicit coverage and reject contradictory provider states.
- [Agent responses become large] → Apply per-section caps and omit DDL/default/check/statistics/vendor details.

## Migration Plan

1. Add canonical models, limits, and SPI contracts with unavailable defaults so engines remain compile-safe.
2. Implement and test MySQL/PostgreSQL table constraints and imported/exported keys.
3. Enrich Agent table detail and deploy the additive relationships endpoint while keeping Web APIs unchanged.
4. Publish a pinned provider image/commit and run the companion Agent Platform black-box suite.
5. Enable the consumer Tool only after both repositories' contract suites pass.

Rollback removes/blocks the relationships endpoint or rolls back the service image. Additive response fields may remain ignored by older consumers; no database migration is required.

## Open Questions

- Can supported driver versions reliably distinguish unique constraints from unique indexes, or should all portable output initially use `UNIQUE_INDEX` except primary keys?
- Should foreign-key update/delete rules be included now, or deferred until the consumer has a concrete use case?
- What production defaults should be selected for per-table sections and relationship pages after measuring representative schemas?
