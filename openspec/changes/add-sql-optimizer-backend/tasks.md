## 1. BE-01 Contract and Agent boundary
- [x] 1.1 Implement Java 8 task/report DTOs and strict structured report validator.
- [x] 1.2 Implement fixed SQL Lite HTTP client with whole-call/byte bounds and WireMock error fixtures.

## 2. BE-02 Database and API
- [ ] 2.1 Add V8 task/attempt/credential persistence and transactional queue repository.
- [ ] 2.2 Implement authenticated create/list/detail/retry, validation, idempotency, serialized quotas and identity-bound cursors.

## 3. BE-04 Security (dependency of Worker)
- [ ] 3.1 Add independent encrypted short-lived credential vault and capability allowlists/default-off integration gate.
- [ ] 3.2 Reauthorize through uncached AuthClient, protect identity/datasource and verify callback key never replaces user authentication.

## 4. BE-03 Worker and recovery
- [ ] 4.1 Add independent bounded slots, atomic claim/attempt creation, heartbeat and guarded terminal transitions without HTTP transactions.
- [ ] 4.2 Recover persisted queued work, fail expired leases and reject late results without automatic POST retries.

## 5. BE-05 Report and maintenance
- [ ] 5.1 Persist schema-1 report/derived summaries and safe classified failures; remove credentials on terminal states.
- [ ] 5.2 Maintain queued expiry/retention and safe aggregate observability.

## 6. BE-06 Local acceptance
- [ ] 6.1 Verify real MySQL API isolation/idempotency/concurrent quotas/cursors and report/HTTP fixtures.
- [ ] 6.2 Verify competing workers/recovery/lease/late results/transaction boundaries and credential expiry/revocation/change/decryption.
- [ ] 6.3 Run JDK17 mvn test and mvn verify with actual container execution and Java8 compatibility, inspect changes and stage commits.

## 7. BE-07 Local handoff
- [ ] 7.1 Provide runnable local API/Worker/auth/Agent fixtures, sample task results and contract evidence, update backend plan/execution.

## 8. External acceptance (outside local Goal)
- [ ] 8.1 Confirm production CurrentUserPort, verified Bearer and real Agent callback configuration.
- [ ] 8.2 Approve production credential custody/expiry/key sources and Agent state retention.
- [ ] 8.3 Verify real SELECT/JOIN/evidence gaps with actual Agent and frontend with Mock disabled.
