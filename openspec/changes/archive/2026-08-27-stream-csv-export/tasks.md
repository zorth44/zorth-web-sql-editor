## 1. Engine SPI and PostgreSQL cursor

- [x] 1.1 Add `EngineSupport.streamingRequiresAutoCommitOff()` defaulting to `false`; override it to `true` on `PostgresEngineSupport`
- [x] 1.2 Assert MYSQL (and GBase 8A if present) keep the default `false`, PostgreSQL returns `true`, and PostgreSQL `streamingFetchSize()` stays a positive value
- [x] 1.3 Confirm `export` still depends only on `EngineSupport`, not `engine.postgres` / `engine.mysql` implementation classes

## 2. Export connection session

- [x] 2.1 In `CsvExportService`, after `applyNamespace` and before `createStatement`, set `autoCommit=false` only when the engine requires it; keep `TYPE_FORWARD_ONLY` and `setFetchSize(engine.streamingFetchSize())`
- [x] 2.2 On a successful streaming export that turned autocommit off, `commit()` then restore `autoCommit=true` before release; on failure, cancel, disconnect, or limit, do not commit and let `targets.release` / `resetAndClose` roll back
- [x] 2.3 Unit-test the export session: PostgreSQL path calls `setAutoCommit(false)` and `commit` on success; MySQL path never flips autocommit; cancel/failure path never commits

## 3. Browser streaming download

- [x] 3.1 Extend `exportExecution` to write `response.body` chunks into an optional `WritableStream` without building a complete Blob or string; Blob + `Content-Disposition` filename remains the fallback return
- [x] 3.2 On export confirm, if `showSaveFilePicker` exists, open it before fetch with a suggested `{dataSource}-{database}.csv` name; picker cancel MUST NOT send the request or show export failure
- [x] 3.3 Abort in-flight export still uses `AbortSignal`; if a file writable is open, abort it so a partial file is not treated as a successful download
- [x] 3.4 Unit-test stream-to-writable, Blob fallback, picker cancel, and abort-while-writing

## 4. Docs and verification

- [x] 4.1 Update `docs/backend-development-spec.md` so CSV export documents engine-specific cursor/autocommit, not only `StreamingResponseBody`
- [x] 4.2 Update `docs/frontend-development-spec.md` so download is File System Access streaming with Blob fallback, not “always Blob”
- [x] 4.3 Run backend unit tests, frontend typecheck/unit tests, and `openspec validate stream-csv-export --strict`
