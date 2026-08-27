## Context

工作台页签是内存对象，草稿只写 Session Storage（约 200KB），关页签即丢。Monaco 的 `zorth-save` 目前 `emit('notice', '工作表保存暂未开放')`。执行历史已经把 SQL 原文存进元数据库 `MEDIUMTEXT`，按 `user_id` 隔离，但那是审计/回放，不是可改的工作副本。

服务已是元数据 MySQL + Flyway + MyBatis；数据源有 `version` 乐观锁；部署要求应用进程尽量无状态（取消仍绑单实例）。单条 SQL 上限 1MB。身份只从 Bearer 上下文派生。

## Goals / Non-Goals

**Goals:**

- 当前用户私有脚本 CRUD，正文落在元数据库，跨浏览器/机器可打开。
- 页签可绑定 `scriptId`；`Cmd/Ctrl+S` 与工具栏保存走同一 upsert。
- 左侧独立「脚本」工作区，不和执行历史、Copilot 对话混在一起。
- 未保存修改关闭前确认；登出只清本地草稿，服务端脚本保留。
- 连接绑定可空；数据源删除后仍能读正文，详情给 `connectionAvailable=false`。

**Non-Goals:**

- 产品内共享、文件夹、标签、`.sql` 导入导出。
- 按键/定时自动保存。
- 把脚本写入执行历史，或从历史行「另存为脚本」的专用 API（前端仍可用打开历史再保存）。
- 对象存储、NFS、应用机本地文件。
- 实时协作 / CRDT。
- 改授权服务或 Token 契约。

## Decisions

### 决策一：正文进元数据库，不落应用盘

新建 `sql_script`，`statement_text MEDIUMTEXT`。1MB 上限与执行配置 `sql-editor.execution.max-statement-bytes` 对齐。应用机文件在 K8s/多实例下会丢或绑死节点；对象存储对这种体积过重。

考虑过「元数据在库、正文在盘」。放弃：多一个一致性问题和备份面，没有任何体积收益。

### 决策二：按用户隔离，不按产品共享

脚本是个人工作副本，列表/读写/删除一律 `WHERE user_id = 当前用户`。客户端提交的 `userId` 忽略。`product_id` 只做创建时快照（与历史一致），不当授权键。

数据源仍按产品可见性校验：创建/更新若带了 `dataSourceId`，必须是当前产品可见的数据源，否则 `404 DATA_SOURCE_NOT_FOUND`（不泄露）。名称快照写入 `data_source_name`。未绑定连接时 `data_source_id` / `database_name` / `data_source_name` 为空。

### 决策三：独立资源 `/api/v1/sql/scripts`，不塞进 history

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | `/api/v1/sql/scripts` | 当前用户游标分页，`updated_at DESC, id DESC` |
| GET | `/api/v1/sql/scripts/{id}` | 全文 + `connectionAvailable` |
| POST | `/api/v1/sql/scripts` | 创建，`201` + Location |
| PUT | `/api/v1/sql/scripts/{id}` | 带 `version` 覆盖名称/正文/连接 |
| DELETE | `/api/v1/sql/scripts/{id}?version=` | 带 version 删除 |

列表项含 `name`、`statementSummary`（空白折叠后最多 240 字，与历史相同）、连接快照、`version`、`updatedAt`。全文只在详情。关键字最多 200 字，对 `name` 与 `statement_text` 做转义 LIKE。`pageSize` 1–100。游标用现有 `MetadataCursorCodec`，scope 绑定筛选条件。

他人或未知 id：`404 SCRIPT_NOT_FOUND`。乐观锁冲突：`409 VERSION_CONFLICT`，details 含 `currentVersion` / `currentUpdatedAt` / `currentUpdatedByName`（与数据源相同形状）。超配额：`409 SCRIPT_QUOTA_EXCEEDED`。空名称或空正文：`400 VALIDATION_FAILED`。超 1MB：`413 STATEMENT_TOO_LARGE`。

名称允许重复，身份只靠 id（与数据源一致）。同名不提示覆盖、不加后缀。列表靠 `updatedAt`（以及 SQL 摘要、连接快照）区分。配额默认 200，配置 `sql-editor.scripts.max-per-user`。

包名 `com.bocsoft.sqleditor.script`。ArchUnit 编排层规则把 `..script..` 加进「不得依赖具体引擎」名单。

### 决策四：显式保存，不做自动保存

首次保存弹出名称（默认当前页签标题）；已绑定则 PUT，不再问名字。工具栏可「另存为」：POST 一条新脚本并让当前页签改绑到新 id。关闭脏页签才确认；干净的已保存页签直接关。

已保存脚本的重命名是独立动作，页签标题和侧栏都能做：立刻 PUT 新 `name`，正文和连接用**上次成功保存的快照**，不把页签里未保存的 SQL 一并提交。未绑定 `scriptId` 的页签改标题只改本地，第一次保存对话框用它当默认名。重名仍然放行。

自动保存能少丢内容，但会制造大量 `Query N`、放大写放大，也和「关页签即弃草稿」的现语义冲突。需要时另开 change。

Session Storage 继续只恢复同标签刷新：草稿增加 `scriptId` 与 `version`，仍受 200KB 上限；结果、Token、凭据不进草稿。登出/401 清本地，不动服务端。

### 决策五：页签绑定 `scriptId`，侧栏第三项

`EditorTab` 增加 `scriptId: string | null` 与上次成功保存的快照（名称、SQL、连接、version）。已打开同一 `scriptId` 则聚焦，不复制页签。打开时用详情里的连接；`connectionAvailable=false` 时只恢复 SQL 和名称，提示在资源树重选。页签标题双击（或等价编辑）对已绑定脚本走重命名；侧栏列表项也可重命名。

左侧 rail：`database` | `history` | `scripts`。`SCRIPT_MANAGE` 控制脚本图标和保存按钮。执行历史、Copilot 对话列表都不出现在脚本侧栏。

### 决策六：Session 增加 `SCRIPT_MANAGE`

与 `HISTORY_READ` 一样是「本阶段已交付的能力」，写死在 `SessionResponse`，不向授权服务要新权限。前端缺能力时藏侧栏和保存；后端仍鉴权失败为准。

## Risks / Trade-offs

- [用户忘了保存仍会丢] → 脏页签关闭确认；刷新靠 Session Storage。自动保存不在本次。
- [列表 LIKE 扫 MEDIUMTEXT] → 每用户上限 200，关键字有长度限制；不够再加全文索引。
- [两标签页同时改同一脚本] → 乐观锁，前端提示刷新详情后覆盖或另存。
- [脚本含敏感 SQL] → 与历史相同：只进元数据库，不写应用日志；按用户隔离。
- [数据源改名后快照陈旧] → 详情用当前可见性算 `connectionAvailable`；列表展示保存时的名称，可接受。
- [Session Storage 仍可能装不下大脚本] → 服务端是权威；刷新丢未保存大稿与今天 200KB 上限相同。

## Migration Plan

1. Flyway `V5__create_sql_script.sql` 建表。无存量数据。
2. 先发后端（能力 + API），再发前端；旧前端仍显示「保存暂未开放」，直到一并发布。
3. 回滚：停前端入口后 drop 表或保留表不用；能力从 Session 去掉后旧客户端把保存当不可用。

## Open Questions

无。同名放行 + 保存后可重命名已定。共享、文件夹、自动保存若以后需要，另开 change。
