## 1. Qualifier parsing and catalog helpers

- [x] 1.1 Add a pure helper module under `web/src/sql-editor/` that parses `qualifier.prefix` at a caret, unwraps engine-quoted identifiers, resolves a table against a loaded name list (exact, then unique case-insensitive), and builds insert text with the existing `quoteIdentifier` behavior when quoting is required
- [x] 1.2 Reuse the existing scanner state rules or an equivalent lightweight scan so qualifier-like text inside single-quoted/dollar-quoted strings, line comments, or block comments is not treated as SQL code
- [x] 1.3 Unit-test unquoted `orders.`, quoted `` `orders`. `` / `"orders".`, doubled quotes inside identifiers, `db.orders.` taking the last identifier, prefix replacement, exact-match precedence, unique and ambiguous case-insensitive matches, unresolved aliases, and string/comment contexts
- [x] 1.4 Unit-test safe insertion for column names containing backticks and double quotes, asserting that the internal engine quote is doubled and no extra SQL is inserted

## 2. Structured metadata catalog

- [x] 2.1 Change `ResourceBrowser` `suggestions` emit from a flat `string[]` to a binding-tagged `{ dataSourceId, namespace, generation, namespaces, tables, columnsByTable }` snapshot sourced from already-loaded databases/tables/details, with the page-owned completion generation passed into the browser as a prop
- [x] 2.2 Update `SqlEditorPage` to retain table-directory snapshots by collision-safe `(dataSourceId, NAMESPACE)` identity, select the active catalog from the current SQL tab rather than event arrival order, and preserve loaded directories while the resource browser is unmounted
- [x] 2.3 Implement `resolveColumns` with a generation/binding/table-scoped in-memory cache, in-flight coalescing, empty list on failure, and synchronous invalidation before refresh or connection change; late responses from an older generation SHALL NOT write or publish columns
- [x] 2.4 Clear the ResourceBrowser completion-facing `columnsByTable` projection before awaiting metadata refresh and prevent stale binding/generation loads from publishing, without leaking other bindings' directory or detail data into the active catalog
- [x] 2.5 Update resource-browser / editor-page tests for binding-tagged catalog snapshots, active-tab scoping, persistence across sidebar unmount, seeding columns from already-opened tables, unknown qualifiers, refresh-before-response races, and connection switches while a detail request is in flight

## 3. Monaco completion provider

- [x] 3.1 Register `.` as a trigger character; make `provideCompletionItems` async; read the latest catalog through a ref instead of re-registering on every catalog change
- [x] 3.2 When the caret is after a resolved table qualifier, offer only that table's columns (await `resolveColumns` if uncached); when the qualifier form is present but unresolved or the caret is in a string/comment, offer nothing; otherwise offer namespaces, tables, and cached columns
- [x] 3.3 Use distinct completion kinds for NAMESPACE / table / column, and insert quoted column names only when required
- [x] 3.4 Extend `SqlMonacoEditor` tests to capture the provider options and completion results for `orders.`, quoted qualifiers, prefix after the dot, string/comment contexts, safely escaped insert text, and the unqualified fallback

## 4. Verification and docs

- [x] 4.1 Update `docs/frontend-development-spec.md` so metadata completion documents `表.` field suggestions with on-demand table-detail, not a flat name list
- [x] 4.2 Run frontend typecheck, unit/component tests, and `openspec validate add-table-column-completion --strict`
