## Context

现有连接模型是「单 host → `NetworkPolicy.resolve(host)`（CIDR 白名单）→ `DynamicPoolManager`（按数据源 id 建 Hikari 池）」，凭据是 AES 加密密码且 `sql_data_source` 的 `host/port/username/password_*` 全 `NOT NULL`（`V1__create_sql_data_source.sql`）。连接获取收口在 `TargetConnectionProvider.borrow(SavedDataSource)`，被 `MetadataService#jdbc`、`SqlExecutionService#run`、`CsvExportService`、`ExplainService` 调用；未保存配置的连接测试走 `ShortLivedConnectionTester`。

参考实现 `bddf-public-service` 的 Kerberos 连接是 `TBDSSecurityHandle`：`init()` 读 keytab/krb5.conf、设 `java.security.krb5.conf`、`useSubjectCredsOnly=false`、刷新 krb5、设默认 realm、`UserGroupInformation.setConfiguration` + `loginUserFromKeytab`、设 `zookeeper.server.principal`；再拼 `jdbc:hive2://<zk>/<db>;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2;principal=hadoop/_HOST@<REALM>`，`DriverManager.getConnection(url,"","")`；无库名变体在 `finally` 里 `UserGroupInformation.reset()`。环境(dev/func/pro)→zk quorum 与 krb5.conf 来自 `application.yml`，keytab 放服务器 `keytab_file_path/{hive531|iceberg}/`。

约束不变：Java 8、Spring Boot 2.7、单实例。服务元数据库仍是 Flyway 管理的 MySQL（最新到 `V6`）。

## Goals / Non-Goals

**Goals:**

- 交付可复用的 **Kerberos + ZooKeeper 服务发现**连接机制，并用 `HIVE_KERBEROS`（dbType 8）验证。
- 每次操作 **加锁 → 登录 → 建连接 → 解锁 → 执行 → 关闭**；`finally` 保证 JVM 全局状态（系统属性、UGI、默认 realm）被还原。
- **不破坏 `DynamicPoolManager`**：MYSQL/POSTGRESQL/GBASE_8A/HIVE 仍走池，行为完全不变。
- 校验由引擎描述符驱动：Kerberos 引擎无需 host/username/password/sslMode。
- Kerberos 凭据（keytab 路径、realm、zk quorum、krb5.conf）服务端化并脱敏，不进数据源响应/日志。
- zk quorum 端点仍做 CIDR 校验。

**Non-Goals:**

- 不注册 `ICEBERG`（独立 change `add-iceberg-engine`）。
- 不新增 keytab 上传接口（keytab 由运维放服务器磁盘）。
- 不为 Kerberos 引擎建长连接池。
- 不改 `EngineSupport` 的既有方法签名（只加默认方法）。
- 不在数据源表存 principal/realm/zk quorum（只存环境名 + keytab 文件名 + queue）。

## Decisions

### 1. 用 SPI 默认方法表达「连接获取模型」，现有引擎零改动

| 能力 | 默认（池化引擎） | Kerberos 引擎 |
| --- | --- | --- |
| `requiresHostResolution()` | true | false（无单 host） |
| `usesPooledConnections()` | true | false |
| `openConnection(JdbcTarget)` | `DriverManager.getConnection(url, props)` | 锁内登录 + 建连 |

`TargetConnectionProvider` 与 `ShortLivedConnectionTester` 改为经 `engine.openConnection(...)` 建连；`JdbcConfigurationBuilder` 在 `requiresHostResolution()==false` 时不再 `networkPolicy.resolve(host)`。这样池化路径拼装与行为等价，主干无 `if (HIVE_KERBEROS)`。

### 2. Kerberos 引擎走短连接，池实现不动

`TargetConnectionProvider.borrow` 按 `usesPooledConnections()` 分派：true → `pools.borrow(...)`（原样）；false → `engine.openConnection(builder.build(config))`（短连接）。`release` 对短连接直接关，`evictor` 对短连接 no-op。**`DynamicPoolManager` 一行不改**，只加 `usesPooledConnections` 分派。

> 放弃长连接池的原因：`UserGroupInformation` / `KerberosName` 默认 realm / `java.security.krb5.conf` / `zookeeper.server.principal` 都是 **JVM 全局**，同进程多数据源、多 realm 会互相覆盖；池化后票据过期/刷新边界不可控。

### 3. 全局 Kerberos 状态用单锁串行 + 快照还原

`KerberosHiveConnector` 持有单一 `ReentrantLock`：锁内快照关键系统属性 → 设置 → 登录 → 建连 → 解锁；`finally` 还原系统属性并 `UserGroupInformation.reset()`。任何异常路径也必须还原。这样并发请求会串行化 Kerberos 阶段（可接受：只在「登录 + 建连」窗口内串行，执行阶段并行）。

### 4. 服务端环境配置承载 zk / krb5 / realm，数据源只存引用

`sql-editor.kerberos`：`keytab-base-path` 与 `environments.<env>.{zookeeper-quorum, krb5-conf, realm, principal-template, zookeeper-namespace}`。数据源保存 `environment` + `keytabFile`（服务器上的文件名）+ `queueName`。keytab 实际路径 = `keytab-base-path/<engine-subdir>/<keytabFile>`（`hive531` for HIVE_KERBEROS）。`@PostConstruct` 校验非空/路径存在。

### 5. 校验改为描述符驱动

`DataSourceValidator` 不再硬编码 host/username/password/sslMode；改为遍历 `engine.descriptor().connectionFields`：`required` 的字段必须非空，`defaultDatabase` 走 `defaultNamespaceRequired()`，`MAXLENGTH` 与端口范围按描述符。Kerberos 引擎因此不要求密码。`ConnectionRequest` 增 `environment/keytabFile/queueName`（可选），`ConnectionConfiguration` 携带它们（或统一 `connectionExtras`）。

### 6. 持久化：放宽 NOT NULL + 显式列

Flyway `V7__hive_kerberos_connection.sql`：把 `host/port/username/password_ciphertext/password_iv/key_version` 改成可空，新增 `environment varchar(32) NULL`、`keytab_file varchar(255) NULL`、`queue_name varchar(128) NULL`。用显式列而非 JSON，便于查询与脱敏白名单。

### 7. 脱敏与 CIDR 边界

`keytab_file`/realm/zk quorum/krb5.conf 不进 `DataSourceListItemResponse`/`detail`，不进日志；失败分类把 Kerberos/krb5/SASL/GSS 异常映射到 `AUTHENTICATION_FAILED`/`CONNECTION_FAILED` 并脱敏。CIDR 对**配置的 zk quorum 端点**校验；ZK 动态发现的 HiveServer2 地址无法预检——这是已知取舍，记入本文件并将在文档说明。

## Risks / Trade-offs

- [全局 Kerberos 状态被并发污染] → 单锁串行 + `finally` 还原；测试覆盖异常路径也还原。
- [hadoop-common/hadoop-auth 依赖重、与 Spring Boot 冲突] → 沿用 drop-in（参考同款 JAR）优先 + 钉版本 + exclusion；change 1 已先引入 hive-jdbc，本变更只加 auth 相关。
- [放宽 NOT NULL 影响既有引擎] → 校验层按描述符拒绝缺失；既有引擎仍要求非空，数据库层放宽不影响其语义。
- [ZK 发现的 HS2 地址绕过 CIDR] → 明确记录取舍；zk quorum 仍受 CIDR 约束。
- [短连接无池导致连接开销] → 可接受（Kerberos 场景连接频率低）；如需优化可后续单独提案。

## Migration Plan

1. 运维放置 keytab 文件与 krb5.conf，配置 `sql-editor.kerberos`（dev/func/pro）。未配置时服务仍启动，但 Kerberos 数据源连接失败并脱敏提示。
2. 部署 `V7` Flyway：放宽列 + 新增列，对既有行无损。
3. 前后端同发：目录第 5 项 `HIVE_KERBEROS`、Kerberos 表单字段。
4. 回滚：回退应用版本。已存 `engine=HIVE_KERBEROS` 的行在旧版本 `ENGINE_NOT_SUPPORTED`（fail-closed）。`V7` 为向前兼容的放宽，回滚应用不需回滚 DB。
