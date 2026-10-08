# Hive JDBC 驱动

Hive 连接使用 `org.apache.hive.jdbc.HiveDriver`，URL 方案 `jdbc:hive2://`。本目录采用与 `gbase` 相同的 drop-in 方式交付驱动，仓库不提交厂商 JAR。

## 放入驱动

1. 取得与现场 HiveServer2 版本匹配的 Hive JDBC standalone（fat）JAR。
2. 复制并命名为本目录下的 `hive-jdbc-standalone.jar`：

```bash
cp /path/to/hive-jdbc-standalone-*.jar service/third-party/hive/hive-jdbc-standalone.jar
```

3. 重新编译 SQL service。Maven 在该文件存在时会自动激活 `hive-official-jdbc` profile，并把驱动打进可运行 fat jar。

未放入驱动时，服务仍可启动，HIVE 数据源的 URL 仍组装为 `jdbc:hive2://`；测试连接或打开目标连接会返回脱敏的 `CONNECTION_FAILED`，提示未找到 Hive JDBC 驱动。MYSQL / POSTGRESQL / GBASE_8A 不受影响。

不要把从非官方镜像下载的 jar 或自造 stub 放进本目录。
