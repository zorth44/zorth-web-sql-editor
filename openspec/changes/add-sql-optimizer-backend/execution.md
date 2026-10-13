# Execution recovery

Scope: BE-01–BE-07 local acceptance, external section 8 excluded from local Goal. Branch feat/sql-optimizer-backend; baseline 3a81b8c. Worktree .worktrees/sql-optimizer-backend. No other agents, push or merge.

Current phase: contract, strict report boundary and HTTP client. Planning artifacts created after reading shared contract and backend plan. Implementation tests not yet run.
Next: implement boundaries, persistence/security, API, worker, real MySQL/WireMock tests, JDK17 full gates and local handoff.
External dependencies: production Agent CurrentUserPort absent in supplied baseline; no real Agent/callback credentials or policy approval supplied. Use authorized test fixtures only; keep feature disabled by default.

Phase 1 evidence: JDK17 `mvn -q -Dtest=OptimizationBoundaryTest test` passed 3 tests, 0 skipped with real WireMock sockets (escalated after sandbox bind denial). Initial invocation from service used an incorrect nested test path and selected no tests; path corrected before acceptance. Report mutation/vault and HTTP 401/403/5xx/redirect/timeout/size/no-replay cases passed.
Next: V8 persistence, API/security and workers. Phase 1 commit recorded by git history (commit message feat(optimization): add contract and strict Agent boundary).

Phase 2: durable API/security/Worker implemented. Latest prior commit c24862b. JDK17 `mvn -q -Dapi.version=1.44 -Dtest=OptimizationBoundaryTest,OptimizationConfigurationTest,OptimizationIntegrationTest test` passed 19 tests (5 boundary + 2 configuration + 12 real MySQL/HTTP), 0 failure/error/skipped. MySQL 8.0 isolated Testcontainers actually executed; callback validate/EXPLAIN used the real random-port Spring server. Full service gates pending.
Failures resolved: V8 default collation mismatched existing datasource join; aligned table defaults and preserved binary identity/idempotency columns. Transaction inspection needs container root PROCESS permission only in tests. A two-character test Token could randomly appear in Base64 ciphertext; replaced with a full test Token while preserving the confidentiality assertion. Missing headers/noninteger pageSize now map to 400; numeric string coercion disabled for create DTO. Conservative engine-aware writable CTE/SELECT INTO guard supplements existing safety evaluator.
Evidence covers concurrent claim/quota/idempotency, stable cursor/visibility, all states/nulls/retry, token expiry/identity change/tamper/key absence/transaction rollback, retention cascade, lease/hard deadline/late results, HTTP wait with zero metadata transactions, bounded scheduler and its own heartbeats, safe report/HTTP classifications and paired callback keys unable to bypass Bearer.
Next: run full mvn test/verify, run local fixture on 18080/18090 and capture sample JSON, finalize documentation and delivery commit. External section 8 stays pending.
