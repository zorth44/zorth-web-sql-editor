## 1. Resource tree

- [x] 1.1 Pass the visible data-source list into `ResourceBrowser` and render data sources as tree roots with name and host:port
- [x] 1.2 Lazy-load databases per expanded data source and key table/detail/expand state by `dataSourceId + database`
- [x] 1.3 Allow multiple data sources to stay expanded; selecting a database or table binds the editor without collapsing other sources
- [x] 1.4 Filter the top search across data-source name/host and databases under expanded sources; keep per-database table/view filter
- [x] 1.5 Show an empty state that points to data-source management when no sources exist

## 2. Editor binding

- [x] 2.1 Emit data-source plus database from the tree and keep toolbar selectors two-way synced via `applyConnection`
- [x] 2.2 On workspace load, bind and auto-expand the URL or first visible source without hiding other sources
- [x] 2.3 Refresh only already-expanded data sources after DDL or the sidebar refresh action

## 3. Verification

- [x] 3.1 Update unit/component coverage for tree roots, lazy database load, multi-source keys, and toolbar sync
- [x] 3.2 Update Playwright assertions so the navigator shows data sources first and databases only after expand
