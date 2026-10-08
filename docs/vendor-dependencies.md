# 厂商驱动的交付方式（drop-in 与条件编译）

本仓库连接的部分目标库依赖**厂商私有 JAR**，这些 JAR 既不在 Maven Central，体积又大、常无法从内网取出。本文说明统一的处理方式，保证「开发机没有厂商 JAR 时也能构建、启动和测试；内网有 JAR 时自动启用对应能力」。

适用范围：`service/`（Spring Boot）。前端不受影响。

## 1. 背景与约束

- 厂商构件坐标形如 `com.gbase:gbase-connector-java`、`org.apache.hive:hive-jdbc:3.1.3-TBDS-5.3.1.3`、`org.apache.hadoop:hadoop-common:3.2.2-TBDS-5.3.1.3` 等，其中 `-TBDS-` 为现场定制版本。
- 这些坐标**无法从公共仓库解析**；开发机 `~/.m2` 里通常只有失败的 `.lastUpdated` 标记。
- 因此**禁止**把厂商构件写成普通的可解析依赖（否则开发机 `mvn` 直接失败）。
- 交付原则：**服务默认构建不引入任何厂商依赖；能力按需启用，缺失时安全降级。**

## 2. 两种模式

### 2.1 纯驱动 drop-in（gbase / hive 现状）

驱动类只通过 `Class.forName` / `DriverManager` 使用，代码**不 import 厂商类**，因此开发机没有 JAR 也能编译。

- JAR 放 `service/third-party/<engine>/`，由 `.gitignore` 忽略。
- `service/pom.xml` 已有的按文件存在激活的 profile：`gbase-official-jdbc`、`hive-official-jdbc`，以 `system` scope 引入，`spring-boot-maven-plugin` 已配置 `includeSystemScope=true`，打包时会把 JAR 打进 fat jar。
- 未放 JAR 时服务仍启动，连接该引擎返回脱敏的 `CONNECTION_FAILED`；其他引擎不受影响。

现有说明见 `service/third-party/gbase/README.md`、`service/third-party/hive/README.md`。

### 2.2 drop-in + 条件编译（需要编译期引用厂商类时）

当能力必须**直接 import 厂商类**（例如 Kerberos 需要 `org.apache.hadoop.security.UserGroupInformation`、`org.apache.hadoop.security.authentication.util.KerberosName`）时，纯 `Class.forName` 不够。做法是把依赖厂商类的实现放进**条件源码根**，只在厂商 JAR 到位时才参与编译：

```text
service/src/main/java/.../<feature>/
    <Feature>Connector.java          # 接口，零厂商 import（始终编译）
    Disabled<Feature>Connector.java  # 桩：调用即返回脱敏 CONNECTION_FAILED（始终编译）
    <Feature>ConnectorFactory.java   # @Bean：Class.forName 找真实现，找不到用桩（始终编译）

service/src/vendor/java/.../<feature>/vendor/
    Vendor<Feature>Connector.java    # 直接 import 厂商类，仅条件编译
```

`service/pom.xml` 新增 profile（以当前 Kerberos 为例）：

```xml
<profile>
  <id>kerberos-vendor</id>
  <activation>
    <file><exists>${project.basedir}/third-party/hive-auth/hadoop-common.jar</exists></file>
  </activation>
  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
        <version>3.5.0</version>
        <executions>
          <execution>
            <id>add-vendor-source</id>
            <phase>generate-sources</phase>
            <goals><goal>add-source</goal></goals>
            <configuration>
              <sources><source>${project.basedir}/src/vendor/java</source></sources>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
  <dependencies>
    <!-- 厂商 JAR：system scope + systemPath，与 gbase/hive profile 同构 -->
  </dependencies>
</profile>
```

工厂是**全工程唯一的反射点**，负责在运行时选择实现：

```java
@Bean
FeatureConnector featureConnector(...) {
    try {
        Class<?> c = Class.forName("...vendor.VendorFeatureConnector");
        return (FeatureConnector) c.getConstructor(...).newInstance(...);
    } catch (ClassNotFoundException absent) {
        return new DisabledFeatureConnector(...);   // 脱敏失败
    }
}
```

## 3. 行为对照

| 环境 | 厂商 JAR | profile | 结果 |
| --- | --- | --- | --- |
| 开发机 | 无 | 文件不存在，未激活 | 厂商源码不编译；能 `mvn package`、启动、跑无集群单测；该能力连接返回脱敏失败，其他引擎无影响 |
| 内网 / 现场 | 有（drop-in） | 文件存在，自动激活 | 真实现编译并打进 fat jar，能力与参考实现同款运行 |

要点：

- **不写入默认可解析依赖**，开发机构建永远绿色，也不会去公共仓库拉厂商坐标。
- 条件源码根里的类**不进开发机的 fat jar**，因此运行时不出现 `NoClassDefFoundError`。
- 与参考工程（`bddf-public-service`）保持一致：真实现可以基本照抄，只需改包名、实现本仓库接口、按需要加线程安全包装。

## 4. Kerberos / ZooKeeper 能力（`add-kerberos-zk-discovery-backend`）

采用本文 2.2 模式，且**只桩连接器**：`HIVE_KERBEROS` 引擎、描述符、字段种类、校验仍始终存在，目录始终列出该引擎；开发机无 JAR 时仅真实连接不可用。

- drop-in 目录：`service/third-party/hive-auth/`（需在 `.gitignore` 忽略其 `*.jar`，由该变更加入）。
- 条件源码根：`service/src/vendor/java/.../hive_kerberos/vendor/TbdsKerberosHiveConnector.java`。
- 参考实现：`/Users/zorth/Code/bddf/bddf-public-service` 的 `TBDSSecurityHandle`、`KerberosUtil`（已验证可运行）。

### 需要的 JAR

| 坐标 | 提供的类 / 用途 |
| --- | --- |
| `org.apache.hadoop:hadoop-common:3.2.2-TBDS-5.3.1.3` | `Configuration`、`UserGroupInformation`（keytab 登录、状态还原） |
| `org.apache.hadoop:hadoop-auth:3.2.2-TBDS-5.3.1.3` | `KerberosName`（realm / principal 推导） |
| `org.apache.hive:hive-jdbc:3.1.3-TBDS-5.3.1.3`（classifier `standalone`） | `HiveDriver`、ZK 服务发现 |
| `org.apache.hive:hive-shims:3.1.3-TBDS-5.3.1.3` | Hive 运行时 shim |
| `org.apache.zookeeper:zookeeper:3.4.6` | ZK 客户端（若 standalone fat jar 未内嵌） |
| `org.apache.commons:commons-configuration2:2.1.1` | 参考实现显式声明的传递依赖 |

`hadoop-common` / `hive-jdbc` 还有一批传递依赖（guava、commons-lang3/collections/logging、woodstox/stax2、jackson 等），缺失会在首次登录报 `NoClassDefFoundError`。

**推荐获取方式**：不要逐个手工拼版本。优先向厂商/现场要可用的 Maven 仓库（Nexus/私服），或从参考工程导出整棵依赖树：

```bash
cd /Users/zorth/Code/bddf/bddf-public-service
mvn -q dependency:copy-dependencies -DoutputDirectory=deploy-jars -DincludeScope=runtime
```

把整个目录 drop-in 到 `service/third-party/hive-auth/`。可先核对 standalone fat jar 是否已内嵌 Hadoop/ZK 类：

```bash
unzip -l hive-jdbc-*-standalone.jar | grep -E 'UserGroupInformation|org/apache/zookeeper'
```

## 5. 新增一个依赖厂商 JAR 的引擎：检查清单

1. 能否只靠 `Class.forName` 使用？能 → 用 2.1；必须 import 厂商类 → 用 2.2。
2. 厂商类只出现在 `src/vendor/java` 下的真实现里；接口、桩、工厂零厂商 import。
3. 新增按文件存在自动激活的 profile，并加入 `system` scope 依赖；如需额外源码根，接 `build-helper-maven-plugin`。
4. 把 JAR 目录加进 `.gitignore`，并写一份 `service/third-party/<name>/README.md` 说明放法和影响。
5. 验收：未放 JAR 时能构建、启动、跑单测；放 JAR 时能力启用；两种情况下其他引擎行为不变。

## 6. 相关文档

- `service/docs/deployment.md`（部署与现场 JAR 放置）
- `docs/local-development.md`（本地链路；GBase 8a 放 JAR 的示例）
- `service/third-party/gbase/README.md`、`service/third-party/hive/README.md`
