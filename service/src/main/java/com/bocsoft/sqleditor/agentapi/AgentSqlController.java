package com.bocsoft.sqleditor.agentapi;

import com.bocsoft.sqleditor.agentapi.api.AgentSqlExplainRequest;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlExplainResponse;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlQueryRequest;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlQueryResponse;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlRequest;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlValidateResponse;
import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.common.ClientIpResolver;
import com.bocsoft.sqleditor.common.RequestIds;
import com.bocsoft.sqleditor.execution.SqlExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequestMapping("/internal/api/v1/agent/data-sources/{id}/sql")
@Tag(name = "Agent Database API")
public class AgentSqlController {
    private final AgentSqlService service;
    private final SqlExecutionService executions;
    private final AsyncTaskExecutor executor;
    private final ClientIpResolver clientIps;

    public AgentSqlController(AgentSqlService service, SqlExecutionService executions,
                              @Qualifier("sqlExecutionExecutor") AsyncTaskExecutor executor,
                              ClientIpResolver clientIps) {
        this.service = service;
        this.executions = executions;
        this.executor = executor;
        this.clientIps = clientIps;
    }

    @PostMapping("/validate")
    @Operation(summary = "Authoritatively validate one read-only SELECT without executing it")
    public AgentSqlValidateResponse validate(@PathVariable String id, @Valid @RequestBody AgentSqlRequest request) {
        return service.validate(CurrentAuth.get(), id, request);
    }

    @PostMapping("/explain")
    @Operation(summary = "Return a normalized plan-only explain")
    public WebAsyncTask<AgentSqlExplainResponse> explain(@PathVariable final String id,
                                                         @Valid @RequestBody final AgentSqlExplainRequest request,
                                                         HttpServletRequest servlet) {
        final AuthContext auth = CurrentAuth.get();
        final String requestId = String.valueOf(servlet.getAttribute(RequestIds.ATTRIBUTE));
        final String clientIp = clientIps.resolve(servlet);
        WebAsyncTask<AgentSqlExplainResponse> task = new WebAsyncTask<AgentSqlExplainResponse>(
            executions.asyncTimeoutMs(service.explainTimeoutSeconds(request)), executor,
            () -> service.explain(auth, id, request, requestId, clientIp));
        task.onTimeout(() -> {
            executions.cancel(auth, request.getExecutionId());
            return null;
        });
        return task;
    }

    @PostMapping("/query")
    @Operation(summary = "Execute a bounded read-only SELECT")
    public WebAsyncTask<AgentSqlQueryResponse> query(@PathVariable final String id,
                                                     @Valid @RequestBody final AgentSqlQueryRequest request,
                                                     HttpServletRequest servlet) {
        final AuthContext auth = CurrentAuth.get();
        final String requestId = String.valueOf(servlet.getAttribute(RequestIds.ATTRIBUTE));
        final String clientIp = clientIps.resolve(servlet);
        WebAsyncTask<AgentSqlQueryResponse> task = new WebAsyncTask<AgentSqlQueryResponse>(
            executions.asyncTimeoutMs(service.queryTimeoutSeconds(request)), executor,
            () -> service.query(auth, id, request, requestId, clientIp));
        task.onTimeout(() -> {
            executions.cancel(auth, request.getExecutionId());
            return null;
        });
        return task;
    }
}
