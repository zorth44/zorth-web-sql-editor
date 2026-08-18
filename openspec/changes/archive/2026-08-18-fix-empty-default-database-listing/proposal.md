## Why

数据源的默认数据库可以留空，产品语义是不钉死某一个库，SQL 编辑器展开该数据源时应列出目标账号可见的全部非系统库。当前还池逻辑会在默认库为空时调用 `setCatalog(null)`，MySQL Connector/J 拒绝该调用，成功列出的库在 `finally` 里被丢掉，编辑器里就像没有数据库。

## What Changes

- 默认数据库为空时，还池不再调用 `setCatalog(null)`；连接测试已经按这个规则工作，业务连接与之对齐。
- 默认数据库为空的数据源，`GET /api/v1/data-sources/{id}/databases` 必须返回该 MySQL 账号可见的非系统库，而不是 `METADATA_QUERY_FAILED` 或空列表。
- 若请求中切换过 catalog，而数据源没有默认库，还池时不得用 null catalog 重置；丢弃该连接，避免污染下一个请求。
- 补真实 MySQL 覆盖：创建不带默认库的数据源后，列库接口成功并包含用户库。
- 不改前端表单、不改数据源 API 契约、不把默认库改成必填。

## Capabilities

### New Capabilities

- （无）本变更只修正已有连接还池与元数据列库行为。

### Modified Capabilities

- `backend-connection-security`: 还池恢复 catalog 时，空默认库不得调用 `setCatalog(null)`；必要时丢弃连接而不是把 catalog 置空。
- `backend-sql-editor`: 明确默认数据库为空时仍按目标账号可见性列出非系统库。

## Impact

- 后端：`ConnectionUse` 还池、`MysqlMetadataService` 列库所用的默认 catalog、以及可能的池连接丢弃路径。
- API：不改请求/响应形状；修复空默认库下列库从失败变为成功。
- 文档：`docs/backend-development-spec.md` 中「为空则 setCatalog(null)」需改成与 Connector/J 一致的规则。
- 测试：`ConnectionUse` 单测覆盖 null catalog；Testcontainers 集成测试覆盖空默认库列库。
- 前端：不改资源树。默认库为空时本来就会 `listDatabases(id)`，后端修好后树即可显示。
