## Why

The phase-one frontend is complete, but it still runs against development mocks because the repository has no SQL service. A production-ready backend is now required to validate existing authorization Tokens, isolate data sources by the current user's product, protect database credentials, and implement the frozen Session and data-source APIs.

## What Changes

- Add a standalone Java 8-compatible Spring Boot 2.7 service under `service/`, including Maven build, configuration profiles, Flyway migrations, MyBatis persistence, OpenAPI, health checks, metrics, request correlation, and unified API errors.
- Consume—but do not implement or modify—the external `bddf-authorization-service` internal auth-context contract, with SHA-256 Token-keyed caching, fail-closed behavior, and WireMock coverage.
- Implement `GET /api/v1/session` and every phase-one data-source list, detail, create, replace, delete, and connection-test endpoint expected by the frontend.
- Enforce product ownership exclusively from the authenticated context, return indistinguishable 404 responses for missing and cross-product records, and use stable cursor pagination and optimistic locking.
- Encrypt database passwords with versioned AES-256-GCM keys, construct JDBC URLs from validated fields, restrict JDBC properties, enforce resolved-address CIDR policy, prevent DNS rebinding, sanitize connection failures, and manage lifecycle hooks for dynamic target pools.
- Add unit, WireMock, MySQL Testcontainers, product-isolation, secret-confidentiality, and contract integration tests, plus deployment, key generation/rotation, and environment configuration documentation.
- Correct the frontend JDBC-property controls/types to the backend allow-list and remove the Mock create-path password leak so development behavior remains contract-faithful.
- Keep authorization-service implementation, account management, SQL metadata browsing, SQL execution, cancellation, export, and execution history outside this change.

## Capabilities

### New Capabilities

- `backend-auth-session`: Bearer Token authentication through the external authorization context API, current Session projection, cache and outage semantics, and authenticated request context.
- `backend-data-source-management`: Product-scoped persistence and the complete phase-one data-source CRUD, cursor pagination, optimistic locking, response confidentiality, and error contracts.
- `backend-connection-security`: Versioned credential encryption, safe JDBC configuration, DNS/CIDR enforcement, sanitized connection testing, and dynamic connection-pool lifecycle foundations.

### Modified Capabilities

- `data-source-management`: Align frontend JDBC-property controls with the authoritative backend allow-list and require Mock create responses/state to exclude database passwords.

## Impact

- Adds the new `service/` Maven project, Java sources, configuration samples, Flyway schema, automated tests, service documentation, and generated OpenAPI support.
- Changes frontend contract types, validation, form controls, MSW handlers, and focused tests without changing the public data-source endpoint shapes.
- Adds runtime dependencies on the SQL editor metadata MySQL, the authorization-service auth-context endpoint, credential/cursor secrets, and configured target-network CIDR rules.
- Production integration remains blocked until `bddf-authorization-service` independently provides `GET /internal/api/v1/auth/context`; this change supplies only its client contract and test stub.
