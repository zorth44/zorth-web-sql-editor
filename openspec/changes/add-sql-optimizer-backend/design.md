## Context
Spring Boot 2.7 MVC/MyBatis/Flyway/MySQL, Java 8 API compatibility. Shared contract v1 and backend plan remain the scope baseline.

## Goals / Non-Goals
Durable single-SQL tasks and locally verified API/worker/credential/report lifecycle. No execution, automatic POST retries, production integration or frontend changes.

## Decisions
- V8 task/attempt/credential tables; a seeded quota mutex row serializes creation counts and idempotency checks across instances. Short TransactionTemplate units ensure remote HTTP holds no metadata transaction.
- Atomic QUEUED claim and current-attempt/status guarded terminal writes; heartbeats only update lease, never task version. Expired leases fail WORKER_LOST without requeue.
- Independent bounded worker and maintenance/heartbeat executors; no sqlExecutionExecutor reuse. Local slot precedes database claim.
- AES-GCM credential vault with task-bound purpose AAD and key version. No built-in key; actual token expiry and TTL are capped, with full-attempt headroom required.
- Fixed Agent path and explicitly assembled coordinates; bounded response bytes and whole-call timeout without transparent POST retries. Only structuredOutput accepted.
- Default-disabled capability with explicit user/product allowlists and integration-ready assertion. Worker uses uncached AuthClient and explicit AuthContext.
- Signed cursor binds user/product/filter/page size and stable timestamp/id order. Real-time datasource visibility filters reads.

## Risks / Trade-offs
- Remote HTTP cannot be exactly-once → no automatic retry, new task for user retry.
- Model evidence may be factually wrong → structural validation only, preserve limitations.
- Revocation after claim → callback authentication remains required; completed reports retain read authorization.
- Global creation mutex limits throughput → acceptable initial global unfinished quota 100; future counters may replace it.

## Migration Plan
Apply additive V8 while feature disabled. Configure independent vault/cursor keys and verified integration in authorized environments before enabling allowlists. Roll back by disabling feature; retain historical tables.

## Open Questions
Production CurrentUserPort, credential custody policy approval, real Agent/callback keys and retention must be resolved before rollout. Local WireMock/Testcontainers does not resolve these.
