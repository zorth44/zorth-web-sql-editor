package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.common.SqlEditorMetrics;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.execution.api.SqlExecutionRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionResponse;
import com.bocsoft.sqleditor.history.ExecutionHistoryService;
import com.bocsoft.sqleditor.history.persistence.ExecutionHistoryRecord;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SqlExecutionService {
    private final TargetConnectionProvider targets;
    private final SqlStatementClassifier classifier;
    private final ResultSetReader reader;
    private final ExecutionRegistry registry;
    private final ExecutionHistoryService history;
    private final SqlEditorProperties.Execution limits;
    private final SqlEditorMetrics metrics;

    public SqlExecutionService(TargetConnectionProvider targets, SqlStatementClassifier classifier, ResultSetReader reader,
                               ExecutionRegistry registry, ExecutionHistoryService history, SqlEditorProperties properties,
                               SqlEditorMetrics metrics) {
        this.targets = targets;
        this.classifier = classifier;
        this.reader = reader;
        this.registry = registry;
        this.history = history;
        this.limits = properties.getExecution();
        this.metrics = metrics;
    }

    public SqlExecutionResponse execute(AuthContext auth, SqlExecutionRequest request, String requestId) {
        String id = uuid(request.getExecutionId());
        SavedDataSource source = targets.require(auth, request.getDataSourceId());
        EngineSupport engine = targets.engine(source);
        String sql = engine.requireSingle(request.getStatement());
        if (sql.getBytes(StandardCharsets.UTF_8).length > limits.getMaxStatementBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "STATEMENT_TOO_LARGE", "SQL 超过大小上限");
        }
        int rowLimit = request.getRowLimit() == null ? limits.getDefaultRowLimit() : request.getRowLimit();
        if (rowLimit < 1 || rowLimit > limits.getMaxRowLimit()) throw ApiException.validation("rowLimit", "OUT_OF_RANGE", "返回行数必须在允许范围内");
        String database = cleanDatabase(engine, request.getDatabase());
        StatementType type = classifier.classify(sql);
        if (database == null && requiresDatabase(type, sql)) throw ApiException.validation("database", "REQUIRED", "请选择数据库");
        if (registry.contains(id) || history.exists(id)) throw new ApiException(HttpStatus.CONFLICT, "EXECUTION_ID_CONFLICT", "执行 ID 已被使用");
        registry.acquire(id, auth.getUserId(), source.getId());
        ExecutionHistoryRecord record = null;
        long started = System.nanoTime();
        Connection connection = null;
        Statement statement = null;
        try {
            record = history.start(id, auth, source, database, "EXECUTE", sql, type, requestId);
            connection = targets.borrow(source);
            connection.setAutoCommit(true);
            engine.applyNamespace(connection, database);
            statement = connection.createStatement();
            registry.bind(id, statement);
            statement.setQueryTimeout(limits.getTimeoutSeconds());
            statement.setMaxRows(rowLimit + 1);
            boolean hasResult = statement.execute(sql);
            long duration = elapsed(started);
            SqlExecutionResponse response;
            if (hasResult) {
                try (ResultSet rs = statement.getResultSet()) {
                    ResultSetReader.ReadResult result = reader.read(rs, rowLimit, limits.getMaxResultBytes());
                    response = SqlExecutionResponse.result(id, result.getColumns(), result.getRows(), result.isTruncated(), duration);
                    history.success(record, "RESULT_SET", result.getRows().size(), null, result.isTruncated(), duration);
                }
            } else {
                long count = statement.getUpdateCount();
                String kind = type == StatementType.DDL ? "DDL" : "UPDATE_COUNT";
                Long affected = count < 0 ? null : count;
                response = SqlExecutionResponse.update(id, kind, affected, duration);
                history.success(record, kind, 0, affected, false, duration);
            }
            metrics.execution("success", type.name(), duration);
            return response;
        } catch (SQLTimeoutException e) {
            long d = elapsed(started);
            if (record != null) history.failure(record, "TIMEOUT", d, e);
            metrics.execution("timeout", type.name(), d);
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "SQL_EXECUTION_TIMEOUT", "SQL 执行超时", details(id, e));
        } catch (SQLException e) {
            long d = elapsed(started);
            if (registry.cancelled(id)) {
                if (record != null) history.failure(record, "CANCELLED", d, e);
                metrics.execution("cancelled", type.name(), d);
                throw new ApiException(HttpStatus.CONFLICT, "SQL_EXECUTION_CANCELLED", "SQL 执行已取消", details(id, e));
            }
            if (record != null) history.failure(record, "FAILED", d, e);
            metrics.execution("failed", type.name(), d);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SQL_EXECUTION_FAILED", safe(e), details(id, e));
        } finally {
            close(statement);
            if (connection != null) try { targets.release(source, connection); } catch (SQLException ignored) { }
            registry.finish(id);
        }
    }

    public void cancel(AuthContext auth, String id) {
        uuid(id);
        if (registry.cancel(id, auth.getUserId())) return;
        ExecutionHistoryRecord r = history.owned(id, auth.getUserId());
        if (r == null) throw new ApiException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND", "执行不存在");
        throw new ApiException(HttpStatus.CONFLICT, "EXECUTION_ALREADY_FINISHED", "执行已经结束");
    }

    private String uuid(String id) {
        try {
            String v = UUID.fromString(id).toString();
            if (!v.equalsIgnoreCase(id)) throw new IllegalArgumentException();
            return v;
        } catch (Exception e) {
            throw ApiException.validation("executionId", "INVALID", "executionId 必须是规范 UUID");
        }
    }
    private String cleanDatabase(EngineSupport engine, String v) {
        if (v == null || v.trim().isEmpty()) return null;
        String x = v.trim();
        engine.validateIdentifier("database", x);
        return x;
    }
    private boolean requiresDatabase(StatementType type, String sql) {
        if (type != StatementType.SELECT) return type != StatementType.OTHER;
        String x = sql.toUpperCase(Locale.ROOT);
        return x.matches("(?s).*\\b(FROM|JOIN)\\b.*");
    }
    private long elapsed(long n) { return Math.max(0, (System.nanoTime() - n) / 1000000L); }
    private void close(Statement s) { if (s != null) try { s.close(); } catch (SQLException ignored) { } }
    private String safe(SQLException e) {
        String m = e.getMessage();
        return m == null ? "SQL 执行失败" : (m.length() > 1000 ? m.substring(0, 1000) : m);
    }
    private Map<String, Object> details(String id, SQLException e) {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("executionId", id);
        if (e.getSQLState() != null) d.put("sqlState", e.getSQLState());
        if (e.getErrorCode() != 0) d.put("vendorErrorCode", e.getErrorCode());
        return d;
    }
}
