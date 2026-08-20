# GBase 8a 官方 JDBC 驱动

GBase 8a 连接使用南大通用官方包 `gbase-connector-java`，驱动类 `com.gbase.jdbc.Driver`，URL 方案 `jdbc:gbase://`。该 JAR 不在 Maven Central，不能用 Oracle MySQL Connector/J 代替。

## 放入官方包

1. 从南大通用官方渠道取得与现场 8a 版本匹配的 `gbase-connector-java-*.jar`（下载中心常见为 zip，解开后取其中的 connector jar）。
2. 复制并命名为本目录下的 `gbase-connector-java.jar`：

```bash
cp /path/to/gbase-connector-java-*.jar service/third-party/gbase/gbase-connector-java.jar
```

3. 重新编译 SQL service。Maven 在该文件存在时会自动激活 `gbase-official-jdbc` profile，并把官方包打进可运行 fat jar。

未放入官方包时，服务仍可启动，GBASE_8A 数据源的 URL 仍组装为 `jdbc:gbase://`；测试连接或打开目标连接会失败，提示未找到官方驱动。MYSQL / POSTGRESQL 不受影响。

不要把从非官方镜像下载的 jar 或自造 stub 放进本目录。
