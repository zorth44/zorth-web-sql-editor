## 1. Frontend Foundation

- [x] 1.1 Scaffold the `web/` pnpm/Vite Vue 3 TypeScript application and commit the lockfile.
- [x] 1.2 Add Vue Router, Pinia, TanStack Vue Query, Tailwind CSS, Lucide, Vitest, Vue Test Utils, Playwright, ESLint, Prettier, and MSW with pinned compatible versions.
- [x] 1.3 Configure strict TypeScript, Vue aliases, lint/format/typecheck/test/e2e/build pnpm scripts, and test setup files.
- [x] 1.4 Define CSS variables, Tailwind theme primitives, global focus styles, typography, and the desktop phase-one layout baseline.
- [x] 1.5 Add typed environment loading and examples for both API bases, LDAP product type, legacy portal URL, bridge origins, and Mock activation; reject Mock in production.

## 2. API Contracts and Mock Infrastructure

- [x] 2.1 Define shared Session, authorization result, `ApiError`, data-source list/detail, write request, connection-test, and pagination TypeScript types matching the reviewed docs.
- [x] 2.2 Implement the authorization client that interprets `AjaxResult.code` and projects raw responses onto sanitized token/account-selection/binding-required results.
- [x] 2.3 Implement the SQL client with Bearer and UUID request headers, direct success-body parsing, typed `ApiError` normalization, and coordinated 401 handling.
- [x] 2.4 Implement Session and data-source API modules, including strict create/update/test/delete request mappers that cannot carry product or user identifiers.
- [x] 2.5 Create MSW fixtures and handlers for Session, cursor lists, detail, create, update, both test modes, and delete using the production contract types.
- [x] 2.6 Add deterministic MSW cases for empty lists, duplicate names, connection failures, 401, invisible 404, version conflict, validation errors, and data-source-in-use.
- [x] 2.7 Wire development/test-only MSW startup and verify the production bootstrap neither imports nor registers the browser worker.

## 3. Authentication and Session

- [x] 3.1 Implement the UTF-8 Base64 plus cryptographically random 12-character LDAP password compatibility encoder and unit tests.
- [x] 3.2 Implement exclusive Local/Session Storage Token utilities, in-memory credential cleanup, and safe relative-redirect validation with unit tests.
- [x] 3.3 Implement the auth Pinia store for login branch state, remember choice, one-time credential retention during account selection, and complete cleanup.
- [x] 3.4 Build the login page with username/password validation, pending/error feedback, remember-me Token lifetime behavior, and successful Session validation.
- [x] 3.5 Build multi-account selection and resubmission using `selectUserId`, plus the legacy-portal handoff for `needBind` without implementing binding.
- [x] 3.6 Implement the Session query, expiry/capability evaluation, focus revalidation, protected route guard, and safe post-login route restoration.
- [x] 3.7 Implement single-flight 401 teardown that clears Token, Pinia, Vue Query, transient passwords, and redirects once to login.
- [x] 3.8 Implement best-effort `POST /logout`, clearing both Token stores and client/query state regardless of remote outcome.
- [x] 3.9 Build `/auth/bridge` with strict opener/origin/message validation, candidate Token Session validation, Token-free acknowledgment, and failure states.
- [x] 3.10 Add unit/component tests proving raw `ldapUser`/`pwd` fields and credentials never enter stores, cache, routes, logs, or rendered output.

## 4. Application Shell and Routing

- [x] 4.1 Configure `/login`, `/auth/bridge`, protected data-source routes, phase-one `/sql-editor` behavior, and the catch-all 404 route.
- [x] 4.2 Build the authenticated shell with application identity, read-only Session product, user menu, and logout action.
- [x] 4.3 Add reusable accessible loading, empty, error, notification, status badge, and confirmation-dialog components.
- [x] 4.4 Verify keyboard navigation, visible focus, labels, status announcements, dialog focus trapping, and supported 1024px+ Chrome/Edge layout.

## 5. Data-Source List

- [x] 5.1 Implement Vue Query keys/options for Session, 30-second data-source lists, details, safe read retries, and mutation-specific invalidation.
- [x] 5.2 Build the list page/table with required fields, `updatedByName`, test/edit/delete actions, capability gating, and no password/raw user/product controls.
- [x] 5.3 Implement debounced server keyword search that resets the cursor sequence and never filters only loaded rows.
- [x] 5.4 Implement previous/next opaque cursor navigation with a cursor stack, page-size reset behavior, and last-page disabling.
- [x] 5.5 Ensure duplicate-named rows remain separately keyed/routed/actioned by ID and add focused component tests.
- [x] 5.6 Implement saved-configuration connection testing from the list, SUCCESS/FAILED result presentation, no automatic retry, and list/detail invalidation.

## 6. Data-Source Create and Edit Forms

- [x] 6.1 Build a shared typed MySQL form model and reusable controls for identity, connection, manual default database, SSL mode, timeout, JDBC allow-list properties, and description.
- [x] 6.2 Implement client validation for all documented lengths/ranges, protocol-free host handling, create-required password, duplicate-name acceptance, and property values.
- [x] 6.3 Implement backend `fieldErrors` mapping to controls with a safe summary fallback for unrecognized fields.
- [x] 6.4 Build the create page, create request mapping, pending-state duplicate prevention, success navigation, query invalidation, and transient password cleanup.
- [x] 6.5 Build the edit page using authoritative detail loading, empty password/preserved-secret guidance, invisible 404 handling, and no password reconstruction.
- [x] 6.6 Implement full PUT mapping with loaded `version`, empty-password preserve semantics, success invalidation/navigation, and transient password cleanup.
- [x] 6.7 Implement create-form connection testing with connection fields only, the form timeout, SUCCESS/FAILED feedback, and no automatic retry.
- [x] 6.8 Implement unsaved edit-form connection testing through the ID endpoint with empty-password reuse and without cache invalidation.
- [x] 6.9 Implement `VERSION_CONFLICT` presentation using safe current updater/time details and an explicit reload-current-detail action.

## 7. Deletion Workflow

- [x] 7.1 Build typed-name deletion confirmation that displays the selected record and disables submission until the exact name matches.
- [x] 7.2 Send DELETE using only the selected ID/version, handle 204 success, invalidate affected queries, and preserve correctness for duplicate names.
- [x] 7.3 Handle `DATA_SOURCE_IN_USE` with `runningTaskCount`, keep the record/dialog recoverable, and never automatically retry deletion.

## 8. Verification and Handoff

- [x] 8.1 Add auth/session unit and component coverage for storage selection, safe redirects, login response branches, expiry/capability handling, 401 teardown, bridge validation, and logout failure.
- [x] 8.2 Add data-source unit and component coverage for request mappers, no product fields, validation, cursor reset/back navigation, duplicate names, password reuse, error mapping, retry rules, and cache invalidation.
- [x] 8.3 Add Playwright flows for login, list/search/pagination, create/test, detail-driven edit, duplicate names, version conflict, saved test, typed deletion, invisible 404, and logout.
- [x] 8.4 Add browser-persistence assertions that Tokens use exactly one selected store and database/LDAP passwords never appear in either store or persisted cache.
- [x] 8.5 Run formatting, lint, typecheck, Vitest, Playwright, and production build; resolve all failures and verify production Mock rejection.
- [x] 8.6 Document local Mock startup, environment variables, verification commands, real-backend cutover, bridge sender dependency, and authorization `ldapUser.pwd` production blocker.
