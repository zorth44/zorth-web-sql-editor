## 1. Frontend Contract Corrections

- [x] 1.1 Replace the frontend `JdbcProperties` keys with `serverTimezone`, `characterSetResults`, `zeroDateTimeBehavior`, `tinyInt1isBit`, and `sendFractionalSeconds`.
- [x] 1.2 Update data-source form controls and validation for the authoritative property values, arbitrary valid IANA timezone input, and the 1024-character password limit.
- [x] 1.3 Remove `useUnicode` and `allowPublicKeyRetrieval` from request mapping/UI paths and add tests proving undeclared properties are not submitted.
- [x] 1.4 Rewrite the MSW create handler to project an explicit password-free detail object instead of spreading the request into state/response.
- [x] 1.5 Add frontend tests that inspect create/update/test Mock responses, Mock state, Vue Query/mutation state, and browser storage for database passwords.
- [x] 1.6 Run frontend format, lint, typecheck, unit/component, E2E, and production-build checks after the contract corrections.

## 2. Service Foundation

- [x] 2.1 Create the `service/` Spring Boot 2.7.18 Maven project with the `com.bocsoft.sqleditor` application entry point and Java 8 release enforcement.
- [x] 2.2 Add managed dependencies for Spring MVC/Security/Validation, MyBatis, Flyway, MySQL Connector/J, HikariCP, Caffeine, springdoc-openapi, Actuator/Micrometer, JUnit 5, WireMock, and Testcontainers.
- [x] 2.3 Create package boundaries for `auth`, `datasource`, `datasource.connection`, `common`, and `config`, with architecture tests preventing inappropriate dependency direction.
- [x] 2.4 Add typed configuration properties for metadata MySQL, auth client/cache, credential keys, cursor signing, target network policy, pool limits, management endpoints, and connection defaults.
- [x] 2.5 Add startup validation that rejects missing/malformed credential keys, missing cursor/internal-service secrets, unsafe empty CIDR allow-lists, invalid CIDRs, and invalid numeric bounds.
- [x] 2.6 Add local/test/production configuration samples that contain placeholders only and prove secrets are not exposed through configuration diagnostics.

## 3. Common HTTP and Operational Contracts

- [x] 3.1 Implement `RequestIdFilter` to accept UUID request IDs, generate replacements, populate MDC, and return the final `X-Request-Id` on all responses.
- [x] 3.2 Define the stable `ApiError`, field-error, version-conflict, in-use, and product-context detail models without secret-bearing `toString` output.
- [x] 3.3 Configure strict unknown-property rejection for browser request DTOs while retaining a separately tolerant authorization transport DTO.
- [x] 3.4 Implement `GlobalExceptionHandler` mappings for validation, authentication, visibility, optimistic lock, in-use, auth outage, malformed JSON, and safe unexpected 5xx responses.
- [x] 3.5 Configure Spring Security as stateless and authenticated-by-default for `/api/v1/**`, with only explicitly selected management paths exposed separately.
- [x] 3.6 Add OpenAPI base configuration with direct success/error schemas, password inputs marked write-only, and no credential persistence fields in generated models.
- [x] 3.7 Add unit/MVC tests for request-ID acceptance/replacement, error-body shape, strict fields, 204 behavior, content types, and stack/secret omission.

## 4. Authorization Context and Session

- [x] 4.1 Implement immutable current-user/product principal and authorization transport DTOs that project only documented fields.
- [x] 4.2 Implement the bounded auth-context HTTP client with Bearer and `X-Internal-Service-Key` headers, timeouts, and 401/409/5xx/transport/malformed-response mappings.
- [x] 4.3 Implement SHA-256 Token-digest caching with maximum size, configurable TTL capped at 60 seconds, expiry recheck, eviction, and no stale-on-error behavior.
- [x] 4.4 Implement the once-per-request Bearer authentication filter and prevent client user/product fields from affecting the installed principal.
- [x] 4.5 Implement `GET /api/v1/session` with the minimal user/product projection, original Token expiry, and exactly `DATA_SOURCE_MANAGE` for phase one.
- [x] 4.6 Add WireMock tests for successful resolution, legacy extra fields, invalid/expired Tokens, product cardinality errors, cache hits/expiry, auth outage, and fail-closed behavior.
- [x] 4.7 Add log/metric capture tests proving raw Tokens and the internal-service key never appear in diagnostics, errors, caches, or health details.

## 5. Metadata Schema and Persistence

- [x] 5.1 Create Flyway V1 for `sql_data_source` using `utf8mb4`, documented columns, UUID key, product/update keyset index, and non-unique product/name index.
- [x] 5.2 Configure the metadata datasource, Flyway, MyBatis, UTC JDBC/session behavior, and millisecond-precision `Instant` mapping.
- [x] 5.3 Implement data-source persistence models and explicit MyBatis result mappings that retain encrypted credential fields only inside the persistence boundary.
- [x] 5.4 Implement product-scoped insert, detail, keyset list, current-version update, saved-test-state update, conflict read, and current-version delete statements.
- [x] 5.5 Implement safe LIKE escaping for `%`, `_`, and the escape character and test name/host keyword matching under the selected collation.
- [x] 5.6 Add Testcontainers migration/repository tests for duplicate names/configurations, UTC audit snapshots, product predicates, keyset ordering, and no plaintext-at-rest.

## 6. Cryptography and Pagination

- [x] 6.1 Implement `CredentialCipher` with AES-256-GCM, random 96-bit IVs, current-version encryption, version-selected decryption, and authenticated-failure handling.
- [x] 6.2 Add deterministic cipher tests for round trips, unique IV/ciphertext output, wrong key/version, tampering, malformed Base64, and startup key validation.
- [x] 6.3 Implement the bounded one-off credential re-encryption mode with dry-run counts, batched transactions, idempotent version checks, and secret-free output.
- [x] 6.4 Implement the versioned Base64URL/HMAC-SHA-256 cursor codec bound to timestamp, ID, normalized keyword hash, and page size using constant-time signature checks.
- [x] 6.5 Add cursor tests for first/last pages, identical timestamps, changed keyword/page size, tampering, malformed/oversized payloads, and precision round trips.

## 7. Data-Source CRUD APIs

- [x] 7.1 Define strict create, full-update, list-query, delete-query, list-item, detail, cursor-page, and mapper DTOs matching the frontend contracts exactly.
- [x] 7.2 Implement normalization and validation for MYSQL engine, names, hosts, ports, usernames, password semantics/length, database, SSL, timeout, properties, description, and versions.
- [x] 7.3 Implement product-scoped list and detail services/controllers with signed keyset pagination, escaped keyword filtering, list/detail projections, and invisible 404 behavior.
- [x] 7.4 Implement create with server-derived ownership/audit fields, UUID/version 1, credential encryption, no implicit connection test, 201 detail body, and `Location` header.
- [x] 7.5 Implement transactional full update with password preservation/replacement, conditional version increment, safe conflict details, and after-commit pool invalidation.
- [x] 7.6 Implement transactional versioned delete with invisible 404, running-task hook, `DATA_SOURCE_IN_USE`, safe version conflict, after-commit pool closure, and empty 204 response.
- [x] 7.7 Add MVC/service tests for duplicate names, unknown ownership fields, strict/full PUT behavior, empty password preservation, stale versions, cross-product IDs, and response confidentiality.

## 8. JDBC and Target Network Security

- [x] 8.1 Implement host syntax parsing for DNS/IPv4/IPv6 and reject schemes, user info, paths, queries, fragments, scoped IPv6 zones, and overlength values.
- [x] 8.2 Implement CIDR parsing/matching for IPv4 and IPv6 with deny precedence and startup rejection of malformed or empty allow policy.
- [x] 8.3 Implement single-resolution DNS evaluation that checks every A/AAAA result, pins approved addresses, applies a shared timeout budget, and never re-resolves during connection construction.
- [x] 8.4 Implement the JDBC property validator for the exact five keys and allowed values, including JVM-known IANA timezone validation.
- [x] 8.5 Implement safe MySQL URL/property construction with IPv6 brackets, fixed non-overridable security settings, and documented DISABLED/PREFERRED/REQUIRED mappings.
- [x] 8.6 Implement conservative SQLException/network exception classification and sanitized result messages for every stable connection failure code.
- [x] 8.7 Add unit tests for CIDR boundaries, mixed safe/unsafe DNS answers, rebinding prevention, IPv6 URLs, property injection attempts, SSL mapping, fixed-option precedence, and message redaction.

## 9. Connection-Test Endpoints

- [x] 9.1 Implement the short-lived connection tester with one overall 1–30 second budget, approved-address fallback, server-version capture, timing, and deterministic resource closure.
- [x] 9.2 Implement `POST /api/v1/data-sources:test` with connection-only strict DTO, required password, no persistence, and validation-before-connect behavior.
- [x] 9.3 Implement no-body `POST /api/v1/data-sources/{id}:test` using the visible saved credential and a product-scoped last-test update that does not increment version.
- [x] 9.4 Implement body-present `POST /api/v1/data-sources/{id}:test` with full connection validation, saved-password reuse, and no form or last-test persistence.
- [x] 9.5 Add MVC/Testcontainers tests for success, authentication failure, refusal, timeout budget, missing database, TLS failure, saved/edit password modes, cross-product 404, and HTTP-200 attempted failures.
- [x] 9.6 Add assertions that all connection-test paths close resources and never place plaintext passwords, JDBC URLs, or unnecessary target addresses in responses, logs, metrics, or persisted state.

## 10. Dynamic Pool Lifecycle

- [x] 10.1 Implement a thread-safe lazy `DynamicPoolManager` with per-ID Hikari pools, max-size 5/min-idle 0 defaults, bounded global pools/connections, idle retirement, and application shutdown cleanup.
- [x] 10.2 Implement atomic stale-pool rejection and after-commit update/delete invalidation so new borrowers cannot acquire superseded configuration.
- [x] 10.3 Implement a connection-use/reset helper that rolls back defensively, restores auto-commit/catalog, clears warnings, and closes Connection/Statement/ResultSet deterministically.
- [x] 10.4 Add concurrent unit/Testcontainers tests for one-pool creation, global limits, update/delete races, idle eviction, shutdown, catalog/session reset, and no pool creation by test endpoints.

## 11. Observability and Deployment Documentation

- [x] 11.1 Add bounded Micrometer metrics for auth outcomes, data-source operations, connection-test outcomes/duration, pool counts/waits, and safe failure classifications.
- [x] 11.2 Implement liveness without dependencies and readiness for metadata MySQL plus bounded auth-origin reachability, explicitly excluding target data-source traversal.
- [x] 11.3 Restrict production Actuator exposure to health/prometheus and add tests that env, configprops, heapdump, and secret-bearing details are unavailable.
- [x] 11.4 Write the service README with local WireMock/Testcontainers workflow, Maven verification commands, API base/gateway integration, and external auth-endpoint prerequisite.
- [x] 11.5 Document every environment variable/secret, metadata privileges, network CIDR behavior, phase-one SSL limitations, management-port exposure, and safe production defaults.
- [x] 11.6 Document credential key generation, additive rotation/re-encryption, verification, rollback, and old-key removal procedures without example production secrets.

## 12. End-to-End Verification

- [x] 12.1 Add HTTP contract tests covering every Session/data-source endpoint, exact statuses/headers/bodies, cursor behavior, strict fields, and direct success payloads.
- [x] 12.2 Add product-isolation tests with products A/B and identical data-source values across list, detail, saved/edit tests, update, delete, and stale-version paths.
- [x] 12.3 Add automated secret scans/assertions across serialized DTOs, logs, metric tags, Actuator output, cache keys, database rows, Mock state, and generated OpenAPI.
- [x] 12.4 Run the complete Maven unit/integration suite on JDK 17 with Java 8 release checks and record any environment-dependent Testcontainers prerequisites.
- [ ] 12.5 Run the corrected frontend against the service with WireMock authentication and validate login Session, list/search/cursors, create/test/edit/conflict/delete, 401, and invisible 404 flows.
- [x] 12.6 Run `openspec validate build-data-source-management-backend` and reconcile the delivered API/OpenAPI, code, tests, and runbooks with every proposal requirement before requesting archive.
