## Context

`POST /api/v1/sql/exports` 已经用 `StreamingResponseBody` 逐行写 CSV，并按引擎设置 `streamingFetchSize()`。MySQL / GBase 8A 用 `Integer.MIN_VALUE`，Connector/J 在 `autoCommit=true` 下就能逐行流。PostgreSQL 必须 `autoCommit=false` 且 `fetchSize > 0` 才会开服务端游标；当前池和导出都是 `autoCommit=true`，`fetchSize=100` 实际被忽略。

浏览器 `exportExecution()` 用 `response.blob()` 等整份响应结束再 `createObjectURL`。规格把这当成可接受的第一阶段做法，靠 100 MiB 上限兜内存。宽表接近上限时，服务端（PG 驱动）和浏览器会同时出现峰值。

连接还池已经走 `ConnectionUse.resetAndClose`：若 `!autoCommit` 则 `rollback()`，再恢复 `autoCommit=true`。导出不应另写一套还池逻辑。常规 `POST /api/v1/sql/executions` 继续 `autoCommit=true`，本变更只动导出。

## Goals / Non-Goals

**Goals:**

- PostgreSQL 导出真正使用服务端游标，JDBC 驱动只保留 `fetchSize` 那么多行。
- 引擎用 SPI 声明是否需要关 `autoCommit`，导出编排不写死 `POSTGRESQL`。
- 支持 File System Access 的浏览器用确认手势先选文件，再把响应体按块写入；取消时中止 fetch 并丢弃未完成文件。
- 不支持该 API 的浏览器保留 Blob 回退；100k 行 / 100 MiB / 60 秒上限不变。

**Non-Goals:**

- 不改导出 HTTP 契约、不放开行数/体积/超时。
- 不把常规 SQL 执行改成事务/游标模式。
- 不引入 StreamSaver、service worker 或短时下载 Token。
- 不把 File System Access 做成唯一下载路径。
- 不处理单格超大 `BLOB`/`TEXT` 的物化（仍是按行流，单元格仍 `getObject`/`getBytes`）。
- 不改网关/Nginx 的 `proxy_buffering`。

## Decisions

### 1. SPI：`streamingRequiresAutoCommitOff()`，默认 false

在已有 `streamingFetchSize()` 旁增加：

```
default boolean streamingRequiresAutoCommitOff() {
    return false;
}
```

`PostgresEngineSupport` 返回 `true`。MYSQL 与 GBase 8A 走默认值：它们的流式靠 `Integer.MIN_VALUE`，关 `autoCommit` 没有收益，还会让还池路径多一次 rollback。

备选是在 `CsvExportService` 里判断 `engine.id()==POSTGRESQL`。那会把游标知识泄漏到 `export` 包，后续引擎无法自描述。备选 `prepareStreaming(Connection)` 更灵活，但本阶段只需要关 `autoCommit`，布尔开关够测、够 ArchUnit。

### 2. 导出里临时关 `autoCommit`，成功则 commit，失败交给还池 rollback

`CsvExportService.stream` 在 `applyNamespace` 之后、`createStatement` 之前：

```
if (engine.streamingRequiresAutoCommitOff()) connection.setAutoCommit(false);
```

随后照旧 `TYPE_FORWARD_ONLY`、`setFetchSize(engine.streamingFetchSize())`、`setMaxRows`。流式写完且未因断开/超限失败时，对这类引擎 `commit()` 再 `setAutoCommit(true)`，使带副作用的 `SELECT`（序列、函数写表）与今天的 autocommit 重放一致。取消、超时、体积超限、SQL 失败不 commit，`finally` 里 `targets.release` → `resetAndClose` 对仍打开的事务 rollback。

不要把这套模式搬到 `SqlExecutionService`：DML 的提交语义必须保持 `autoCommit=true`。

### 3. 有 File System Access 时先选文件再 fetch；否则 Blob 回退

`showSaveFilePicker` 需要用户激活。确认对话框的「继续导出」是手势；`await fetch` 之后再弹选择器通常会失败。因此：

1. 用户确认导出。
2. 若 `showSaveFilePicker` 可用：立即弹出，`suggestedName` 用当前数据源名 + 库/schema（与后端 `{source}-{database}.csv` 同类，非法字符替换掉）。用户取消选择器则不发请求，UI 回到空闲，不当成导出失败。
3. 再 `POST /api/v1/sql/exports`（无自动重试，带 `AbortSignal`）。
4. 用 `response.body` 逐块 `writable.write(chunk)`，写完 `close()`。不把 body 拼成字符串或完整 `Blob`。
5. 若 API 不可用：保持现有 `blob()` + `Content-Disposition` 文件名 + `<a download>`。

Blob 回退仍然受 100 MiB 上限约束。不新增 npm 依赖。

取消：`AbortSignal` 停 fetch；若已打开 writable，调用 `abort()`（或等价丢弃），不留下半截文件当成功下载。离开页签沿用现有 abort。

### 4. 前端文件名：选择器建议名 vs 响应头

流式落盘时用户在选择器里已经定名，忽略 `Content-Disposition`。Blob 回退仍解析响应头，与现在一致。两端建议名允许不完全相同（后端还有 `safeName` 规则），不为此增加预检 API。

## Risks / Trade-offs

- [PG 事务里重放 SELECT，副作用语义变化] → 成功路径显式 `commit()`；失败/取消才 rollback。不在执行 API 上关 autocommit。
- [Firefox / Safari 无 File System Access] → 特征检测后走 Blob；上限仍在。不为此引入 service worker。
- [异步 fetch 之后弹选择器丢失手势] → 必须先选文件再请求。
- [用户选盘后请求失败，留下空文件] → 失败/取消时 `writable.abort()`；选择器取消则根本不请求。
- [连接在 `autoCommit=false` 下还池污染下一个借用者] → 只走现有 `resetAndClose`；单测覆盖成功 commit 恢复与失败 rollback。
- [GBase 8A 尚未进主规格] → 走 SPI 默认 `false`，与 MySQL 相同，不必在本 change 改 GBase delta。

## Migration Plan

1. 后端与前端可独立发布：只发后端则 PG 游标生效、浏览器仍 Blob；只发前端则 Chrome/Edge 流式落盘、PG 驱动仍可能攒行。
2. 无需 Flyway、无需配置项、无需网关改动。
3. 回滚：去掉 SPI 调用即回到今日行为；前端回退到 `blob()` 即可。

## Open Questions

无。Blob 回退范围与「不改上限 / 不改执行事务模型」已在 Non-Goals 钉死。
