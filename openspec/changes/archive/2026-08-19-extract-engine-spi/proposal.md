## Why

目标库方言（JDBC URL、SSL、属性白名单、失败分类、catalog 复位、元数据、SQL 扫描）写死在主干类里，`engine` 落库后运行时被丢掉。后面还要加 PostgreSQL、Hive、GBase，现在抽插件面，避免第二种库开始在主干堆分支。

## What Changes

- 新增后端 `EngineSupport` 插件契约和注册表；本变更只注册 `MYSQL`，MySQL 的连接、测试、元数据、扫描、失败分类行为与现在一致。
- `ConnectionConfiguration` 携带 `engine`；校验、拼 JDBC、借池、连接测试、执行、导出都通过注册表解析引擎，不再在主干里写死 `jdbc:mysql://`。
- 数据源 `engine` 必须是已注册引擎；当前注册表只有 `MYSQL`，因此对外仍只接受 `MYSQL`。
- **BREAKING**：执行失败详情和历史详情把 `mysqlErrorCode` 改成 `vendorErrorCode`；历史表列 `mysql_error_code` 迁移为 `vendor_error_code`。前端契约与 Mock 同步改名。
- 不增加 PostgreSQL / Hive / GBase，不改数据源表单（类型仍只读 MySQL），不改资源树契约（仍是 databases → tables），不加 `GET /api/v1/engines`。

## Capabilities

### New Capabilities

- `backend-engine-spi`: 引擎注册表、`EngineSupport` 职责边界、运行时按 `engine` 分发、以及「未注册引擎拒绝写入」的合同。本阶段唯一实现是 MySQL。

### Modified Capabilities

- `backend-data-source-management`: 创建/更新时 `engine` 必须是注册表中的引擎；当前仅 `MYSQL` 可注册成功。
- `backend-connection-security`: JDBC URL、属性白名单、SSL、连接失败分类、会话复位由选中的引擎提供；MySQL 实现保持现有 Connector/J 规则。
- `backend-sql-editor`: 元数据浏览、单语句扫描、执行失败码通过引擎提供；失败详情字段改为 `vendorErrorCode`。
- `backend-sql-history-export`: 历史持久化与详情使用 `vendorErrorCode`。
- `frontend-sql-editor-workbench`: 展示与类型使用 `vendorErrorCode`；编辑器语言本阶段仍为 MySQL。

## Impact

- 后端：`datasource.connection`、`metadata`、`execution`、`history`、Flyway、ArchUnit；新增 `engine` 包。
- API：数据源 CRUD 形状不变；执行/历史 JSON 字段 `mysqlErrorCode` → `vendorErrorCode`。
- 前端：`contracts.ts`、Mock、历史/错误展示。
- 文档：`docs/backend-development-spec.md` 中 JDBC 构建与错误码字段。
- 不改授权服务、元数据库引擎（仍是 MySQL）、目标网段策略、连接池容量。
