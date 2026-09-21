## 1. Canonical metadata contracts and limits

- [x] 1.1 Add engine-neutral models for unique keys, foreign keys, indexes, relationship edges, direction/evidence, cardinality and `COMPLETE`/`TRUNCATED`/`UNAVAILABLE` coverage; validate ordered nonempty/equal composite column pairs
- [x] 1.2 Add Agent API configuration for maximum unique keys, foreign keys, indexes and relationships/page with positive bounded defaults and startup validation
- [x] 1.3 Extend `EngineSupport` and reusable metadata service contracts for bounded table constraints plus imported/exported one-hop relationships, with explicit capability-unavailable behavior and no JDBC types outside engine adapters
- [x] 1.4 Extend/sign relationship cursor payloads so datasource/product query scope, namespace, table, direction, sort position and expiry are bound and tampering fails safely

## 2. MySQL and PostgreSQL engine metadata

- [x] 2.1 Implement MySQL extraction/grouping for ordered composite primary/unique/index/imported/exported key metadata using safe catalog APIs under hard limits
- [x] 2.2 Implement PostgreSQL extraction/grouping with the same canonical semantics while treating schema as NAMESPACE and preserving the pinned database connection model
- [x] 2.3 Distinguish `UNIQUE_CONSTRAINT` from `UNIQUE_INDEX` only where the driver/catalog proves it; verify weaker evidence is reported otherwise and ordinary indexes never create relationships
- [x] 2.4 Add focused engine unit tests for composite grouping, stable sorting, empty COMPLETE, TRUNCATED limits, UNAVAILABLE capability and malformed driver rows
- [x] 2.5 Make GBase 8a return explicit capability-not-supported until its official driver passes equivalent relationship tests; verify no MYSQL-family fallback claims COMPLETE metadata

## 3. Reusable metadata service

- [x] 3.1 Extend the internal table metadata aggregate with bounded unique keys, outbound foreign keys, cropped indexes and per-section coverage without changing existing Web DTOs
- [x] 3.2 Implement one-table, one-hop relationship lookup with BOTH/OUTBOUND/INBOUND filtering, authorization before connection borrowing, grouping before pagination and no recursive traversal
- [x] 3.3 Add service tests for product isolation, input validation, provider caps, stable cursor continuation, composite edge atomicity and deterministic cleanup of metadata connections
- [x] 3.4 Add safe error/observability handling that records only request/datasource/operation/count/duration/status and omits metadata payloads, names, JDBC URLs, credentials and vendor errors

## 4. Versioned Agent API

- [x] 4.1 Extend Agent table-detail DTO/mapping with uniqueKeys, outbound foreignKeys, cropped indexes, coverage and applied limits while omitting raw DDL/default/check/statistics/vendor fields
- [x] 4.2 Add `GET /internal/api/v1/agent/data-sources/{id}/relationships` with strict database/table/direction/pageSize/pageToken validation and stable Agent DTOs
- [x] 4.3 Add MVC contract tests for enriched table detail, relationship directions, opaque paging, missing/invalid fields, unsupported capability, additive internal caller key and cross-product not-found behavior
- [x] 4.4 Update OpenAPI/`docs/agent-database-api.md` with endpoint parameters, response examples, evidence/coverage semantics, hard limits and the companion consumer change `add-schema-relationships-tools`

## 5. Real-database verification and release

- [x] 5.1 Add MySQL Testcontainers fixtures with single/composite foreign keys, unique constraint/index distinctions, inbound/outbound edges, ordinary lookalike indexes and more-than-page-size relationships
- [x] 5.2 Add equivalent PostgreSQL Testcontainers fixtures across schemas and verify NAMESPACE application, table detail, relationship paging and safe unsupported/empty semantics
- [x] 5.3 Fix integration-test database isolation and response charset handling so the complete Docker-enabled Maven suite is deterministic rather than order-dependent
- [x] 5.4 Run focused tests and the full Docker-enabled `env 'api.version=1.44' mvn test`; verify MySQL/PostgreSQL relationship suites and all existing Web/Agent contracts pass
- [x] 5.5 Build and tag a provider image, record its Git SHA, and run the companion `bddf-agentscope` datasource black-box profile against that exact image
- [x] 5.6 Run `openspec validate add-agent-schema-relationships-api --type change --strict` and verify the change is apply-ready
