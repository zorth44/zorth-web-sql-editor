## Why

第一阶段已经交付认证、会话和产品隔离的数据源管理，但用户尚不能在可见数据源上浏览元数据、执行 SQL、查看结果、导出或回看历史。第二阶段需要在不改变冻结数据源 API 的前提下补齐完整 SQL 编辑器链路，并用本地临时授权服务替代当前缺失的外部依赖，使仓库可独立开发和验收。

## What Changes

- 为 SQL 服务增加产品隔离的数据库、表/视图、字段、主键和索引元数据 API。
- 增加单语句异步执行、行数/体积/超时/并发限制、结果类型编码、运行注册与尽力取消。
- 增加当前用户执行历史、游标分页和详情，以及从成功查询历史重新执行的流式 CSV 导出。
- 将 Session 能力扩展为 `SQL_EXECUTE`、`SQL_EXPORT` 和 `HISTORY_READ`。
- 交付 Vue SQL 编辑器工作台，包括连接与资源树、SQL 页签、Monaco 编辑、执行/取消、结果表、CSV 下载和历史恢复。
- 将登录后默认入口切换到 `/sql-editor`，并保留数据源管理入口。
- 在仓库内增加仅供本地开发的临时 `auth-service`，实现 LDAP 登录、退出和内部授权上下文假接口。
- 增加后端、前端和本地串联验证，覆盖隔离、单语句、数据编码、CSV 安全、执行历史和核心编辑器流程。

## Capabilities

### New Capabilities

- `backend-sql-editor`: 目标 MySQL 元数据、受限单语句执行、取消、结果编码和安全错误契约。
- `backend-sql-history-export`: 当前用户执行历史、查询重放和流式 CSV 导出契约。
- `frontend-sql-editor-workbench`: SQL 编辑器工作台、连接资源、页签、执行结果、导出和历史交互。
- `local-auth-service`: 本地开发授权假服务的登录、退出和内部上下文契约。

### Modified Capabilities

- `backend-auth-session`: 第二阶段 Session 增加 SQL 执行、导出和历史能力。
- `frontend-application-shell`: 启用 SQL 编辑器路由并将登录后默认入口切换到编辑器。
- `frontend-auth-session`: 登录与 Token 桥接成功后的默认落点切换到 SQL 编辑器，并按第二阶段能力控制功能。

## Impact

- 后端：`service/` 新增 Flyway 历史表、MyBatis 持久层、metadata/execution/export/history 包、异步执行配置和运行指标。
- 前端：`web/` 新增 Monaco 及编辑器状态依赖、API 契约、工作台组件、路由、Mock 和测试；保留第一阶段数据源页面。
- 本地开发：新增 `auth-service/`，并补充可复现的启动配置/说明；该服务不用于生产。
- 外部系统：继续兼容 `bddf-authorization-service` 已冻结的 Token 契约，不依赖其内部代码或存储。
