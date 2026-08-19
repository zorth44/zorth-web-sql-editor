package com.bocsoft.sqleditor.engine.mysql;

import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MysqlEngineSupport implements EngineSupport {
    private final MysqlJdbc jdbc = new MysqlJdbc();
    private final MysqlFailures failures = new MysqlFailures();
    private final MysqlSqlScanner scanner = new MysqlSqlScanner();
    private final MysqlCatalogs catalogs = new MysqlCatalogs();

    @Override public String id() { return EngineId.MYSQL; }
    @Override public String family() { return "MYSQL_WIRE"; }
    @Override public boolean defaultNamespaceRequired() { return false; }
    @Override public boolean canSwitchNamespaceOnConnection() { return true; }
    @Override public int identifierMaxLength() { return 64; }

    @Override public Map<String, String> validateProperties(Map<String, String> properties) { return jdbc.validateProperties(properties); }
    @Override public JdbcTarget buildJdbc(ConnectionConfiguration configuration, ResolvedTarget resolved) { return jdbc.build(configuration, resolved); }
    @Override public ConnectionFailure classifyConnectionFailure(Throwable failure) { return failures.classify(failure); }
    @Override public String jdbcUrlWithoutNamespace(String url) { return jdbc.jdbcUrlWithoutNamespace(url); }
    @Override public void verifyDefaultNamespace(Connection connection, String defaultNamespace) throws SQLException { catalogs.verifyDefaultNamespace(connection, defaultNamespace); }

    @Override public void applyNamespace(Connection connection, String namespace) throws SQLException { catalogs.applyNamespace(connection, namespace); }
    @Override public boolean restoreSession(Connection connection, String defaultNamespace) throws SQLException { return catalogs.restoreSession(connection, defaultNamespace); }

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
}
