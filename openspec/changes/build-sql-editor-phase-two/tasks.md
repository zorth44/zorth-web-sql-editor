## 1. Persistence and configuration foundation

- [x] 1.1 Add the execution-history Flyway migration and MyBatis record/mapper with user-scoped list/detail, lifecycle updates, conflict checks, and escaped filters
- [x] 1.2 Extend service configuration with execution, export, history-retention, and executor settings plus second-stage Session capabilities
- [x] 1.3 Expose a product-scoped internal saved-connection accessor and preserve deterministic target connection session reset

## 2. Backend metadata and execution

- [x] 2.1 Implement database/table/view/table-detail metadata DTOs, cursor pagination, service, and controller
- [x] 2.2 Implement the quote/comment-aware single-statement scanner, classifier, request validation, and execution result DTOs
- [x] 2.3 Implement JDBC value encoding and bounded result reading for precision, binary values, row limits, byte limits, and result kinds
- [x] 2.4 Implement bounded asynchronous execution, per-user/global quotas, running registry/counter, cancellation, timeout, and history finalization
- [x] 2.5 Map SQL execution failures to stable safe API errors and add execution metrics/log-safe diagnostics

## 3. Backend history and export

- [x] 3.1 Implement current-user history list/detail APIs with signed filter-bound keyset cursors and connection availability
- [x] 3.2 Implement RFC 4180 CSV encoding with BOM, CRLF, NULL policy, formula protection, and byte accounting
- [x] 3.3 Implement query-only history replay export with target reauthorization, independent EXPORT history, streaming response, limits, and cleanup
- [x] 3.4 Add stale RUNNING recovery and configurable history-retention cleanup

## 4. Temporary authorization service

- [x] 4.1 Add the zero-dependency local auth-service with health, LDAP-compatible login/logout, in-memory Token expiry, internal context, key validation, and CORS allow-list
- [x] 4.2 Document and configure the real local Web → auth-service → SQL service → MySQL startup path without production fallback

## 5. Frontend API and editor state

- [x] 5.1 Add phase-two TypeScript contracts and API clients for metadata, execution/cancel, export, and history with required retry/AbortSignal behavior
- [x] 5.2 Add SQL scanning/current-statement utilities and a Pinia editor store with connection-bound tabs, three-run limit, bounded Session Storage drafts, and auth teardown
- [x] 5.3 Add Monaco, formatting, split layout, virtual result dependencies and an editor component with MySQL completion and keyboard commands

## 6. Frontend workspace

- [x] 6.1 Implement the SQL editor route/default landing, compact shell navigation, connection URL state, and capability-aware actions
- [x] 6.2 Implement lazy resource browser/search/refresh/table detail and quoted-name/SELECT generation actions
- [x] 6.3 Implement execution/cancel lifecycle, typed result/message/error presentation, copy/filter/detail interactions, and DDL metadata invalidation
- [x] 6.4 Implement CSV replay download/cancel confirmation and current-user history filtering/pagination/detail reopen flows
- [x] 6.5 Extend MSW production-contract mocks for all phase-two endpoints and failure states

## 7. Verification and delivery

- [x] 7.1 Add backend unit/integration tests for metadata, scanner, type encoding, conflicts, isolation, cancellation, history filters, and CSV safety
- [x] 7.2 Add frontend unit/component tests for SQL extraction, tab persistence, special result values, DDL invalidation, metadata/history, execution/cancel, and export
- [x] 7.3 Add a second-stage E2E core-flow test and verify frontend formatting, lint, typecheck, Vitest, production build, and Playwright
- [x] 7.4 Run Maven test/verify, local auth-service contract checks, OpenSpec strict validation, and update deployment/API documentation
