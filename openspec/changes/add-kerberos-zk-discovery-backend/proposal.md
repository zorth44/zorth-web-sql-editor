## Why

现场第二种连接方式是 **Kerberos + ZooKeeper 服务发现**（参考实现 `bddf-public-service`（项目位于：/Users/zorth/Code/bddf/bddf-public-service）的 `Constants.DBTYPE_HIVE_531 = "8"`，走 `TBDSSecurityHandle` + `KerberosUtil`）：用 keytab 登录 (`UserGroupInformation.loginUserFromKeytab`)、设置 `java.security.krb5.conf` 与 `zookeeper.server.principal`、按环境选 zk quorum/krb5.conf/realm，再拼 `jdbc:hive2://<zkQuorum>/<db>;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2;principal=hadoop/_HOST@<REALM>`。

它和本项目现有连接模型**全面冲突**：没有 host/port/username/password（用的是 keytab 文件）、连接目标是 ZK 动态发现的、Kerberos 登录是 **JVM 全局状态**（UGI/realm/krb5.conf）、`sql_data_source` 的 host/username/password 都是 `NOT NULL`、`DataSourceValidator` 硬编码要求用户名/密码、`MetadataService`/`SqlExecutionService` 都经 `DynamicPoolManager` 长连接池。因此后端必须作为一次**架构级扩展**整体落地，并用一个引擎（`HIVE_KERBEROS`，dbType 8）验证。

本变更不改池：**MYSQL / POSTGRESQL / GBASE_8A / HIVE 仍走现有 Hikari 池，行为完全不变**。前端消费面拆到 `add-kerberos-zk-discovery-frontend`，本 change 只做后端。

## What Changes

- **SPI 扩展（默认兼容）**：`EngineSupport` 增默认方法 `requiresHostResolution()`、`usesPooledConnections()`、`openConnection(JdbcTarget)`；新增字段类型 `ENVIRONMENT`/`KEYTAB`/`QUEUE`。现有引擎零改动。
- **Kerberos + ZooKeeper 连接机制**：新增 `KerberosHiveConnector`（移植 `TBDSSecurityHandle`/`KerberosUtil`），进程内**单锁**串行化全局状态；每次操作 **加锁 → 登录 → 建连接 → 解锁 → 执行 → 关闭**，`finally` 保证系统属性与 UGI 被还原。对 zk quorum 端点复用 `NetworkPolicy` 做 CIDR 校验。
- **服务端配置**：`sql-editor.kerberos`（`keytab-base-path` + `environments.<env>.{zookeeper-quorum, krb5-conf, realm, principal-template, zookeeper-namespace}`）；keytab 文件放服务器磁盘，数据源只存「环境 + keytab 文件名 + queue」。
- **持久化/校验**：Flyway `V7` 放宽 `host/port/username/password_*` 为 NULL，新增 `environment`/`keytab_file`/`queue_name` 列；`DataSourceValidator` 改为**按引擎描述符驱动**的必填校验（Kerberos 引擎不要求 host/username/password/sslMode）。
- **连接获取收口**：`TargetConnectionProvider` 按 `usesPooledConnections()` 分派——池化引擎走 `DynamicPoolManager`（不动），Kerberos 引擎走短连接；`release`/`evictor` 对短连接直接关闭。
- **注册 `HIVE_KERBEROS`**：`@Order(5)`、`family=HIVE_WIRE`，目录/扫描**委托** `HiveEngineSupport`；`requiresHostResolution=false`、`usesPooledConnections=false`。
- **依赖交付（drop-in + 条件源码根 + 桩）**：厂商 Hadoop/Hive 认证 jar 非 Maven Central 可解析坐标且体积大，放 `third-party/hive-auth/` drop-in，profile 按文件存在自动激活并加入条件源码根 `src/kerberos/java`；真实现**照抄** `bddf-public-service`，始终编译的接口 + 桩（`DisabledKerberosHiveConnector`）在无 jar 时代替，工厂单点 `Class.forName` 选择。开发机无 jar 也能构建/启动，`HIVE_KERBEROS` 目录/校验仍在（方案 A），仅连接脱敏失败。
- **降级语义**：`sql-editor.kerberos` 完全未配置 = 功能关闭、服务照常启动；配置存在但缺字段/路径非法 = fail startup。
- 不注册 `ICEBERG`（另见 change `add-iceberg-engine`）。
- 前端表单/MSW/图标不在本变更范围（见 change `add-kerberos-zk-discovery-frontend`）。

## Capabilities

### New Capabilities

- `backend-kerberos-zk-connection`: Kerberos + ZooKeeper 服务发现的连接机制（SPI 连接获取模型、单锁登录与全局状态还原、服务端环境配置、CIDR 校验、脱敏失败分类）。
- `backend-hive-kerberos-engine`: `HIVE_KERBEROS`（dbType 8）引擎注册、描述符与短连接获取合同。

### Modified Capabilities

- `backend-engine-spi`: 注册表包含 `HIVE_KERBEROS`；新增「引擎声明连接获取模型」要求。
- `backend-engine-catalog`: 目录返回五项，含 `HIVE_KERBEROS` 描述。
- `backend-data-source-management`: 校验改为引擎描述符驱动；Kerberos 引擎不要求密码。
- `backend-connection-security`: 目标网络策略覆盖 zk quorum；动态池豁免 Kerberos 引擎；新增 Kerberos 凭据服务端化与短连接要求。

## Impact

- 后端：`engine/EngineSupport`（加默认方法）、`engine/hive_kerberos/`、`datasource/connection/KerberosHiveConnector`、`ConnectionConfiguration`、`JdbcConfigurationBuilder`、`ShortLivedConnectionTester`、`TargetConnectionProvider`、`DataSourceValidator`、`DataSourceService`、`DataSourceRecord`、`DataSourceMapper.xml`、`SqlEditorProperties`、`application.yml`、Flyway `V7`、`pom.xml`（hadoop-common/hadoop-auth + exclusions）、ArchUnit。
- 依赖：厂商 drop-in jar（`hadoop-common`/`hadoop-auth`/`hive-jdbc:standalone`/`hive-shims`/`zookeeper`，`-TBDS-` 坐标，非 Central）；具体清单见 design 的 "Required vendor artifacts"。
- API：创建/编辑新增可选 Kerberos 字段；目录多一项 `HIVE_KERBEROS`。
- 部署：keytab 文件与 krb5.conf 按环境放服务器磁盘；`sql-editor.kerberos` 配置生效；真实集群端到端为**现场手动联调**，不在 CI。
- 前端：不在本 change 范围（见 `add-kerberos-zk-discovery-frontend`）。
