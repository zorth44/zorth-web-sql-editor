package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.execution.api.SqlExplainRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SqlExplainService {
    private final TargetConnectionProvider targets;
    private final SqlStatementClassifier classifier;
    private final SqlExecutionService executions;
    private final SqlEditorProperties properties;

    public SqlExplainService(TargetConnectionProvider targets, SqlStatementClassifier classifier,
                             SqlExecutionService executions, SqlEditorProperties properties) {
        this.targets = targets;
        this.classifier = classifier;
        this.executions = executions;
        this.properties = properties;
    }

    public void requireAnalyzeEnabled() {
        if (!properties.getExplainAnalyze().isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EXPLAIN_ANALYZE_DISABLED", "EXPLAIN ANALYZE 未开启");
        }
    }

    public SqlExecutionRequest prepare(AuthContext auth, SqlExplainRequest request, ExplainMode mode) {
        if (mode == ExplainMode.ANALYZE) requireAnalyzeEnabled();
        SavedDataSource source = targets.require(auth, request.getDataSourceId());
        EngineSupport engine = targets.engine(source);
        String sql = engine.requireSingle(request.getStatement());
        StatementType type = classifier.classify(sql);
        if (type != StatementType.SELECT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXPLAIN_STATEMENT_NOT_SUPPORTED", "仅支持对查询语句生成执行计划");
        }
        if (mode == ExplainMode.PLAN && engine.isAnalyzedExplain(sql)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXPLAIN_ANALYZE_NOT_ALLOWED", "计划接口不允许 EXPLAIN ANALYZE");
        }
        String rewritten = engine.rewriteExplain(sql, mode);
        SqlExecutionRequest inner = new SqlExecutionRequest();
        inner.setExecutionId(request.getExecutionId());
        inner.setDataSourceId(request.getDataSourceId());
        inner.setDatabase(request.getDatabase());
        inner.setStatement(rewritten);
        inner.setReadOnly(Boolean.TRUE);
        inner.setTimeoutSeconds(timeoutSeconds(request, mode));
        return inner;
    }

    public SqlExecutionResponse execute(AuthContext auth, SqlExecutionRequest inner, String requestId,
                                        String clientIp, ExplainMode mode) {
        String source = mode == ExplainMode.ANALYZE
            ? ExecutionSource.AI_AGENT_EXPLAIN_ANALYZE
            : ExecutionSource.AI_AGENT_EXPLAIN;
        return executions.executeForced(auth, inner, requestId, clientIp, source);
    }

    public long asyncTimeoutMs(SqlExecutionRequest inner) {
        return executions.asyncTimeoutMs(inner);
    }

    private Integer timeoutSeconds(SqlExplainRequest request, ExplainMode mode) {
        if (mode != ExplainMode.ANALYZE) return request.getTimeoutSeconds();
        int cap = properties.getExplainAnalyze().getTimeoutSeconds();
        Integer requested = request.getTimeoutSeconds();
        if (requested == null) return Integer.valueOf(cap);
        if (requested.intValue() < 1) {
            throw ApiException.validation("timeoutSeconds", "OUT_OF_RANGE", "超时秒数必须在 1 到配置上限之间");
        }
        return Integer.valueOf(Math.min(requested.intValue(), cap));
    }
}
