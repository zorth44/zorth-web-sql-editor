## MODIFIED Requirements

### Requirement: Agent API exposes bounded datasource and metadata discovery
The API SHALL expose datasource list/detail, namespace listing, table list/search, enriched table detail, cross-table column search, and one-hop table relationship discovery for visible datasources. `GET /internal/api/v1/agent/data-sources/{id}/databases` SHALL reuse Web NAMESPACE semantics. `GET .../{id}/relationships` SHALL require database and table, accept direction `BOTH`/`OUTBOUND`/`INBOUND`, pageSize and opaque pageToken, and return stable canonical source/target edges. Responses SHALL use hard limits and Agent DTOs, SHALL represent JDBC types and evidence by canonical names, and MUST NOT include passwords, encrypted credentials, JDBC URLs, raw DDL/default/check expressions, statistics, vendor objects, or connection properties not required by the Tool contract.

#### Scenario: Datasources and tables are discovered
- **WHEN** an authorized Agent lists datasources and searches tables with bounded page sizes
- **THEN** stable summaries and cursor tokens are returned without credential material

#### Scenario: Namespaces are listed
- **WHEN** an authorized Agent calls the databases endpoint for a visible datasource
- **THEN** the service returns a cursor page of NAMESPACE items with engine-specific system-namespace hiding

#### Scenario: Enriched table detail is requested
- **WHEN** an authorized Agent requests a visible table
- **THEN** the response contains normalized columns, primary keys, bounded unique keys, outbound foreign keys, cropped indexes and explicit per-section coverage without raw DDL/default/check/statistics fields

#### Scenario: Columns are searched
- **WHEN** an authorized Agent searches a column keyword within a namespace
- **THEN** the engine/catalog layer returns a bounded page without controller-level table-detail fan-out

#### Scenario: One-hop relationships are requested
- **WHEN** an authorized Agent requests BOTH direction relationships for one visible table
- **THEN** the service returns one stable cursor page of imported/exported canonical edges and does not recursively inspect adjacent tables

#### Scenario: Relationship capability is unsupported
- **WHEN** the selected engine cannot reliably provide relationship metadata
- **THEN** the API returns stable capability-not-supported or UNAVAILABLE coverage rather than a fabricated COMPLETE empty result

### Requirement: Provider owns executable compatibility tests
The service SHALL provide MVC contract tests and Testcontainers integration tests covering every Agent endpoint, authentication, product isolation, MySQL behavior, supported PostgreSQL behavior, read-only rejection, pagination, canonical data types, limits, errors, enriched table detail, and one-hop relationships. Relationship integration tests SHALL cover ordered composite foreign keys, inbound/outbound direction, uniqueness evidence, confirmed empty, truncation and unsupported capability. The service SHALL document a deterministic black-box startup/seed flow usable by consumer repositories without an LLM or authentication bypass.

#### Scenario: Provider integration suite runs
- **WHEN** Docker is available and the Agent API integration suite starts Auth WireMock, metadata storage, and MySQL/PostgreSQL targets
- **THEN** it creates datasources through the public management API and verifies enriched table detail and relationship APIs against real JDBC metadata

#### Scenario: Unsupported GBase relationship metadata
- **WHEN** GBase 8a has not passed equivalent real-driver relationship scenarios
- **THEN** its Agent relationship operation reports capability-not-supported and does not reuse unverified MySQL-family assumptions

#### Scenario: Consumer provisions a black-box datasource
- **WHEN** a pinned packaged service is started for the Agent repository
- **THEN** the consumer can seed composite key fixtures and verify all declared metadata capability scenarios

## ADDED Requirements

### Requirement: Agent relationship envelopes preserve evidence and completeness
Each relationship SHALL contain source/target database, table, ordered columns, direction relative to the requested table, optional real constraintName, stable evidence, and optional evidence-backed cardinality. Source and target column arrays MUST be nonempty and equal in length. Coverage SHALL be `COMPLETE`, `TRUNCATED`, or `UNAVAILABLE`; pagination/truncation state MUST be internally consistent.

#### Scenario: Composite outbound foreign key is returned
- **WHEN** a visible table contains a two-column imported foreign key
- **THEN** the API returns one OUTBOUND edge with both ordered source/target pairs and `evidence=FOREIGN_KEY`

#### Scenario: Ordinary index resembles a relationship
- **WHEN** same-named indexed columns exist without a declared reference
- **THEN** the API returns no relationship for that coincidence

#### Scenario: Metadata is truncated
- **WHEN** matches exceed the effective page or section limit
- **THEN** the API returns only the bounded data, `coverage=TRUNCATED`, appliedLimit and an opaque nextPageToken where paging applies
