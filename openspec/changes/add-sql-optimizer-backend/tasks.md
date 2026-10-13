## 1. BE-01 Contract and Agent boundary
- [x] 1.1 Implement Java 8 task/report DTOs and strict structured report validator.
- [x] 1.2 Implement fixed SQL Lite HTTP client with whole-call/byte bounds and WireMock error fixtures.

## 2. BE-02 Database and API
- [x] 2.1 Add V8 task/attempt/credential persistence and transactional queue repository.
- [x] 2.2 Implement authenticated create/list/detail/retry, validation, idempotency, serialized quotas and identity-bound cursors.

## 3. BE-04 Security (dependency of Worker)
- [x] 3.1 Add independent encrypted short-lived credential vault and capability allowlists/default-off integration gate.
- [x] 3.2 Reauthorize through uncached AuthClient, protect identity/datasource and verify callback key never replaces user authentication.

## 4. BE-03 Worker and recovery
- [x] 4.1 Add independent bounded slots, atomic claim/attempt creation, heartbeat and guarded terminal transitions without HTTP transactions.
- [x] 4.2 Recover persisted queued work, fail expired leases and reject late results without automatic POST retries.

## 5. BE-05 Report and maintenance
- [x] 5.1 Persist schema-1 report/derived summaries and safe classified failures; remove credentials on terminal states.
- [x] 5.2 Maintain queued expiry/retention and safe aggregate observability.

## 6. BE-06 Local acceptance
- [x] 6.1 Verify real MySQL API isolation/idempotency/concurrent quotas/cursors and report/HTTP fixtures.
- [x] 6.2 Verify competing workers/recovery/lease/late results/transaction boundaries and credential expiry/revocation/change/decryption.
- [x] 6.3 Run JDK17 mvn test and mvn verify with actual container execution and Java8 compatibility, inspect changes and stage commits.

## 7. BE-07 Local handoff
- [x] 7.1 Provide runnable local API/Worker/auth/Agent fixtures, sample task results and contract evidence, update backend plan/execution.

## 8. External acceptance (outside local Goal)
- [ ] 8.1 Confirm production CurrentUserPort, verified Bearer and real Agent callback configuration.
  - [x] 2026-10-13 local run against a real Agent Platform and real MySQL: user Bearer propagated on the Agent chat call, paired callback key enforced (`X-Internal-Service-Key` alone 401, wrong/missing key 401, bearer + paired key 200) and the internal key never substitutes user identity.
  - [ ] Production CurrentUserPort adapter, its deployment location and formal end-user identity are still unverified; local acceptance used the `dev` development user header.
- [ ] 8.2 Approve production credential custody/expiry/key sources and Agent state retention.
  - [ ] Pending: short-lived token persistence approval, real `tokenExpiresAt` source, independent key sources and rotation, 90-day retention confirmation, Agent session retention policy.
- [x] 8.3 Verify real SELECT/JOIN/evidence gaps with actual Agent and frontend with Mock disabled.
  - [x] 2026-10-13 completed: real Agent + real target MySQL + Mock-disabled frontend; 7 real tasks (5 SUCCEEDED with evidence-bearing reports, 1 FAILED/`CREDENTIAL_EXPIRED`, cross-user 404 samples), evidence in `docs/sql-optimizer-live-acceptance.md`, driver in `tools/optimizer-live/`.
