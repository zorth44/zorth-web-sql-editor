# backend-sql-optimization Specification

## Purpose

Define the persistent asynchronous SQL optimization backend: the authenticated task APIs, the database-backed Worker and its recovery behavior, short-lived credential custody, the strict structured report boundary, and maintenance/observability. Optimization is delivered but disabled by default and requires an explicit allowlist plus verified integration.

## Requirements
### Requirement: Durable contract APIs
The service SHALL implement shared contract v1 create, list, detail and retry APIs with identity/product isolation, live datasource visibility, signed cursors, transactionally serialized quotas and idempotency before quota consumption.

#### Scenario: Idempotent submission
- **WHEN** an authorized user repeats the same key and normalized input
- **THEN** the service SHALL return the original task and current status with 202 and Location even at quota, while different input returns 409.

#### Scenario: Read isolation
- **WHEN** a task is missing, owned by another user/product or its datasource is no longer visible
- **THEN** detail and retry SHALL return 404 and list SHALL exclude it.

### Requirement: Worker lifecycle
Workers SHALL claim persisted QUEUED tasks only after obtaining local capacity, create a unique attempt/session, invoke the fixed SQL Lite endpoint outside metadata transactions and condition terminal writes on the current RUNNING attempt.

#### Scenario: Restart and stale response
- **WHEN** queue work survives restart or a running attempt lease expires
- **THEN** queued work SHALL remain claimable, expired running work SHALL fail WORKER_LOST and late responses SHALL NOT replace a terminal state.

### Requirement: Credential custody
The service SHALL store only AES-GCM encrypted short-lived verified tokens with independent purpose/versioned keys and task-bound AAD, require full attempt headroom, reauthorize remotely before calls, verify unchanged identity/product and remove ciphertext at terminal state/expiry.

#### Scenario: Revoked or changed identity
- **WHEN** reauthorization rejects a token or returns a different user/product
- **THEN** the task SHALL fail safely without calling Agent.

### Requirement: Structured report boundary
The service SHALL accept only bounded structuredOutput with contract fields, valid enums, unique IDs, valid issue references and category evidence/severity, derive summary and retain immutable schema version 1 snapshots without evaluating evidence truth.

#### Scenario: Invalid upstream output
- **WHEN** upstream returns prose, malformed output, bad references or excessive response bytes
- **THEN** the task SHALL fail REPORT_INVALID or REPORT_TOO_LARGE without fallback or truncation.

### Requirement: Maintenance and observability
The service SHALL recover expired leases, expire queued credentials, delete terminal credentials and retire historical tasks/attempts after configured retention; logs and metric labels SHALL exclude SQL, reports and tokens.

#### Scenario: Credential expires in queue
- **WHEN** a queued credential can no longer cover an attempt
- **THEN** maintenance SHALL fail it CREDENTIAL_EXPIRED and remove ciphertext.

