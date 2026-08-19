package com.bocsoft.sqleditor.engine;

import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface EngineSupport {
    String id();
    String family();
    boolean defaultNamespaceRequired();
    boolean canSwitchNamespaceOnConnection();
    int identifierMaxLength();
    EngineDescriptor descriptor();

    Map<String, String> validateProperties(Map<String, String> properties);
    JdbcTarget buildJdbc(ConnectionConfiguration configuration, ResolvedTarget resolved);
    ConnectionFailure classifyConnectionFailure(Throwable failure);
    String jdbcUrlWithoutNamespace(String url);
    void verifyDefaultNamespace(Connection connection, String defaultNamespace) throws SQLException;

    void applyNamespace(Connection connection, String namespace) throws SQLException;
    boolean restoreSession(Connection connection, String defaultNamespace) throws SQLException;

    void validateIdentifier(String field, String value);
    List<DatabaseItem> listDatabases(Connection connection, String keyword, boolean includeSystem) throws SQLException;
    List<TableItem> listTables(Connection connection, String database, String keyword, String[] types) throws SQLException;
    TableDetailResponse tableDetail(Connection connection, String database, String table) throws SQLException;
    void ensureNamespace(Connection connection, String database) throws SQLException;

    String requireSingle(String sql);
    List<String> split(String sql);
    String quoteIdentifier(String value);
}
