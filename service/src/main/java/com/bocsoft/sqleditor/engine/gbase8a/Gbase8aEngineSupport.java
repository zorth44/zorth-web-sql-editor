package com.bocsoft.sqleditor.engine.gbase8a;

import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import com.bocsoft.sqleditor.engine.EngineDescriptor;
import com.bocsoft.sqleditor.engine.EngineField;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(3)
@Component
public class Gbase8aEngineSupport implements EngineSupport {
    private static final String PORT_DEFAULT = "5258";
    private final MysqlEngineSupport mysql;
    private final Gbase8aJdbc jdbc = new Gbase8aJdbc();

    public Gbase8aEngineSupport(MysqlEngineSupport mysql) {
        this.mysql = mysql;
    }

    @Override public String id() { return EngineId.GBASE_8A; }
    @Override public String family() { return mysql.family(); }
    @Override public boolean defaultNamespaceRequired() { return mysql.defaultNamespaceRequired(); }
    @Override public boolean canSwitchNamespaceOnConnection() { return mysql.canSwitchNamespaceOnConnection(); }
    @Override public int identifierMaxLength() { return mysql.identifierMaxLength(); }

    @Override public EngineDescriptor descriptor() {
        EngineDescriptor mysqlDescriptor = mysql.descriptor();
        return new EngineDescriptor(
            id(), "GBase 8a", family(), 5258,
            mysqlDescriptor.getEditorLanguage(), mysqlDescriptor.getIdentifierQuote(),
            mysqlDescriptor.getCapabilities(),
            withPortDefault(mysqlDescriptor.getConnectionFields()),
            mysqlDescriptor.getPropertyFields(),
            mysqlDescriptor.getResourceTree()
        );
    }

    @Override public Map<String, String> validateProperties(Map<String, String> properties) {
        return mysql.validateProperties(properties);
    }
    @Override public JdbcTarget buildJdbc(ConnectionConfiguration configuration, ResolvedTarget resolved) {
        return jdbc.rewrite(mysql.buildJdbc(configuration, resolved));
    }
    @Override public ConnectionFailure classifyConnectionFailure(Throwable failure) {
        if (jdbc.missingOfficialDriver(failure)) return jdbc.missingDriverFailure();
        return mysql.classifyConnectionFailure(failure);
    }
    @Override public String jdbcUrlWithoutNamespace(String url) { return jdbc.jdbcUrlWithoutNamespace(url); }
    @Override public void verifyDefaultNamespace(Connection connection, String defaultNamespace) throws SQLException {
        mysql.verifyDefaultNamespace(connection, defaultNamespace);
    }
    @Override public void applyNamespace(Connection connection, String namespace) throws SQLException {
        mysql.applyNamespace(connection, namespace);
    }
    @Override public boolean restoreSession(Connection connection, String defaultNamespace) throws SQLException {
        return mysql.restoreSession(connection, defaultNamespace);
    }
    @Override public void validateIdentifier(String field, String value) { mysql.validateIdentifier(field, value); }
    @Override public List<DatabaseItem> listDatabases(Connection connection, String keyword, boolean includeSystem) throws SQLException {
        return mysql.listDatabases(connection, keyword, includeSystem);
    }
    @Override public List<TableItem> listTables(Connection connection, String database, String keyword, String[] types) throws SQLException {
        return mysql.listTables(connection, database, keyword, types);
    }
    @Override public TableDetailResponse tableDetail(Connection connection, String database, String table) throws SQLException {
        return mysql.tableDetail(connection, database, table);
    }
    @Override public void ensureNamespace(Connection connection, String database) throws SQLException {
        mysql.ensureNamespace(connection, database);
    }
    @Override public String requireSingle(String sql) { return mysql.requireSingle(sql); }
    @Override public List<String> split(String sql) { return mysql.split(sql); }
    @Override public String quoteIdentifier(String value) { return mysql.quoteIdentifier(value); }
    @Override public void applyConnectTimeout(Properties properties, long timeoutMillis) {
        mysql.applyConnectTimeout(properties, timeoutMillis);
    }
    @Override public int streamingFetchSize() { return mysql.streamingFetchSize(); }

    private static List<EngineField> withPortDefault(List<EngineField> fields) {
        List<EngineField> out = new ArrayList<EngineField>();
        for (EngineField field : fields) {
            if ("port".equals(field.getName())) {
                out.add(EngineField.connection(
                    field.getName(), field.getKind(), field.getWidget(), field.getLabel(),
                    field.isRequired(), field.getMin(), field.getMax(), field.getMaxLength(),
                    PORT_DEFAULT, field.getOptions()));
            } else {
                out.add(field);
            }
        }
        return out;
    }
}
