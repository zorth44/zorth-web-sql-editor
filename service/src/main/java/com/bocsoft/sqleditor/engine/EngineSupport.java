package com.bocsoft.sqleditor.engine;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import com.bocsoft.sqleditor.metadata.api.ColumnSearchItem;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.RelationshipEdge;
import com.bocsoft.sqleditor.metadata.api.TableConstraintLimits;
import com.bocsoft.sqleditor.metadata.api.TableConstraintMetadata;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.springframework.http.HttpStatus;

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

    default boolean supportsRelationshipMetadata() {
        return false;
    }

    default TableConstraintMetadata tableConstraints(Connection connection, String database, String table,
                                                     TableConstraintLimits limits) throws SQLException {
        return TableConstraintMetadata.unavailable();
    }

    default List<RelationshipEdge> importedRelationships(Connection connection, String database, String table,
                                                         Set<String> requestedUniqueSets, int limit) throws SQLException {
        throw capabilityNotSupported();
    }

    default List<RelationshipEdge> exportedRelationships(Connection connection, String database, String table,
                                                         Set<String> requestedUniqueSets, int limit) throws SQLException {
        throw capabilityNotSupported();
    }

    default List<ColumnSearchItem> searchColumns(Connection connection, String database, String keyword) throws SQLException {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "METADATA_QUERY_FAILED", "当前引擎不支持跨表字段搜索");
    }

    default boolean isAnalyzedExplain(String sql) {
        return false;
    }

    default String rewriteExplain(String sql, ExplainMode mode) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXPLAIN_STATEMENT_NOT_SUPPORTED", "当前引擎不支持计划改写");
    }

    default PlanEvidence normalizePlan(List<String> columnNames, List<List<Object>> rows) {
        return PlanEvidence.unsupported("当前引擎无法规范化执行计划");
    }

    String requireSingle(String sql);
    List<String> split(String sql);
    String quoteIdentifier(String value);

    default void applyConnectTimeout(Properties properties, long timeoutMillis) {
        properties.setProperty("connectTimeout", String.valueOf(Math.max(1L, timeoutMillis)));
    }

    default int streamingFetchSize() {
        return Integer.MIN_VALUE;
    }

    default boolean streamingRequiresAutoCommitOff() {
        return false;
    }

    static ApiException capabilityNotSupported() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CAPABILITY_NOT_SUPPORTED", "当前引擎不支持表关系元数据");
    }
}
