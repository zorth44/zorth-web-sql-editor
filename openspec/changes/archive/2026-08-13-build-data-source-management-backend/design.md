## Context

The repository currently contains a completed Vue phase-one frontend and reviewed API documentation but no backend project. The frontend calls direct JSON Session and data-source APIs with a Bearer Token and currently uses MSW for development. The new service must remain Java 8 compatible and follow Spring Boot 2.7 conventions while the local development machine may compile it with a newer JDK using `--release 8`.

Identity remains owned by `bddf-authorization-service`. This change does not modify that service; it consumes the future `GET /internal/api/v1/auth/context` endpoint through a narrow client and uses WireMock until the external endpoint is available. The SQL service owns an internal MySQL metadata schema and connects to user-configured target MySQL instances, making product isolation, credential encryption, SSRF resistance, error sanitization, and deterministic resource cleanup first-class concerns.

The frontend investigation also found two contract defects: its JDBC property set differs from the backend allow-list, and the MSW create handler spreads a password-bearing request into response/state. Both are included as small companion corrections so the mocks continue to exercise the production contract.

## Goals / Non-Goals

**Goals:**

- Deliver a standalone `service/` Maven application implementing every phase-one Session and data-source endpoint.
- Enforce authentication and product ownership at the service boundary, including indistinguishable missing/cross-product responses.
- Keep Tokens, service credentials, database passwords, encryption keys, and internal connection details out of public and diagnostic surfaces.
- Provide secure target connection testing and a bounded dynamic-pool foundation reusable by phase two.
- Make behavior executable through unit, WireMock, and MySQL Testcontainers suites before the real authorization endpoint exists.
- Keep frontend types, controls, and mocks aligned with the accepted backend contract.

**Non-Goals:**

- Implement or modify `bddf-authorization-service`, its Redis state, login/logout endpoints, users, products, or roles.
- Add a production authentication bypass, static-user profile, or fallback identity.
- Implement metadata browsing, SQL execution, cancellation, CSV export, or execution history.
- Add database sharing, product selection, names as identity, TLS certificate verification, or arbitrary JDBC URLs/properties.
- Build browser-owned persistent JDBC sessions or create business pools merely to test a connection.

## Decisions

### 1. Build one Spring Boot 2.7 Maven service under `service/`

The service will target Java 8 bytecode and use Spring MVC/Security, validation, MyBatis, Flyway, MySQL Connector/J, HikariCP, Caffeine, springdoc-openapi 1.x, Actuator/Micrometer, JUnit 5, WireMock or MockWebServer, and Testcontainers. Spring Boot dependency management will pin compatible transitive versions. Maven compiler and animal-sniffer/release checks will prevent accidental Java 9+ API use even when builds run on JDK 17.

The package root will be `com.bocsoft.sqleditor`, split into `auth`, `datasource`, `common`, and `config`. Connection security types live under `datasource.connection` rather than a generic utility package so secret-bearing values have a narrow lifetime and ownership boundary.

A multi-module repository or reactive stack was considered but rejected: phase one has a single deployable and short CRUD/auth/test requests, and Spring MVC matches the reviewed platform baseline.

### 2. Authenticate through a stateless Spring Security filter and narrow auth client

Security defaults to authenticated for `/api/v1/**`; only configured Actuator health/prometheus paths can be exposed separately. A once-per-request filter extracts exactly one Bearer Token, looks up `AuthContext`, and installs an immutable principal for downstream code. Controllers never accept identity arguments.

`AuthClient` uses bounded connect/read timeouts and sends the original Token plus `X-Internal-Service-Key` only to the configured authorization origin. It maps documented 401/409 responses and treats timeouts, transport failures, 5xx, and malformed success bodies as unavailable or invalid context. It neither imports authorization-service packages nor reads its database/Redis.

Caffeine keys entries by SHA-256 Token digest, caps size, and uses a maximum 60-second write TTL. Every cache hit also checks `tokenExpiresAt`; cache lifetime never extends Token validity. Raw Tokens exist only in request/auth-client call scope and are excluded from `toString`, logs, metrics, and exception messages.

Direct Token introspection against authorization Redis or a local fallback user was rejected because either would couple internal schemas or create a production authentication bypass.

### 3. Keep inbound JSON strict without making the external auth response brittle

Application request DTOs use constructor/bean validation plus Jackson unknown-property failure. Validation errors are normalized into stable field codes and `details.fieldErrors`. Empty JSON versus no body is distinguished for the ID-scoped test endpoint.

The authorization response is parsed into an explicit tolerant transport DTO that allow-lists projected fields and ignores legacy extras. This prevents strict client DTOs from breaking on unrelated authorization fields while retaining strictness for browser-controlled writes.

Strings are trimmed at the application boundary except passwords, which remain byte-for-byte UTF-8 values. Create requires a non-empty password and all password-bearing inputs are capped at 1024 characters; update/test empty values mean reuse only where the public contract says so.

### 4. Persist one product-owned aggregate with explicit MyBatis SQL

Flyway V1 creates `sql_data_source` with `utf8mb4`, the documented fields, primary key, product/update keyset index, and non-unique product/name index. Application time uses `Clock`/`Instant`; JDBC is configured for UTC and maps MySQL `datetime(3)` values consistently.

MyBatis statements always include `product_id` in list, detail, update, delete, and saved-test updates. Update uses `WHERE id = ? AND product_id = ? AND version = ?`; if no row changes, a product-scoped read distinguishes invisible/missing from a visible version conflict without ever querying another product's record. Delete follows the same pattern and returns conflict details for a stale visible version.

Create/update/delete run in metadata transactions. Pool invalidation is registered for after-commit, preventing rollback from closing a still-valid pool. Saved connection-test state updates only `last_test_*` and deliberately does not increment configuration version.

JPA was considered but rejected because the design depends on explicit keyset, product predicates, and conditional update semantics already suited to the documented MyBatis baseline.

### 5. Use signed stateless keyset cursors

Lists order by `updated_at DESC, id DESC` and fetch `pageSize + 1`. The next token is Base64URL-encoded versioned JSON containing the last timestamp at micro/millisecond precision, ID, page size, and SHA-256 normalized-keyword hash, followed by HMAC-SHA-256 using a dedicated cursor secret. Parsing is length-bounded, signature comparison is constant-time, and any mismatch becomes `VALIDATION_FAILED`.

An offset cursor was rejected because concurrent writes shift pages. Server-side cursor storage was rejected because it adds cache affinity and expiration state for a naturally stateless boundary. Cursor contents are not sensitive; the signature provides integrity, not encryption.

### 6. Encrypt credentials with versioned AES-256-GCM keys

Configuration supplies a map of Base64-encoded 256-bit keys and one current version. Encryption generates a fresh 96-bit IV with `SecureRandom`; persisted ciphertext, IV, and version remain separate. Decryption selects exactly the row's version and fails closed if unavailable or authentication fails. DTOs and entities avoid generated `toString` methods that could include secret material.

Rotation is additive: deploy old plus new keys, mark the new version current, then run a bounded one-off re-encryption application mode that decrypts/re-encrypts rows in transactions without printing values. Old keys are removed only after a database count confirms no row references them. Reusing the credential key for cursor signing was rejected to keep cryptographic purposes and rotation independent.

### 7. Pin DNS results after an allow/deny CIDR decision

The validator accepts DNS names, IPv4, and IPv6 without scheme, credentials, path, query, fragment, or scoped IPv6 zone. It resolves once with `InetAddress`, normalizes every A/AAAA result, and rejects the whole host if any result is denied or outside allowed CIDRs. An empty allowed list or malformed CIDR fails startup. Deny rules win.

After all results pass, the connection attempt uses a deterministic address from that same result set and never passes the original hostname back to Connector/J, preventing a second DNS resolution. IPv6 is bracketed. Connection failures may try the already-approved addresses within the same overall timeout budget, but they never re-resolve. Because phase-one SSL modes do not verify certificates or hostnames, pinning an address does not weaken a verification guarantee that otherwise existed; the limitation remains explicit in API/deployment documentation.

Allowing all networks by default or validating only the first DNS result was rejected because any authenticated user could otherwise probe internal or rebinding targets.

### 8. Centralize JDBC property and SSL construction

The property validator accepts only:

- `serverTimezone`: a syntactically valid IANA zone known to the JVM;
- `characterSetResults`: `utf8` or `UTF-8`;
- `zeroDateTimeBehavior`: `CONVERT_TO_NULL`, `EXCEPTION`, or `ROUND`;
- `tinyInt1isBit` and `sendFractionalSeconds`: `true` or `false`.

It explicitly rejects all other keys. After allowed values, the builder writes fixed multi-query, local-file, deserialization, Unicode, and encoding settings so user input cannot override them. SSL is mapped only from `DISABLED`, `PREFERRED`, or `REQUIRED`; all three phase-one modes document that certificate and hostname verification are absent.

The frontend will remove `useUnicode` because the service fixes it to true, remove `allowPublicKeyRetrieval` because it is not in the accepted security contract, and add controls for the remaining documented keys. `serverTimezone` becomes validated IANA input rather than a two-value-only contract.

### 9. Separate connection tests from dynamic business pools

Both test endpoints build a short-lived connection configuration, open one connection within the 1–30 second total budget, read server version on success, and close all resources. Attempted target failures return HTTP 200 with stable failure codes; validation/network-policy failures remain `ApiError`s because no connection was attempted. Exception classification uses SQLState/vendor codes and conservative message patterns, with `CONNECTION_FAILED` as the safe fallback.

`DynamicPoolManager` is nevertheless implemented now as the phase-two foundation: lazy per-ID Hikari pools, max 5/min 0, bounded total pools/connections, idle pool retirement, after-commit invalidation, and deterministic shutdown. No phase-one browser endpoint creates a business pool. A narrow connection-use helper owns try/finally reset: defensive rollback when needed, auto-commit true, catalog restored to configured default or null, warnings cleared, then close.

Using business pools for tests was rejected because it would retain test credentials and make test state influence later traffic.

### 10. Normalize API errors and operational surfaces

`RequestIdFilter` runs before security, accepts only UUID request IDs, returns the final ID on every response, and puts it in MDC. `GlobalExceptionHandler` maps validation, auth, visibility, optimistic lock, in-use, and infrastructure failures to direct `ApiError` bodies. Unexpected errors log a server-side stack with safe IDs but return no stack, class, SQL, JDBC URL, or network detail.

Liveness checks only process health. Readiness checks metadata MySQL and performs a bounded reachability probe to the configured authorization origin without a real user Token; any non-5xx HTTP response proves reachability, while transport/5xx failures mark it down. Target data sources are never traversed. Production exposes only health and prometheus on a management port or internal route.

OpenAPI describes direct success and error bodies but marks password inputs write-only. Metrics use bounded status/code tags and never user/product/data-source names, Tokens, SQL, hosts, or passwords.

### 11. Test the public contract at security boundaries

Unit tests cover cipher vectors/rotation/failure, cursor signing and query binding, strict DTOs, LIKE escaping, URL/property construction, IPv6, exception sanitization, and conflict mapping. WireMock verifies auth headers, cache/outage behavior, response projection, and absence of raw Tokens in captured application diagnostics. MySQL Testcontainers exercises Flyway, CRUD, UTC/audit data, test connection, encryption-at-rest, password reuse, and pool invalidation/reset helpers.

Product-isolation tests create identical configurations under products A and B and exercise every item operation with the other product's ID. HTTP contract tests assert exact statuses/bodies/headers, no password/cipher/product leakage, no mutation retries, and strict rejection of ownership fields. Frontend tests reproduce MSW create/update/test flows and inspect runtime responses/state/storage for passwords.

## Risks / Trade-offs

- [The external auth-context endpoint is unavailable during implementation] → Keep a WireMock contract fixture and fail closed in production; real integration remains an explicit release prerequisite.
- [Spring Boot 2.7 and Java 8 are out of community support] → Pin versions, use the organization's security-patch source, run dependency scanning, and keep framework-specific code isolated for later migration.
- [A 60-second positive auth cache permits a logged-out Token briefly] → Make TTL configurable down to zero, never exceed Token expiry, document the window, and avoid stale-on-error behavior.
- [DNS answers can change legitimately] → Resolve on each new test/pool creation, validate all answers, pin only for that connection construction, and recreate pools after configuration/network-policy changes.
- [Phase-one SSL does not authenticate target servers] → State the limitation in UI/OpenAPI/runbook and keep future `VERIFY_CA`/`VERIFY_IDENTITY` additive rather than implying current protection.
- [Strict unknown-field rejection can surface frontend drift immediately] → Keep shared contract tests and MSW behavior aligned; return stable field errors rather than silently accepting unsafe data.
- [Closing a pool after commit can briefly race with a borrower] → Reject new borrows through atomic manager replacement and let Hikari close active connections according to a bounded shutdown policy.
- [Key rotation mode touches every credential] → Use bounded transactions, dry-run counts, backups, idempotent version checks, and retain old keys until verification completes.

## Migration Plan

1. Correct frontend JDBC controls/types and MSW secret handling, then keep MSW enabled for local UI work.
2. Build and test the service against WireMock auth and Testcontainers MySQL; publish OpenAPI and configuration/runbook artifacts.
3. Provision the metadata schema user, credential key map/current version, cursor HMAC secret, internal-service key, network CIDRs, management port, and authorization base URL.
4. Run Flyway and deploy the service with public traffic disabled; verify liveness, readiness, database migration, no secret-bearing configuration output, and auth reachability.
5. After the external auth-context endpoint is available, execute contract tests with real Tokens for products A and B, then point `VITE_SQL_API_BASE`/gateway routes at the service and disable MSW.
6. Roll back application traffic to the previous frontend/Mock deployment if necessary. Do not undo the additive `sql_data_source` migration; an older service version from this change remains schema-compatible.

## Open Questions

No implementation decision remains open for this change. Availability and production rollout of the external authorization context endpoint are tracked outside this repository and remain a release prerequisite.
