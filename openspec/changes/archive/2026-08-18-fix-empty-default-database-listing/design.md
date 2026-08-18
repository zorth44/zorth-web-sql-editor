## Context

数据源 `defaultDatabase` 可空。前端表单、校验和测连接都按「不钉死某一个库」处理。SQL 编辑器展开数据源时调用 `GET /api/v1/data-sources/{id}/databases`，后端用 `DatabaseMetaData.getCatalogs()`（即 `SHOW DATABASES`）列库，并不按默认库过滤。

还池却无条件执行 `connection.setCatalog(defaultCatalog)`。默认库为空时传入 `null`，MySQL Connector/J 抛 `Catalog can not be null`。该调用写在 `ConnectionUse.execute` 的 `finally` 里，列库已经成功也会被丢掉，接口变成 `422 METADATA_QUERY_FAILED`。测连接路径不会调用 `setCatalog(null)`，所以保存数据源能成功，编辑器里却看不到库。

现有单测 mock 了 `Connection`，集成测试创建数据源时总是带上 Testcontainers 库名，这条路径没有被真实 MySQL 覆盖。

## Goals / Non-Goals

**Goals:**

- 默认库为空时，列库、表/视图和 SQL 执行的成功结果不得被还池失败覆盖。
- 空默认库的列库结果以目标 MySQL 账号可见性为准，默认隐藏系统库。
- 有默认库时，还池仍恢复到该 catalog，避免 `USE` 泄漏到下一个请求。
- 用 mock 和 Testcontainers 锁住空默认库这条路径。

**Non-Goals:**

- 不把默认数据库改成必填，不改数据源 CRUD 契约。
- 不改前端资源树；它已经会 `listDatabases(id)`。
- 不引入 `databaseTerm` / `nullDatabaseMeansCurrent` 等未开放的 JDBC 参数。
- 不实现 Connector/J 做不到的「把 catalog 清回没有数据库」。

## Decisions

### 1. 空默认库时禁止 `setCatalog(null)`

`ConnectionUse.resetAndClose` 仅在 `defaultCatalog` 非空时调用 `setCatalog`。空串与 `null` 同等对待。有默认库时行为不变：rollback、恢复 auto-commit、`setCatalog(default)`、清 warning、close。

备选是捕获 Connector/J 的 null catalog 异常并忽略。拒绝：那会把驱动细节散落到调用方，且 `finally` 里其他失败仍可能盖掉业务结果。

测连接已经按「无库 URL + 仅非空才 setCatalog」工作，业务连接与之对齐。

### 2. 还池失败不得覆盖已成功的业务结果

当前任意还池 `SQLException` 都会从 `finally` 抛出，成功的 `getCatalogs()` 被丢弃。修复后：

- `setCatalog(null)` 根本不发生。
- close / 恢复会话的失败要记录并尽量关闭连接，但不得把已经成功的 metadata/执行结果改写成 `METADATA_QUERY_FAILED`。
- 连接若无法安全还池，丢弃它，不要把它连同失败一起当成业务错误返回。

备选是让 Hikari 在 close 时自己 reset catalog。Hikari 4 在 pool catalog 为 null 时通常不会再 `setCatalog(null)`；真正的调用方是我们自己的 `ConnectionUse`。仍以我方代码为准，不依赖驱动/池的副作用。

### 3. 空默认库且本次切过 catalog 时丢弃连接

Connector/J 不能把 catalog 恢复成「未选择数据库」。请求里 `setCatalog("orders")` 之后，若数据源没有默认库，把连接还回池会留下 `USE`。

规则：重置时若默认库为空，且 `getCatalog()` 非空，则关闭并逐出该连接，而不是还池。列库本身不切 catalog，不受影响。表结构、执行、导出切过库的连接被丢掉，用新连接补上。空默认库场景下连接更短命，可接受。

备选是每次借出都 `USE` 目标库、容忍池里残留 catalog。拒绝：`SELECT DATABASE()` 或未指定库的语句会看到上一次的库。

### 4. 列库语义保持「账号可见的非系统库」

不因为默认库为空就只返回空列表，也不改成只返回默认库。`includeSystem=false` 时仍隐藏 `information_schema`、`performance_schema`、`mysql`、`sys`。目标账号若本来只能看到系统库，树里仍会是空的；这不是本次 bug。

### 5. 用真实 MySQL 锁回归

`ConnectionUse` 单测覆盖：默认库为 `orders` 时仍 `setCatalog("orders")`；为 null/空串时永不 `setCatalog`；work 已成功时还池异常不得改变返回值。

Testcontainers 增加：创建一个 `defaultDatabase` 为 null 的数据源，`GET /databases` 返回 200，且 `items` 包含该账号可见的用户库（至少 Testcontainers 自己的库），而不是 `METADATA_QUERY_FAILED`。

## Risks / Trade-offs

- [空默认库下切过 catalog 的连接被丢弃，池命中率下降] → 只影响未填默认库的数据源；池本就 `minimumIdle=0`。需要长连接时填默认库。
- [Hikari close 仍可能对 dirty catalog 做 reset] → 实现后用空默认库列库/列表回归验证；若 close 仍抛 null catalog，改为先逐出再 close，且不把该异常映射为业务失败。
- [账号没有任何用户库时树仍为空] → 保持现有系统库过滤；与本次 bug 无关，不在范围里放宽。

## Migration Plan

无需数据迁移。部署新版本后，已保存的空默认库数据源展开即可列出可见库。回滚会回到 `setCatalog(null)` 失败。

文档 `docs/backend-development-spec.md` 中「若为空则设为 null」改为：空默认库不调用 `setCatalog`；若会话 catalog 已被切换则丢弃连接。

## Open Questions

无。测连接已经给出可执行的空默认库语义。
