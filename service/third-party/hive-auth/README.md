# Kerberos / TBDS Hive 认证依赖（drop-in + 条件编译）

`HIVE_KERBEROS` 引擎通过 Kerberos keytab 登录并使用 ZooKeeper 服务发现连接 HiveServer2。
这条路径的**真实现**直接 import 厂商 Hadoop/Hive 类（`UserGroupInformation`、`Configuration`、
`KerberosName`），因此采用 drop-in + 条件源码根的方式交付：厂商 JAR 在位时自动激活
`kerberos-vendor` profile 编译真实现；未放 JAR 时服务照常构建、启动，`HIVE_KERBEROS` 仍在目录中，
连接返回脱敏的 `CONNECTION_FAILED`。

真实现位于条件源码根 `service/src/kerberos/java/.../hive_kerberos/vendor/`，仅在该 profile 下参与编译。
工厂 `KerberosConnectorFactory` 是全工程唯一的反射点。

## 放入依赖

把厂商 JAR（TBDS 定制版本）复制到本目录，并使用**以下固定文件名**：

| 文件名 | 用途 |
| --- | --- |
| `hadoop-common.jar` | `Configuration`、`UserGroupInformation`（此项存在即激活 profile） |
| `hadoop-auth.jar` | `KerberosName` |
| `hive-jdbc-standalone.jar` | `HiveDriver`、ZooKeeper 服务发现 |
| `hive-shims.jar` | Hive 运行时 shim |
| `zookeeper.jar` | ZooKeeper 客户端（standalone fat jar 未内嵌时） |
| `commons-configuration2.jar` | 参考实现显式声明的传递依赖 |

`hadoop-common` / `hive-jdbc` 还有一批传递依赖（guava、commons-lang3/collections/logging、woodstox、
stax2、jackson 等），缺失会在首次登录时报 `NoClassDefFoundError`。**推荐不要手工逐个拼版本**，
优先向厂商/现场要可用的 Maven 仓库，或从参考工程导出整棵依赖树后再按上表重命名核心 JAR。

## 行为

- 未放入 JAR：`kerberos-vendor` profile 不激活，`src/kerberos/java` 不编译；`mvn package`、启动、
  无集群单测全部正常；`HIVE_KERBEROS` 连接返回脱敏失败，其他引擎不受影响。
- 放入 JAR：真实现参与编译并打进 fat jar（`includeSystemScope=true`）。

不要把从非官方镜像下载的 JAR 或自造 stub 放进本目录。
