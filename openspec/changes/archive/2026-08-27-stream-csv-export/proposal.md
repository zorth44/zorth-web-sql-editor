## Why

CSV 导出在规格上已经是流式的，但两条路径其实没流起来：PostgreSQL 在 `autoCommit=true` 下会忽略 `fetchSize`，驱动仍可能把结果攒进 JVM；浏览器用 `response.blob()` 等整份文件下完再下载。10 万行 / 100 MiB 上限能避免无界爆炸，但宽表导出仍会在服务端和浏览器各堆一份峰值。

## What Changes

- 导出在创建流式 `Statement` 之前，按引擎决定是否把连接临时切到 `autoCommit=false`，让 PostgreSQL 的 `fetchSize` 真正走服务端游标；还池时仍走现有 `rollback` + 恢复 `autoCommit=true`。
- MySQL / GBase 8A 保持现有 `Integer.MIN_VALUE` 逐行流，不为此改事务模式。
- 浏览器在支持 File System Access 时先选保存位置，再把响应体按块写入文件，不再把整份 CSV 收成 Blob；不支持该 API 时保留现有 Blob 回退，100 MiB 上限仍生效。
- 取消导出时中止 fetch，并丢弃未完成的目标文件。不改导出 API、行数上限、体积上限或超时。

## Capabilities

### New Capabilities

- 无。本次只补齐已承诺的流式导出，不引入新能力域。

### Modified Capabilities

- `backend-engine-spi`: 引擎声明流式导出是否必须关闭 `autoCommit`，由导出路径调用，而不是在编排层写死 PostgreSQL。
- `backend-postgresql-engine`: PostgreSQL 导出必须关闭 `autoCommit`，否则 `fetchSize` 不生效。
- `backend-sql-history-export`: 导出在 JDBC 侧按引擎准备流式游标，逐行写 HTTP，不在驱动或 JVM 中缓冲完整结果集。
- `frontend-sql-editor-workbench`: 支持流式落盘的浏览器把 CSV 块写入用户选择的文件；其余浏览器保持 Blob 回退。取消时中止请求并丢弃未完成文件。

## Impact

- 后端：`EngineSupport` 增加流式会话准备；`PostgresEngineSupport` 声明需要关 `autoCommit`；`CsvExportService` 在开 `Statement` 前调用；还池逻辑复用 `ConnectionUse.resetAndClose`。
- 前端：`exportExecution` / 下载确认路径改为可选的 `showSaveFilePicker` + `WritableStream`；Blob 回退与 `AbortSignal` 保留。
- API：`POST /api/v1/sql/exports` 契约不变。
- 文档：同步 `docs/backend-development-spec.md` 与 `docs/frontend-development-spec.md` 中「流式导出 / Blob 下载」的过时表述。
- 不改行数/体积/超时上限，不新增依赖，不把 File System Access 做成唯一路径。
