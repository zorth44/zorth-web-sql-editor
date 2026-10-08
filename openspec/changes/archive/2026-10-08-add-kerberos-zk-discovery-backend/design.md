## Context

现有连接模型是「单 host → `NetworkPolicy.resolve(host)`（CIDR 白名单）→ `DynamicPoolManager`（按数据源 id 建 Hikari 池）」，凭据是 AES 加密密码且 `sql_data_source` 的 `host/port/username/password_*` 全 `NOT NULL`（`V1__create_sql_data_source.sql`）。连接获取收口在 `TargetConnectionProvider.borrow(SavedDataSource)`，被 `MetadataService#jdbc`、`SqlExecutionService#run`、`CsvExportService`、`ExplainService` 调用；未保存配置的连接测试走 `ShortLivedConnectionTester`。

参考实现 `bddf-public-service` 的 Kerberos 连接是 `TBDSSecurityHandle`：`init()` 读 keytab/krb5.conf、设 `java.security.krb5.conf`、`useSubjectCredsOnly=false`、刷新 krb5、设默认 realm、`UserGroupInformation.setConfiguration` + `loginUserFromKeytab`、设 `zookeeper.server.principal`；再拼 `jdbc:hive2://<zk>/<db>;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2;principal=hadoop/_HOST@<REALM>`，`DriverManager.getConnection(url,"","")`；无库名变体在 `finally` 里 `UserGroupInformation.reset()`。环境(dev/func/pro)→zk quorum 与 krb5.conf 来自 `application.yml`，keytab 放服务器 `keytab_file_path/{hive531|iceberg}/`。

约束不变：Java 8、Spring Boot 2.7、单实例。服务元数据库仍是 Flyway 管理的 MySQL（最新到 `V6`）。前端消费面独立成 `add-kerberos-zk-discovery-frontend`。

## Goals / Non-Goals

**Goals:**

- 交付可复用的 **Kerberos + ZooKeeper 服务发现**连接机制，并用 `HIVE_KERBEROS`（dbType 8）验证。
- 每次操作 **加锁 → 登录 → 建连接 → 解锁 → 执行 → 关闭**；`finally` 保证 JVM 全局状态（系统属性、UGI、默认 realm）被还原。
- **不破坏 `DynamicPoolManager`**：MYSQL/POSTGRESQL/GBASE_8A/HIVE 仍走池，行为完全不变。
- 校验由引擎描述符驱动：Kerberos 引擎无需 host/username/password/sslMode。
- Kerberos 凭据（keytab 路径、realm、zk quorum、krb5.conf）服务端化并脱敏，不进数据源响应/日志。
- zk quorum 端点仍做 CIDR 校验。
- **未配置 `sql-editor.kerberos` 时功能关闭、服务照常启动**；只有配置存在但缺字段/路径非法才 fail startup。
- **把「登录+建连」收敛为薄适配层**，让 URL/状态还原/校验/分类等可在无集群、无 KDC、无厂商 jar 时单测。
- **明确无集群的验证边界**：本地 CI 只证明失败路径与纯逻辑，真实登录/ZK 发现/驱动兼容留现场手动联调。

**Non-Goals:**

- 不注册 `ICEBERG`（独立 change `add-iceberg-engine`）。
- 不做前端表单/MSW/图标（独立 change `add-kerberos-zk-discovery-frontend`）。
- 不新增 keytab 上传接口（keytab 由运维放服务器磁盘）。
- 不为 Kerberos 引擎建长连接池。
- 不改 `EngineSupport` 的既有方法签名（只加默认方法）。
- 不在数据源表存 principal/realm/zk quorum（只存环境名 + keytab 文件名 + queue）。
- **不在本地搭建真实 KDC/HiveServer2/ZooKeeper 做端到端自动化**（真实集群联调为现场手动步骤）。

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

`sql-editor.kerberos`：`keytab-base-path` 与 `environments.<env>.{zookeeper-quorum, krb5-conf, realm, principal-template, zookeeper-namespace}`。数据源保存 `environment` + `keytabFile`（服务器上的文件名）+ `queueName`。keytab 实际路径 = `keytab-base-path/<engine-subdir>/<keytabFile>`（`hive531` for HIVE_KERBEROS）。

配置校验分两态，互不混淆：

- **完全缺省 = 功能关闭**：`sql-editor.kerberos` 整体不存在（本地开发/未上线）时服务正常启动，`HIVE_KERBEROS` 仍在目录中，连接请求返回脱敏 `CONNECTION_FAILED`（"Kerberos 未配置"），不影响既有引擎。
- **存在但非法 = fail startup**：一旦配置了 `kerberos`，则 `keytab-base-path` 非空、每个 environment 的 `realm`/`zookeeper-quorum` 必填且路径存在，否则启动失败。

### 5. 校验改为描述符驱动

`DataSourceValidator` 不再硬编码 host/username/password/sslMode；改为遍历 `engine.descriptor().connectionFields`：`required` 的字段必须非空，`defaultDatabase` 走 `defaultNamespaceRequired()`，`MAXLENGTH` 与端口范围按描述符。Kerberos 引擎因此不要求密码。`ConnectionRequest` 增 `environment/keytabFile/queueName`（可选），`ConnectionConfiguration` 携带它们。

### 6. 持久化：放宽 NOT NULL + 显式列

Flyway `V7__hive_kerberos_connection.sql`：把 `host/port/username/password_ciphertext/password_iv/key_version` 改成可空，新增 `environment varchar(32) NULL`、`keytab_file varchar(255) NULL`、`queue_name varchar(128) NULL`。用显式列而非 JSON，便于查询与脱敏白名单。

### 7. 脱敏与 CIDR 边界

`keytab_file`/realm/zk quorum/krb5.conf 不进 `DataSourceListItemResponse`/`detail`，不进日志；失败分类把 Kerberos/krb5/SASL/GSS 异常映射到 `AUTHENTICATION_FAILED`/`CONNECTION_FAILED` 并脱敏。CIDR 对**配置的 zk quorum 端点**校验；ZK 动态发现的 HiveServer2 地址无法预检——这是已知取舍，记入本文件并将在文档说明。

### 8. 厂商依赖走 drop-in + 条件源码根 + 桩，公共构建不引入不可解析坐标

参考实现 `bddf-public-service` 用厂商定制坐标（`org.apache.hadoop:hadoop-common:3.2.2-TBDS-5.3.1.3`、`hadoop-auth:3.2.2-TBDS-5.3.1.3`、`org.apache.hive:hive-jdbc:3.1.3-TBDS-5.3.1.3:standalone`、`hive-shims`），这些 `-TBDS-` 构件不在 Maven Central（本地 `~/.m2` 仅有失败的 `.lastUpdated` 标记），且体积大、无法从内网取出。因此采用**按文件存在自动激活的 profile + 条件源码根**，让「依赖厂商类的真实现」只在 jar 在位时参与编译：

- **drop-in**：厂商 jar 放 `service/third-party/hive-auth/`，`.gitignore` 忽略；profile 按文件存在自动激活并以 system-scope 引入（与现有 `gbase`/`hive-official-jdbc` profile 同构）。
- **条件源码根**：真实现 `service/src/kerberos/java/.../hive_kerberos/vendor/TbdsKerberosHiveConnector.java` **照抄** `bddf-public-service` 逻辑（直接 `import org.apache.hadoop.*`），仅在 profile 下经 `build-helper-maven-plugin` 加入源码根。默认构建（开发机无 jar）**根本不编译它**。
- **始终编译的接口 + 桩**：`KerberosHiveConnector`（零厂商 import）与 `DisabledKerberosHiveConnector`（连接即返回脱敏 `CONNECTION_FAILED`）。
- **工厂单点反射选择实现**：`KerberosConnectorFactory` 用 `Class.forName("...vendor.TbdsKerberosHiveConnector")`，存在则实例化，`ClassNotFoundException` 时回退桩。这是全工程**唯一**的反射点。
- **方案 A（本变更采用）**：桩只替换连接器；`HIVE_KERBEROS` 引擎、描述符、字段种类、校验仍在，目录始终 5 项，开发机可验目录/表单/校验/持久化，仅真实连接不可用。

结果：开发机无 jar 也能 `mvn package`、启动、跑无集群单测；内网有 jar 时自动编译并打包真实现（`spring-boot-maven-plugin` 已配 `includeSystemScope=true`）。jar 清单见下节 "Required vendor artifacts"。

### 9. 登录+建连收敛为薄适配层，最大化无集群可测性

唯一必须真实 KDC/集群的窗口是「登录 + 建连 + 关闭」，用一个薄接口（如 `KerberosSession`/`KerberosLoginCallback`）包住，连接器其余部分只做纯逻辑：

- **可无集群单测**：URL 组装（带/不带 queue）、realm/principal 推导（`KerberosName` 为字符串逻辑）、系统属性快照/还原（成功与异常）、zk quorum CIDR 校验、失败分类（喂合成异常）、环境解析与 keytab 路径拼接。
- **必须现场验证**：`loginUserFromKeytab` 真登录、ZK 服务发现解析 HS2 地址、驱动与现场 TBDS 3.2.2 的兼容、`_HOST` 替换、票据行为。
- 集成测试只覆盖**失败路径**（缺 keytab/driver → 脱敏 `CONNECTION_FAILED`）与「jar 在位但无 Kerberos 数据源时应用上下文正常启动、既有引擎不受影响」。

## Required vendor artifacts

按用途分组（坐标为参考实现使用值，交付时以现场 TBDS 版本为准）：

**条件源码根直接引用（编译期，仅 `kerberos-vendor` profile 下编译）**

- `org.apache.hadoop:hadoop-common:3.2.2-TBDS-5.3.1.3` — `org.apache.hadoop.conf.Configuration`、`org.apache.hadoop.security.UserGroupInformation`
- `org.apache.hadoop:hadoop-auth:3.2.2-TBDS-5.3.1.3` — `org.apache.hadoop.security.authentication.util.KerberosName`
- `org.apache.hive:hive-jdbc:3.1.3-TBDS-5.3.1.3:standalone` — `org.apache.hive.jdbc.HiveDriver`、ZK 服务发现
- `org.apache.zookeeper:zookeeper:3.4.6` — ZK 客户端（若 standalone fat jar 未内嵌）

**运行时需要（hadoop-common / hive-jdbc 的传递依赖，缺失会在首次登录时报 `NoClassDefFoundError`）**

- `org.apache.hive:hive-shims:3.1.3-TBDS-5.3.1.3`
- `org.apache.commons:commons-configuration2:2.1.1`（参考实现显式声明）
- 其余传递依赖（guava、commons-lang3/collections/logging、woodstox/stax2、jackson 等）建议直接取参考工程 `mvn dependency:copy-dependencies` 的完整集合，避免手工挑版本。

> 首选做法：向厂商/现场要一份**可用的 Maven 仓库（Nexus/私服）或参考工程解析出的完整依赖目录**，整体 drop-in；不要逐个手工拼版本。若只能拿到零星 jar，则至少保证上表「编译期直接引用」四个 + `hive-shims` 齐全，并在现场用真实数据源验证传递依赖是否完整。

## Risks / Trade-offs

- [全局 Kerberos 状态被并发污染] → 单锁串行 + `finally` 还原；测试覆盖异常路径也还原。
- [hadoop-common/hadoop-auth 依赖重、与 Spring Boot 冲突] → 条件源码根 + 桩，厂商类只在 profile 的真实现里出现；默认构建不引入不可解析坐标，jar 缺失时仍可构建/启动（决策 8）。
- [厂商 `-TBDS-` 构件本地不可解析/体积大无法取出] → 只做 drop-in + 条件源码根，不写进默认可解析依赖；真实现照抄参考工程，开发机完全不编译它。提供明确 jar 清单（见 "Required vendor artifacts"）。
- [本地无 TBDS 集群，真实登录/ZK 发现/驱动兼容不可验] → 薄适配层隔离（决策 9），本地只验失败路径与纯逻辑；真实端到端列为**现场手动联调**，CI 全绿不等于现场可用。
- [Hadoop 类静态初始化带来全局副作用] → 加「jar 在位、无 Kerberos 数据源时上下文正常启动且既有引擎不受影响」的测试。
- [放宽 NOT NULL 影响既有引擎] → 校验层按描述符拒绝缺失；既有引擎仍要求非空，数据库层放宽不影响其语义。
- [ZK 发现的 HS2 地址绕过 CIDR] → 明确记录取舍；zk quorum 仍受 CIDR 约束。
- [短连接无池导致连接开销] → 可接受（Kerberos 场景连接频率低）；如需优化可后续单独提案。

## Migration Plan

1. 取得厂商依赖 jar（见 "Required vendor artifacts"）放入 drop-in 目录；本地/CI 未放 jar 时仍可构建、启动、跑无集群单测。
2. 运维放置 keytab 文件与 krb5.conf，配置 `sql-editor.kerberos`（dev/func/pro）。**未配置时服务照常启动**（功能关闭），Kerberos 数据源连接失败并脱敏提示。
3. 部署 `V7` Flyway：放宽列 + 新增列，对既有行无损。
4. 后端先发：目录多出第 5 项 `HIVE_KERBEROS`。旧前端忽略未知引擎即可；前端渲染与提交流程由 `add-kerberos-zk-discovery-frontend` 独立落地。
5. **现场手动联调（不在 CI）**：用真实 TBDS Hive 数据源验证 keytab 登录、ZK 服务发现、查询/元数据、`queue` 生效、错误脱敏。
6. 回滚：回退应用版本。已存 `engine=HIVE_KERBEROS` 的行在旧版本 `ENGINE_NOT_SUPPORTED`（fail-closed）。`V7` 为向前兼容的放宽，回滚应用不需回滚 DB。
