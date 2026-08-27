## Why

SQL 页签目前只把草稿写进 Session Storage；关页签、关浏览器或换机器后工作副本就丢了，`Cmd/Ctrl+S` 也只提示「工作表保存暂未开放」。执行历史只保留跑过的语句，不能当可改的脚本库。用户需要一份跨会话、按本人隔离的 SQL 脚本，显式保存后能再打开。

## What Changes

- 在元数据 MySQL 增加当前用户私有的 SQL 脚本表，正文以 `MEDIUMTEXT` 落库，不写应用服务器本地文件，也不引入对象存储。
- 新增脚本 CRUD API：列表/详情/创建/更新/删除，按 `user_id` 隔离，连接绑定可空，乐观锁与数据源同一套 `version`。
- Session 增加 `SCRIPT_MANAGE` 能力；前端按该能力露出脚本侧栏和保存操作。
- 工作台左侧在「数据库 / 执行历史」旁增加「脚本」：搜索、打开、重命名、删除；打开后进入绑定了 `scriptId` 的 SQL 页签。同名脚本允许共存，列表用更新时间区分。
- `Cmd/Ctrl+S` 与工具栏保存改为真正 upsert；首次保存询问名称，已绑定脚本则带 version 覆盖。首次保存之后可从页签标题或脚本侧栏重命名（立刻改服务端 `name`，不顺带提交未保存的 SQL）。
- 未保存修改的页签关闭前确认；已保存且无改动的页签可直接关闭。Session Storage 草稿仍只做同标签刷新恢复，登出仍清空本地草稿，服务端脚本保留。

不包含：产品内共享、文件夹、`.sql` 导入导出、按键自动保存、实时协作、把脚本混进执行历史。

## Capabilities

### New Capabilities

- `backend-sql-scripts`: 当前用户 SQL 脚本在元数据库中的持久化、隔离、校验、乐观锁和 CRUD 契约。

### Modified Capabilities

- `frontend-sql-editor-workbench`: 脚本侧栏、保存/另存/重命名、页签与 `scriptId` 绑定、脏状态关闭确认；`Cmd/Ctrl+S` 不再声明 persistence 不可用。
- `backend-auth-session`: Session 能力集增加 `SCRIPT_MANAGE`。
- `frontend-auth-session`: 按 `SCRIPT_MANAGE` 显隐脚本与保存操作。

## Impact

- 后端：`service/` 新增 Flyway 脚本表、`script` 包（Controller/Service/MyBatis）、Session 能力、配额与语句体积校验；ArchUnit 编排层规则需纳入新包。
- 前端：`web/` 新增脚本 API/契约/MSW、侧栏、保存/重命名、editor store 的 `scriptId`/脏标记；Monaco 保存动作改为真正保存。
- 部署：无新中间件；备份与现有元数据库相同。SQL 原文只进受控元数据库，不写应用日志。
- 外部系统：不改授权服务；身份仍只从 Bearer 上下文派生。
