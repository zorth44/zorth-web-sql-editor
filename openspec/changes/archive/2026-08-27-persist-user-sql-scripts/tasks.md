## 1. Persistence and session

- [x] 1.1 Add Flyway `V5__create_sql_script.sql`: `sql_script` with id, user_id, username, product_id, name, data_source_id, data_source_name, database_name, statement_text MEDIUMTEXT, version, created/updated audit columns, PK on id, index `(user_id, updated_at DESC, id DESC)`
- [x] 1.2 Add `sql-editor.scripts.max-per-user` (default 200) to `SqlEditorProperties`, `application.yml`, and deployment example env docs
- [x] 1.3 Add `SCRIPT_MANAGE` to `SessionResponse` and assert it on `GET /api/v1/session`
- [x] 1.4 Extend ArchUnit orchestrator rules so `..script..` must not depend on concrete engine packages

## 2. Script API

- [x] 2.1 Add `script` package: record, mapper XML, DTOs (`ScriptSummary`, `ScriptDetail`, create/update requests), `ScriptService`, `ScriptController` at `/api/v1/sql/scripts`
- [x] 2.2 Implement create: validate name 1–100, non-blank statement, UTF-8 size vs `max-statement-bytes`, optional visible data-source snapshot, quota, `201` + Location
- [x] 2.3 Implement list: current `user_id` only, escaped LIKE on name and statement, optional dataSourceId/database filters, cursor `updated_at|id`, pageSize 1–100, 240-char `statementSummary`
- [x] 2.4 Implement detail: owner-only full statement, `connectionAvailable` from current product visibility, `404 SCRIPT_NOT_FOUND` otherwise
- [x] 2.5 Implement PUT/DELETE with `version` optimistic lock; `409 VERSION_CONFLICT` details match data-source shape; wrong owner is `404 SCRIPT_NOT_FOUND`

## 3. Backend tests

- [x] 3.1 Integration: user A CRUD; user B cannot list/get/update/delete A's script
- [x] 3.2 Integration: invisible `dataSourceId` → `404 DATA_SOURCE_NOT_FOUND`; deleted source still readable with `connectionAvailable=false`
- [x] 3.3 Integration: blank statement `400`; oversized statement `413 STATEMENT_TOO_LARGE`; stale version `409 VERSION_CONFLICT`; quota `409 SCRIPT_QUOTA_EXCEEDED`
- [x] 3.4 Integration: keyword search matches name and SQL; pagination continues with a bound page token; two scripts with the same name both persist
- [x] 3.5 Integration: PUT that only changes `name` (same statement and connection) increments version and returns the new name

## 4. Frontend contracts and store

- [x] 4.1 Add `SCRIPT_MANAGE` to `Capability`; add script types and `web/src/api/scripts.ts`
- [x] 4.2 MSW handlers for script CRUD scoped by mock token user; include `SCRIPT_MANAGE` in session fixtures
- [x] 4.3 Extend editor store: `scriptId`, last-save snapshot, dirty flag, persist those fields in Session Storage drafts, restore on reload
- [x] 4.4 Store tests: dirty vs clean close; drafts include `scriptId` not results; logout/`clearAll` drops local drafts only

## 5. Workbench UI

- [x] 5.1 Add Scripts rail beside Database and History; `ScriptPanel` lists/search/rename/delete and shows updated time next to the name; hide rail and save without `SCRIPT_MANAGE`
- [x] 5.2 Open script: focus existing tab with that `scriptId` or create one; unavailable connection restores SQL only
- [x] 5.3 First save prompts for name (default tab title) then POST; bound save PUTs version; Save As POSTs and rebinds; `VERSION_CONFLICT` keeps local text dirty
- [x] 5.4 Monaco `zorth-save` emits save instead of 「工作表保存暂未开放」; toolbar Save / Save As; prevent browser save dialog
- [x] 5.5 Close dirty or never-saved non-empty SQL tabs with confirm; close clean saved tabs without confirm
- [x] 5.6 After confirmed script delete, unbind or close tabs that referenced that id
- [x] 5.7 Rename from a bound tab title and from the Scripts rail: persist name immediately using the last-save snapshot; unsaved tabs only change local title; duplicate names remain listed

## 6. Frontend tests and docs

- [x] 6.1 Component/unit tests for ScriptPanel, save dialog, rename, capability gating, and Monaco save emit
- [x] 6.2 E2E: save SQL, rename, reopen from Scripts rail after reload, save a second script with the same name, delete; assert execution History still does not list scripts
- [x] 6.3 Update `docs/backend-development-spec.md` and `docs/frontend-development-spec.md` for script APIs, `SCRIPT_MANAGE`, and Cmd+S
- [x] 6.4 Run backend tests and frontend typecheck/unit tests; `openspec validate persist-user-sql-scripts --strict`
