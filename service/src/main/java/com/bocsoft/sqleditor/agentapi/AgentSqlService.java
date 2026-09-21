package com.bocsoft.sqleditor.agentapi;

import com.bocsoft.sqleditor.agentapi.api.AgentSqlExplainRequest;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlExplainResponse;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlQueryRequest;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlQueryResponse;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlRequest;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlValidateResponse;
import com.bocsoft.sqleditor.agentapi.api.AgentFinding;
import com.bocsoft.sqleditor.agentapi.api.AgentQueryMasking;
import com.bocsoft.sqleditor.agentapi.api.AgentQueryTruncation;
import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.DataSourceService;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.execution.ExecutionCommand;
import com.bocsoft.sqleditor.execution.ExecutionOutcome;
import com.bocsoft.sqleditor.execution.ExecutionSource;
import com.bocsoft.sqleditor.execution.ExplainService;
import com.bocsoft.sqleditor.execution.ResultSetReader;
import com.bocsoft.sqleditor.execution.SqlExecutionService;
import com.bocsoft.sqleditor.execution.SqlSafetyEvaluator;
import com.bocsoft.sqleditor.execution.api.SqlExplainRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionResponse;
import java.util.Collections;
import org.springframework.stereotype.Service;

@Service
public class AgentSqlService {
    private final DataSourceService dataSources;
    private final EngineRegistry engines;
    private final SqlSafetyEvaluator safety;
    private final ExplainService explains;
    private final SqlExecutionService executions;
    private final AgentApiMapper mapper;
    private final SqlEditorProperties properties;

    public AgentSqlService(DataSourceService dataSources, EngineRegistry engines, SqlSafetyEvaluator safety,
                           ExplainService explains, SqlExecutionService executions, AgentApiMapper mapper,
                           SqlEditorProperties properties) {
        this.dataSources = dataSources;
        this.engines = engines;
        this.safety = safety;
        this.explains = explains;
        this.executions = executions;
        this.mapper = mapper;
        this.properties = properties;
    }

    public AgentSqlValidateResponse validate(AuthContext auth, String dataSourceId, AgentSqlRequest request) {
        EngineSupport engine = engine(auth, dataSourceId);
        return mapper.validation(safety.evaluate(engine, request.getSql()));
    }

    public AgentSqlExplainResponse explain(AuthContext auth, String dataSourceId, AgentSqlExplainRequest request,
                                           String requestId, String clientIp) {
        EngineSupport engine = engine(auth, dataSourceId);
        safety.requireSafeSelect(engine, request.getSql());
        String namespace = AgentNamespaceResolver.resolve(request.getDatabase(), request.getSchema());
        SqlExplainRequest forwarded = new SqlExplainRequest();
        forwarded.setExecutionId(request.getExecutionId());
        forwarded.setDataSourceId(dataSourceId);
        forwarded.setDatabase(namespace);
        forwarded.setStatement(request.getSql());
        forwarded.setTimeoutSeconds(request.getTimeoutSeconds());
        return mapper.explain(explains.planDetailed(auth, forwarded, requestId, clientIp).getEvidence());
    }

    public AgentSqlQueryResponse query(AuthContext auth, String dataSourceId, AgentSqlQueryRequest request,
                                       String requestId, String clientIp) {
        SqlEditorProperties.AgentApi agent = properties.getAgentApi();
        SqlEditorProperties.Execution web = properties.getExecution();
        EngineSupport engine = engine(auth, dataSourceId);
        String sql = safety.requireSafeSelect(engine, request.getSql());
        String namespace = AgentNamespaceResolver.resolve(request.getDatabase(), request.getSchema());
        int maxRowsCap = Math.min(agent.getMaxRowLimit(), web.getMaxRowLimit());
        int requestedRows = request.getMaxRows() == null ? Math.min(agent.getDefaultRowLimit(), maxRowsCap) : request.getMaxRows();
        if (requestedRows < 1) {
            throw ApiException.validation("maxRows", "OUT_OF_RANGE", "返回行数必须在允许范围内");
        }
        requestedRows = Math.min(requestedRows, maxRowsCap);
        int timeoutCap = Math.min(agent.getTimeoutSeconds(), web.getTimeoutSeconds());
        int timeout = clampDown(request.getTimeoutSeconds(), timeoutCap);
        long bytes = Math.min(agent.getMaxResultBytes(), web.getMaxResultBytes());
        int cellBytes = agent.getMaxCellBytes();
        ExecutionCommand command = new ExecutionCommand(
            request.getExecutionId(), dataSourceId, namespace, sql,
            requestedRows, timeout, bytes, cellBytes, true, ExecutionSource.AI_AGENT, true);
        ExecutionOutcome outcome = executions.run(auth, command, requestId, clientIp);
        SqlExecutionResponse response = outcome.getResponse();
        ResultSetReader.ReadResult read = outcome.getRead();
        AgentQueryTruncation truncation = read == null
            ? new AgentQueryTruncation(false, false, 0)
            : new AgentQueryTruncation(read.isRowLimitReached(), read.isResultBytesReached(), read.getCellTruncatedCount());
        return new AgentSqlQueryResponse(response.getExecutionId(), response.getKind(), response.getColumns(),
            response.getRows(), response.getRowCount(), Boolean.TRUE.equals(response.getTruncated()), truncation,
            response.getDurationMs(), Collections.<AgentFinding>emptyList(),
            new AgentQueryMasking(false, null), requestedRows, timeout, bytes, cellBytes);
    }

    public int queryTimeoutSeconds(AgentSqlQueryRequest request) {
        SqlEditorProperties.AgentApi agent = properties.getAgentApi();
        int timeoutCap = Math.min(agent.getTimeoutSeconds(), properties.getExecution().getTimeoutSeconds());
        return clampDown(request.getTimeoutSeconds(), timeoutCap);
    }

    public int explainTimeoutSeconds(AgentSqlExplainRequest request) {
        return clampDown(request.getTimeoutSeconds(), properties.getExecution().getTimeoutSeconds());
    }

    private int clampDown(Integer requested, int cap) {
        if (requested == null) return cap;
        if (requested < 1) {
            throw ApiException.validation("timeoutSeconds", "OUT_OF_RANGE", "超时秒数必须在 1 到配置上限之间");
        }
        return Math.min(requested, cap);
    }

    private EngineSupport engine(AuthContext auth, String dataSourceId) {
        SavedDataSource source = dataSources.requireSaved(auth, dataSourceId);
        return engines.requireSaved(source.getEngine());
    }
}
