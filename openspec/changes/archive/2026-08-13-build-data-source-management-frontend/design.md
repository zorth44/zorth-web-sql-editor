## Context

The repository currently contains only the reviewed frontend/backend contracts. Phase one must create a greenfield `web/` application that can be developed before the SQL service exists, while still integrating with the existing `bddf-authorization-service` login behavior.

The authorization service and SQL service have intentionally different response conventions: authorization endpoints return an `AjaxResult` body with an application-level `code`, while SQL endpoints use HTTP status codes, direct success payloads, and the documented `ApiError`. Data-source ownership is enforced only by the future backend: each record belongs to the current product, clients never submit a product identifier, names may repeat, and IDs are the only stable identity.

Security-sensitive constraints include Bearer Token storage, safe redirects, strict `postMessage` origin/source validation, never persisting database passwords, and ignoring legacy authorization response fields. The current authorization implementation can serialize `ldapUser.pwd`; production integration depends on fixing that service even though this frontend also discards the field.

## Goals / Non-Goals

**Goals:**

- Deliver a production-shaped Vue 3 application for every phase-one user journey.
- Keep authorization and SQL transport behavior explicit, typed, and independently testable.
- Make the application usable against MSW now and switchable to the real SQL service without component rewrites.
- Preserve the backend's product-isolation boundary by never accepting or sending client-selected product IDs.
- Provide accessible loading, empty, success, validation, conflict, and failure states.
- Establish automated unit/component and end-to-end coverage for the frozen requirements.

**Non-Goals:**

- Implement or modify the SQL service, authorization service, gateway, or old-system bridge sender.
- Reproduce authorization-service account binding or local-account creation.
- Deliver SQL editing, metadata browsing, execution, history, export, or phase-two state.
- Add client-side product authorization or a product switcher.
- Persist query results, database passwords, or SQL editor content.

## Decisions

### 1. Create a single pnpm/Vite application under `web/`

The application will use Vue 3, TypeScript, `<script setup>`, Vue Router, Pinia, TanStack Vue Query, Tailwind CSS with CSS variables, and Lucide icons. Source code will be grouped primarily by feature (`auth`, `data-sources`) with shared `api`, `router`, `stores`, `types`, and `utils` modules.

This follows the reviewed project layout and leaves room for phase-two features without introducing a monorepo or package abstraction before one is needed. A root-level frontend was considered but rejected because the repository explicitly reserves sibling `web/` and `service/` areas.

### 2. Use separate authorization and SQL API adapters

`authClient` will use `VITE_AUTH_API_BASE`, interpret `AjaxResult.code`, and expose only sanitized discriminated results (`token`, account selection, binding required). `sqlClient` will use `VITE_SQL_API_BASE`, attach `Authorization` and a fresh UUID `X-Request-Id`, parse direct success bodies, and normalize documented error bodies into `ApiError`.

Keeping the adapters separate prevents the legacy application's body-level status convention from leaking into new APIs. A single generalized HTTP wrapper was considered but rejected because it would need endpoint-specific guesses and could treat an authorization HTTP 200/business 500 as success.

### 3. Isolate legacy password compatibility and sensitive login state

The LDAP password adapter will Base64-encode the UTF-8 password and append a 12-character suffix generated from cryptographically secure random bytes. The clear password and encoded value remain local to the submit operation. Multi-account selection keeps the clear credentials only in volatile component/store memory until the branch completes or the user leaves the flow; neither value is written to browser storage, logs, query cache, or error reporting.

Authorization responses will be projected immediately onto an allow-listed frontend shape. Unknown fields, including `ldapUser`, are dropped at the client boundary. This limits accidental propagation, although it does not replace the required authorization-service fix for `ldapUser.pwd`.

### 4. Centralize Token ownership and route recovery

An auth store and storage utility will own the Token. Successful login clears both storage locations and writes exactly one copy according to “remember me.” Startup reads the single available Token, then validates it through `GET /api/v1/session`; the mere presence of a Token never establishes an authenticated session.

Protected navigation records only an internal relative route. Redirect validation rejects absolute URLs, protocol-relative URLs, malformed values, and login/bridge loops. A SQL API 401 clears both Token stores, Session/query state, and navigates to login once even if several requests fail concurrently.

### 5. Treat Session and data sources as server state

TanStack Vue Query will own Session, data-source lists, and details. Pinia will hold client-only authentication flow and shell state, not copies of API collections. Query keys will include the list keyword, page size, and current cursor; detail keys will include only the data-source ID because product context is implicit in the Token.

Session uses the documented five-minute stale window and focus revalidation. Lists use 30 seconds. Mutations invalidate the minimum affected list/detail keys. Test-current-form does not invalidate because it persists nothing; test-saved-configuration invalidates list and detail after either SUCCESS or FAILED.

### 6. Implement cursor navigation with a client-side cursor stack

The list sends a debounced keyword to the backend and never filters loaded rows locally. It stores the cursor used for each visited page in component state so users can move backward and forward. Changing the keyword or page size clears the stack and requests the first page. Numeric random page navigation is not presented because the API intentionally exposes opaque forward cursors.

Rows and actions are keyed only by ID. Duplicate names remain visually valid and do not affect routing, mutation targets, or selection.

### 7. Use one typed connection-form model with explicit request mappers

Create and edit share presentation/validation but use different request mappers. The model includes name, fixed MYSQL engine, host, port, username, transient password, manual default database, SSL mode, timeout, allow-listed JDBC properties, and description.

The create mapper requires a password and omits all product fields. The update mapper includes `version`; an empty password is sent as empty or omitted according to the frozen API and means reuse. The connection-test mapper includes only connection fields: create testing requires a password, while edit testing calls the ID-scoped endpoint and may reuse the saved password. Request types will not contain `productId` or `productIds`, making accidental submission a compile-time error.

Validation is implemented as typed feature utilities rather than adding another form/schema dependency. Client validation improves feedback, while backend `fieldErrors` remain authoritative and map back to fields.

### 8. Use MSW as a contract-faithful development seam

MSW handlers will import the same API types as production clients and model Session, cursor lists, details, mutations, connection results, 401/404, version conflict, and in-use deletion. Mock activation requires both development/test mode and `VITE_ENABLE_API_MOCK=true`; the production entry will not import or register the browser worker.

MSW was chosen over component-local fake repositories because it exercises the actual HTTP adapters, headers, serialization, retry rules, and Vue Query behavior. It is not an alternate API and will not accept fields forbidden by the documented contract.

### 9. Build verification around behavior and security boundaries

Vitest and Vue Test Utils will cover storage, redirect validation, response adapters, request mappers, form validation, cursor reset, cache invalidation, and error presentation. Playwright will cover login/session, list/filter/navigation, create/test, detail-driven edit, duplicate names, edit password reuse, conflict recovery, delete confirmation, 404 behavior, and absence of secrets in browser storage.

Tests will assert that mutation/test operations are never automatically retried and that the production bootstrap cannot register MSW.

## Risks / Trade-offs

- [The real SQL service is unavailable] → Keep one shared contract type layer and exercise it through strict MSW handlers; replace only base URLs/Mock activation during integration.
- [Authorization branches can expose `ldapUser.pwd`] → Drop unknown response fields immediately, never log raw responses, and block production acceptance until the authorization service removes password serialization.
- [Multi-account selection temporarily needs credentials for a second request] → Keep credentials only in volatile memory, clear them after completion/cancel/navigation, and never place them in route/query state.
- [Bearer Tokens in Web Storage are exposed to successful XSS] → Avoid dynamic HTML, enforce CSP at deployment, minimize dependencies, never store passwords, and keep the existing storage requirement explicit.
- [Cursor data can shift while navigating] → Use the backend's stable `updatedAt + id` cursor order, reset on filter changes, and accept that concurrent mutations may change later pages.
- [The bridge requires an old-system sender change] → Implement and test only the strict receiver now; expose allowed origins as deployment configuration and track sender rollout externally.
- [Phase-one CSS/layout choices could constrain the SQL editor] → Keep shell navigation and theme tokens reusable while avoiding phase-two pane/editor abstractions in this change.

## Migration Plan

1. Scaffold and verify `web/` with Mock disabled by default.
2. Develop phase-one flows against MSW and run unit/component/E2E suites.
3. Deploy a preview build with real authorization service and mocked SQL service; verify CORS, LDAP account branches, logout, and Token bridge origins.
4. After the authorization password-response fix and SQL Session/data-source APIs are available, disable MSW and run the same Playwright contract flows against the integration environment.
5. Publish the static frontend behind the gateway with production environment values and CSP.

Rollback is static: restore the previous frontend deployment. No application data migration is performed by this change.

## Open Questions

- What production values will be used for `VITE_AUTH_API_BASE`, `VITE_SQL_API_BASE`, `VITE_LEGACY_PORTAL_URL`, and `VITE_AUTH_BRIDGE_ALLOWED_ORIGINS`?
- Which team and release will remove `ldapUser.pwd` from authorization-service serialization?
- When will the old system ship the Token-bridge sender using the frozen message contract?
