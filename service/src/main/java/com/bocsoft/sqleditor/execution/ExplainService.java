package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import com.bocsoft.sqleditor.execution.api.SqlColumn;
import com.bocsoft.sqleditor.execution.api.SqlExplainRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ExplainService {
    private final TargetConnectionProvider targets;
    private final SqlStatementClassifier classifier;
    private final SqlExecutionService executions;
    private final SqlEditorProperties properties;

    public ExplainService(TargetConnectionProvider targets, SqlStatementClassifier classifier,
                          SqlExecutionService executions, SqlEditorProperties properties) {
        this.targets = targets;
        this.classifier = classifier;
        this.executions = executions;
        this.properties = properties;
    }

    public SqlExecutionResponse plan(AuthContext auth, SqlExplainRequest request, String requestId, String clientIp) {
        return explain(auth, request, requestId, clientIp, ExplainMode.PLAN,
            ExecutionSource.AI_AGENT_EXPLAIN, properties.getExecution().getTimeoutSeconds(), true).getResponse();
    }

    public ExplainOutcome planDetailed(AuthContext auth, SqlExplainRequest request, String requestId, String clientIp) {
        return explain(auth, request, requestId, clientIp, ExplainMode.PLAN,
            ExecutionSource.AI_AGENT_EXPLAIN, properties.getExecution().getTimeoutSeconds(), true);
    }

    public SqlExecutionResponse analyze(AuthContext auth, SqlExplainRequest request, String requestId, String clientIp) {
        SqlEditorProperties.ExplainAnalyze config = properties.getExplainAnalyze();
        if (!config.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EXPLAIN_ANALYZE_DISABLED", "实测执行计划未启用");
        }
        return explain(auth, request, requestId, clientIp, ExplainMode.ANALYZE,
            ExecutionSource.AI_AGENT_EXPLAIN_ANALYZE, config.getTimeoutSeconds(), true).getResponse();
    }

    public int timeoutCap(ExplainMode mode) {
        if (mode == ExplainMode.ANALYZE) return properties.getExplainAnalyze().getTimeoutSeconds();
        return properties.getExecution().getTimeoutSeconds();
    }

    private ExplainOutcome explain(AuthContext auth, SqlExplainRequest request, String requestId, String clientIp,
                                   ExplainMode mode, String source, int timeoutCap, boolean readOnly) {
        SavedDataSource dataSource = targets.require(auth, request.getDataSourceId());
        EngineSupport engine = targets.engine(dataSource);
        String sql = engine.requireSingle(request.getStatement());
        StatementType type = classifier.classify(sql);
        if (type != StatementType.SELECT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXPLAIN_STATEMENT_NOT_SUPPORTED", "仅支持对查询语句生成执行计划");
        }
        if (mode == ExplainMode.PLAN && engine.isAnalyzedExplain(sql)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXPLAIN_ANALYZE_NOT_ALLOWED", "计划口不允许 ANALYZE");
        }
        String rewritten = engine.rewriteExplain(sql, mode);
        int timeout = executions.clampTimeout(request.getTimeoutSeconds(), timeoutCap);
        ExecutionCommand command = new ExecutionCommand(
            request.getExecutionId(), request.getDataSourceId(), request.getDatabase(), rewritten,
            properties.getExecution().getDefaultRowLimit(), timeout,
            properties.getExecution().getMaxResultBytes(), Integer.MAX_VALUE,
            readOnly, source, false);
        ExecutionOutcome outcome = executions.run(auth, command, requestId, clientIp);
        PlanEvidence evidence = engine.normalizePlan(names(outcome.getResponse()), rows(outcome.getResponse()));
        return new ExplainOutcome(outcome.getResponse(), evidence);
    }

    private List<String> names(SqlExecutionResponse response) {
        if (response.getColumns() == null) return Collections.emptyList();
        List<String> names = new ArrayList<String>();
        for (SqlColumn column : response.getColumns()) names.add(column.getName());
        return names;
    }

    private List<List<Object>> rows(SqlExecutionResponse response) {
        return response.getRows() == null ? Collections.<List<Object>>emptyList() : response.getRows();
    }

    public static final class ExplainOutcome {
        private final SqlExecutionResponse response;
        private final PlanEvidence evidence;

        public ExplainOutcome(SqlExecutionResponse response, PlanEvidence evidence) {
            this.response = response;
            this.evidence = evidence;
        }

        public SqlExecutionResponse getResponse() { return response; }
        public PlanEvidence getEvidence() { return evidence; }
    }
}
