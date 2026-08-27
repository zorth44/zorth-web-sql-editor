package com.bocsoft.sqleditor.engine.postgres;

import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import com.bocsoft.sqleditor.engine.EngineCapabilities;
import com.bocsoft.sqleditor.engine.EngineDescriptor;
import com.bocsoft.sqleditor.engine.EngineField;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.ResourceTreeLevel;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(2)
@Component
public class PostgresEngineSupport implements EngineSupport {
    private final PostgresJdbc jdbc = new PostgresJdbc();
    private final PostgresFailures failures = new PostgresFailures();
    private final PostgresSqlScanner scanner = new PostgresSqlScanner();
    private final PostgresCatalogs catalogs = new PostgresCatalogs();

    @Override public String id() { return EngineId.POSTGRESQL; }
    @Override public String family() { return "POSTGRES_WIRE"; }
    @Override public boolean defaultNamespaceRequired() { return true; }
    @Override public boolean canSwitchNamespaceOnConnection() { return true; }
    @Override public int identifierMaxLength() { return 63; }

    @Override public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            id(), "PostgreSQL", family(), 5432, "pgsql", "\"",
            new EngineCapabilities(defaultNamespaceRequired(), canSwitchNamespaceOnConnection()),
            Arrays.asList(
                EngineField.connection("host", "HOST", "TEXT", "Host", true, null, null, Integer.valueOf(255), null, null),
                EngineField.connection("port", "PORT", "NUMBER", "Port", true, Integer.valueOf(1), Integer.valueOf(65535), null, "5432", null),
                EngineField.connection("username", "USERNAME", "TEXT", "用户名", true, null, null, Integer.valueOf(128), null, null),
                EngineField.password("password", "密码", 1024),
                EngineField.connection("defaultDatabase", "DEFAULT_NAMESPACE", "TEXT", "数据库名", true, null, null, Integer.valueOf(63), null, null),
                EngineField.connection("sslMode", "SSL_MODE", "SELECT", "SSL 模式", true, null, null, null, "PREFERRED",
                    EngineField.labeled("DISABLED", "禁用", "PREFERRED", "优先", "REQUIRED", "必需")),
                EngineField.connection("connectTimeoutSeconds", "TIMEOUT", "NUMBER", "连接超时（秒）", true, Integer.valueOf(1), Integer.valueOf(30), null, "10", null)
            ),
            PostgresJdbc.PROPERTY_FIELDS,
            Arrays.asList(
                ResourceTreeLevel.namespace("模式", "筛选模式", "databases"),
                ResourceTreeLevel.child("TABLE", "表", "筛选表名", "NAMESPACE"),
                ResourceTreeLevel.child("VIEW", "视图", null, "NAMESPACE")
            )
        );
    }

    @Override public Map<String, String> validateProperties(Map<String, String> properties) { return jdbc.validateProperties(properties); }
    @Override public JdbcTarget buildJdbc(ConnectionConfiguration configuration, ResolvedTarget resolved) { return jdbc.build(configuration, resolved); }
    @Override public ConnectionFailure classifyConnectionFailure(Throwable failure) { return failures.classify(failure); }
    @Override public String jdbcUrlWithoutNamespace(String url) { return jdbc.jdbcUrlWithoutNamespace(url); }
    @Override public void verifyDefaultNamespace(Connection connection, String defaultNamespace) throws SQLException {
        catalogs.verifyDefaultNamespace(connection, defaultNamespace);
    }

    @Override public void applyNamespace(Connection connection, String namespace) throws SQLException { catalogs.applyNamespace(connection, namespace); }
    @Override public boolean restoreSession(Connection connection, String defaultNamespace) throws SQLException {
        return catalogs.restoreSession(connection, defaultNamespace);
    }

    @Override public void validateIdentifier(String field, String value) { catalogs.validateIdentifier(field, value); }
    @Override public List<DatabaseItem> listDatabases(Connection connection, String keyword, boolean includeSystem) throws SQLException {
        return catalogs.listDatabases(connection, keyword, includeSystem);
    }
    @Override public List<TableItem> listTables(Connection connection, String database, String keyword, String[] types) throws SQLException {
        return catalogs.listTables(connection, database, keyword, types);
    }
    @Override public TableDetailResponse tableDetail(Connection connection, String database, String table) throws SQLException {
        return catalogs.tableDetail(connection, database, table);
    }
    @Override public void ensureNamespace(Connection connection, String database) throws SQLException { catalogs.ensureNamespace(connection, database); }

    @Override public String requireSingle(String sql) { return scanner.requireSingle(sql); }
    @Override public List<String> split(String sql) { return scanner.split(sql); }
    @Override public String quoteIdentifier(String value) { return catalogs.quoteIdentifier(value); }

    @Override public void applyConnectTimeout(Properties properties, long timeoutMillis) {
        int seconds = (int) Math.max(1L, (timeoutMillis + 999L) / 1000L);
        properties.setProperty("connectTimeout", String.valueOf(seconds));
        properties.setProperty("loginTimeout", String.valueOf(seconds));
    }

    @Override public int streamingFetchSize() { return 100; }

    @Override public boolean streamingRequiresAutoCommitOff() { return true; }
}
