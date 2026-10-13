# SQL optimizer backend local fixture

Run commands from the backend worktree's `service/`. This harness starts its own MySQL container, local auth/Agent HTTP fixture and real Spring API/Worker. Auth resolves a generated Bearer and an internal key; the Agent stub propagates that Bearer and the paired callback key to the real service's validate and plan-only EXPLAIN APIs. It never invokes `/query` or ANALYZE. The stub is not a real model and gives no optimization quality guarantee.

```bash
env JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home mvn -Dapi.version=1.44 verify
python3 tools/optimizer_local_fixture.py
```

Requires Python 3.9+, JDK 17, Docker and an available `mysql:8.0` image (Docker can pull it). Java compiles for Java 8. Use `--java-home`, `--api-port` or `--fixture-port` to change local paths/ports. Defaults: API `http://127.0.0.1:18080`, fixture `127.0.0.1:18090`; MySQL gets a loopback-only dynamic port. The harness does not touch any pre-existing process/container or shared schema. The metadata database doubles as the fixture target, so EXPLAIN uses the isolated real database.

On startup it creates three actual tasks: successful authenticated plan callback, successful report with evidence limitations, and upstream failure. Stdout contains only base URL, private client-file path, result-file path, task IDs/statuses and the harness PID. Credentials and encryption keys are random per run; no credentials belong in repository docs or browser DTOs. The generated auth session expires in one hour and task credential custody is capped at ten minutes. Temporary files are owner-readable. Ctrl-C or SIGTERM to the printed fixture PID stops only this harness's Java process and container. `--smoke-only` validates and shuts down automatically.

Use the printed private client file without exposing its token in a terminal command:

```bash
python3 tools/optimizer_local_fixture.py --client /private/tmp/<printed-runtime>/client.json --path /api/v1/session
python3 tools/optimizer_local_fixture.py --client /private/tmp/<printed-runtime>/client.json --path /api/v1/sql-optimization/tasks
python3 tools/optimizer_local_fixture.py --client /private/tmp/<printed-runtime>/client.json --path /api/v1/sql-optimization/tasks/<task-id>
python3 tools/optimizer_local_fixture.py --client /private/tmp/<printed-runtime>/client.json --method POST --path /api/v1/sql-optimization/tasks/<terminal-task-id>/retry
```

A create request can use `--method POST --path /api/v1/sql-optimization/tasks --body-file <json-file>` with only name/datasourceId/database/sql. The helper generates an idempotency UUID for each new submission. Direct clients must reuse a key for transport retries. The runtime `runtime.json` includes the visible datasource and namespace; `tasks.json` contains exact API results and `last-callback.json` contains validate/EXPLAIN evidence, never tokens. Treat these as local fixture artifacts, not production acceptance.

## Feature configuration

`sql-editor.optimization` is disabled by default. Enabling requires `enabled=true`, `integration-ready=true`, both explicit `allowed-users` and `allowed-products`, a configured independent 32-byte Base64 credential key, and a >=32-byte Base64 cursor key. Allowlist strings are comma separated via the corresponding `SQL_EDITOR_OPTIMIZATION_ALLOWED_USERS/PRODUCTS` variables. There are no wildcards. Startup fails if gating, keys or budgets are invalid. The vault key cannot reuse the configured current datasource-password key. Never assert integration readiness in production until actual identity/callback/custody acceptance.

The supplied harness supplies runtime configuration for its own fixture environment. For deployment, configure `SQL_EDITOR_OPTIMIZATION_CREDENTIAL_KEY_V1`, `SQL_EDITOR_OPTIMIZATION_CURSOR_KEY`, actual `AGENT_BASE_URL`, allowlists and budget/retention values through protected service configuration. Adding historical versions uses `sql-editor.optimization.credential-keys.<version>`; retain them until every outstanding task encrypted with that key reaches a terminal state. `current-key-version` selects new encryption. Unavailable/deleted keys fail existing tasks safely. No token renewals or delegation are implemented.

Defaults: 2 workers, 2s scheduling, unfinished quota 10 per user/product and 100 global, token TTL 600s, connect 3s / whole call 135s / hard attempt 150s / lease 180s / heartbeat 15s, response cap 1MiB including envelope, retention 90 days. Source permissions are checked on reads and before worker calls. Heartbeat never changes task version. Maintenance remains active when the capability is disabled, so existing credentials still expire and get removed (the explicit scheduler-enabled test switch stops both scheduling and maintenance). Recovery fails expired running attempts; it never automatically resends a POST. The HTTP byte cap includes the envelope to bound parsing memory; oversized upstream output fails rather than being truncated.

Meters: `sql.optimization.queued`, `.running`, `.oldest.wait.seconds`, `.completed` (safe outcome code), `.duration`, `.limitations`, `.late.results`, `.heartbeat.errors`, `.scheduler.errors`. SQL, tokens and reports are excluded from labels and routine logs. Default lifecycle errors are intentionally safe generic messages.

## Acceptance boundary

Testcontainers/WireMock tests require actual Docker/socket permissions; `OptimizationIntegrationTest` fails if Docker is unavailable rather than silently skipping. Existing baseline container tests keep their established behavior; delivery must inspect aggregate skips. `mvn verify` also checks Java 8 APIs with Animal Sniffer.

Production CurrentUserPort, verified end-user Bearer propagation, data-source integration enablement, callback key pairing, custody policy/key source/actual expiry and Agent session retention remain external deployment work. Real Agent SELECT/JOIN quality and frontend integration with Mock disabled remain separately pending.
