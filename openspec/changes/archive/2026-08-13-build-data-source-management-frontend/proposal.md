## Why

The project currently contains only reviewed development documentation, so users have no standalone frontend for signing in and managing their product-scoped MySQL data sources. Phase one should establish the Vue application and deliver the complete data-source management workflow while the SQL service is still being built against the frozen API contract.

## What Changes

- Create the `web/` Vue 3 + TypeScript application, including routing, application shell, query/state infrastructure, styling, quality checks, and automated tests.
- Integrate the existing LDAP authorization flow, Bearer Token lifecycle, safe redirects, multi-account selection, logout, and the old-system Token bridge.
- Add the authenticated data-source list with server-side keyword filtering, cursor navigation, empty/loading/error states, and product context supplied by Session.
- Add create and edit forms for MySQL connection settings, strict validation, JDBC property allow-list controls, unsaved-configuration connection testing, and edit-time password reuse.
- Add saved-configuration connection testing, optimistic-lock conflict handling, typed-name deletion confirmation, and cache invalidation.
- Add MSW development/test handlers that implement the same TypeScript contracts as the future SQL service without enabling Mock behavior in production.
- Keep SQL editing, metadata browsing, execution, export, history, account binding, and backend service implementation outside this change.

## Capabilities

### New Capabilities

- `frontend-auth-session`: LDAP login compatibility, local Token lifecycle, Session validation, protected routing, logout, multi-account selection, and secure old-system Token bridging.
- `frontend-application-shell`: Phase-one routes, authenticated layout, product/user context, navigation behavior, accessibility baseline, and development-only API Mock behavior.
- `data-source-management`: Product-scoped data-source listing, filtering, cursor navigation, detail loading, create/edit validation, connection testing, optimistic locking, and deletion.

### Modified Capabilities

None. There are no existing main specs in this repository.

## Impact

- Adds a new `web/` application and its pnpm lockfile, build configuration, source tree, test suites, and environment examples.
- Integrates browser calls to `bddf-authorization-service` and consumes the future SQL service Session and data-source APIs documented in `docs/backend-development-spec.md`.
- Adds runtime dependencies for Vue 3, Vue Router, Pinia, TanStack Vue Query, Tailwind CSS, and Lucide; adds Vitest, Vue Test Utils, Playwright, ESLint, Prettier, and MSW for development and verification.
- Requires deployment values for the authorization/SQL API base URLs, LDAP product type, legacy portal URL, and allowed Token-bridge origins.
- Production authorization integration remains dependent on removing `ldapUser.pwd` from authorization-service responses and on implementation of the SQL service APIs.
