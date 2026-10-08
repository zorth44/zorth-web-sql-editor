package com.bocsoft.sqleditor.engine.iceberg;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import com.bocsoft.sqleditor.engine.EngineCapabilities;
import com.bocsoft.sqleditor.engine.EngineDescriptor;
import com.bocsoft.sqleditor.engine.EngineField;
import com.bocsoft.sqleditor.engine.EngineFieldOption;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import com.bocsoft.sqleditor.engine.ResourceTreeLevel;
import com.bocsoft.sqleditor.engine.hive.HiveEngineSupport;
import com.bocsoft.sqleditor.engine.hive_kerberos.KerberosConnectionSpec;
import com.bocsoft.sqleditor.engine.hive_kerberos.KerberosHiveConnector;
import com.bocsoft.sqleditor.metadata.api.ColumnSearchItem;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.RelationshipEdge;
import com.bocsoft.sqleditor.metadata.api.TableConstraintLimits;
import com.bocsoft.sqleditor.metadata.api.TableConstraintMetadata;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(6)
@Component
public class IcebergEngineSupport implements EngineSupport {
    static final String KEYTAB_SUBDIRECTORY = "iceberg";

    private final HiveEngineSupport hive;
    private final KerberosHiveConnector connector;
    private final SqlEditorProperties properties;

    @Autowired
    public IcebergEngineSupport(KerberosHiveConnector connector, SqlEditorProperties properties) {
        this(new HiveEngineSupport(), connector, properties);
    }

    IcebergEngineSupport(HiveEngineSupport hive, KerberosHiveConnector connector, SqlEditorProperties properties) {
        this.hive = hive;
        this.connector = connector;
        this.properties = properties;
    }

    @Override public String id() { return EngineId.ICEBERG; }
    @Override public String family() { return "HIVE_WIRE"; }
    @Override public boolean defaultNamespaceRequired() { return false; }
    @Override public boolean canSwitchNamespaceOnConnection() { return true; }
    @Override public int identifierMaxLength() { return 128; }
    @Override public boolean requiresHostResolution() { return false; }
    @Override public boolean usesPooledConnections() { return false; }

    @Override public EngineDescriptor descriptor() {
        List<EngineFieldOption> environments = new ArrayList<EngineFieldOption>();
        SqlEditorProperties.Kerberos kerberos = properties == null ? null : properties.getKerberos();
        if (kerberos != null) {
            for (String name : kerberos.getEnvironments().keySet()) {
                environments.add(new EngineFieldOption(name, name));
            }
        }
        return new EngineDescriptor(
            id(), "Iceberg", family(), 10000, "hive", "`",
            new EngineCapabilities(defaultNamespaceRequired(), canSwitchNamespaceOnConnection()),
            Arrays.asList(
                new EngineField("environment", "ENVIRONMENT", "SELECT", "环境", true, null,
                    null, null, null, null, environments.isEmpty() ? null : environments),
                EngineField.connection("keytabFile", "KEYTAB", "TEXT", "Keytab 文件", true, null, null,
                    Integer.valueOf(255), null, null),
                EngineField.connection("queueName", "QUEUE", "TEXT", "队列", false, null, null,
                    Integer.valueOf(128), null, null),
                EngineField.connection("defaultDatabase", "DEFAULT_NAMESPACE", "TEXT", "默认数据库", false, null,
                    null, Integer.valueOf(128), null, null)
            ),
            new ArrayList<EngineField>(),
            Arrays.asList(
                ResourceTreeLevel.namespace("数据库", "筛选数据库", "databases"),
                ResourceTreeLevel.child("TABLE", "表", "筛选表名", "NAMESPACE"),
                ResourceTreeLevel.child("VIEW", "视图", null, "NAMESPACE")
            )
        );
    }

    @Override public Map<String, String> validateProperties(Map<String, String> properties) {
        return hive.validateProperties(properties);
    }

    @Override public JdbcTarget buildJdbc(ConnectionConfiguration configuration, ResolvedTarget resolved) {
        SqlEditorProperties.Kerberos kerberos = properties == null ? null : properties.getKerberos();
        return KerberosConnectionSpec.forConfiguration(configuration, kerberos, KEYTAB_SUBDIRECTORY).toJdbcTarget();
    }

    @Override public Connection openConnection(JdbcTarget target) throws SQLException {
        return connector.openConnection(KerberosConnectionSpec.fromJdbcTarget(target));
    }

    @Override public ConnectionFailure classifyConnectionFailure(Throwable failure) {
        return connector.classify(failure);
    }

    @Override public String jdbcUrlWithoutNamespace(String url) { return url; }

    @Override public void verifyDefaultNamespace(Connection connection, String defaultNamespace) throws SQLException {
        hive.verifyDefaultNamespace(connection, defaultNamespace);
    }
    @Override public void applyNamespace(Connection connection, String namespace) throws SQLException {
        hive.applyNamespace(connection, namespace);
    }
    @Override public boolean restoreSession(Connection connection, String defaultNamespace) throws SQLException {
        return hive.restoreSession(connection, defaultNamespace);
    }
    @Override public void validateIdentifier(String field, String value) { hive.validateIdentifier(field, value); }
    @Override public List<DatabaseItem> listDatabases(Connection connection, String keyword, boolean includeSystem) throws SQLException {
        return hive.listDatabases(connection, keyword, includeSystem);
    }
    @Override public List<TableItem> listTables(Connection connection, String database, String keyword, String[] types) throws SQLException {
        return hive.listTables(connection, database, keyword, types);
    }
    @Override public TableDetailResponse tableDetail(Connection connection, String database, String table) throws SQLException {
        return hive.tableDetail(connection, database, table);
    }
    @Override public void ensureNamespace(Connection connection, String database) throws SQLException {
        hive.ensureNamespace(connection, database);
    }
    @Override public boolean supportsRelationshipMetadata() { return false; }
    @Override public TableConstraintMetadata tableConstraints(Connection connection, String database, String table,
                                                              TableConstraintLimits limits) {
        return TableConstraintMetadata.unavailable();
    }
    @Override public List<RelationshipEdge> importedRelationships(Connection connection, String database, String table,
                                                                  Set<String> requestedUniqueSets, int limit) throws SQLException {
        return hive.importedRelationships(connection, database, table, requestedUniqueSets, limit);
    }
    @Override public List<RelationshipEdge> exportedRelationships(Connection connection, String database, String table,
                                                                  Set<String> requestedUniqueSets, int limit) throws SQLException {
        return hive.exportedRelationships(connection, database, table, requestedUniqueSets, limit);
    }
    @Override public List<ColumnSearchItem> searchColumns(Connection connection, String database, String keyword) throws SQLException {
        return hive.searchColumns(connection, database, keyword);
    }
    @Override public boolean isAnalyzedExplain(String sql) { return hive.isAnalyzedExplain(sql); }
    @Override public String rewriteExplain(String sql, ExplainMode mode) { return hive.rewriteExplain(sql, mode); }
    @Override public PlanEvidence normalizePlan(List<String> columnNames, List<List<Object>> rows) {
        return hive.normalizePlan(columnNames, rows);
    }
    @Override public String requireSingle(String sql) { return hive.requireSingle(sql); }
    @Override public List<String> split(String sql) { return hive.split(sql); }
    @Override public String quoteIdentifier(String value) { return hive.quoteIdentifier(value); }
    @Override public void applyConnectTimeout(Properties properties, long timeoutMillis) {
        hive.applyConnectTimeout(properties, timeoutMillis);
    }
    @Override public int streamingFetchSize() { return hive.streamingFetchSize(); }
    @Override public boolean streamingRequiresAutoCommitOff() { return hive.streamingRequiresAutoCommitOff(); }
}
