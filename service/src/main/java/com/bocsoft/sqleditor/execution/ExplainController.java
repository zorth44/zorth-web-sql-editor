package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.common.ClientIpResolver;
import com.bocsoft.sqleditor.common.RequestIds;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.execution.api.SqlExplainRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionResponse;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequestMapping("/api/v1/sql")
public class ExplainController {
    private final ExplainService service;
    private final SqlExecutionService executions;
    private final AsyncTaskExecutor executor;
    private final ClientIpResolver clientIps;

    public ExplainController(ExplainService service, SqlExecutionService executions,
                             @Qualifier("sqlExecutionExecutor") AsyncTaskExecutor executor,
                             ClientIpResolver clientIps) {
        this.service = service;
        this.executions = executions;
        this.executor = executor;
        this.clientIps = clientIps;
    }

    @PostMapping("/explains")
    public WebAsyncTask<SqlExecutionResponse> plan(@Valid @RequestBody SqlExplainRequest request,
                                                   HttpServletRequest servlet) {
        return run(request, servlet, false);
    }

    @PostMapping("/explains:analyze")
    public WebAsyncTask<SqlExecutionResponse> analyze(@Valid @RequestBody SqlExplainRequest request,
                                                      HttpServletRequest servlet) {
        return run(request, servlet, true);
    }

    private WebAsyncTask<SqlExecutionResponse> run(final SqlExplainRequest request, HttpServletRequest servlet,
                                                   final boolean analyze) {
        final AuthContext auth = CurrentAuth.get();
        final String requestId = String.valueOf(servlet.getAttribute(RequestIds.ATTRIBUTE));
        final String clientIp = clientIps.resolve(servlet);
        int cap = service.timeoutCap(analyze ? ExplainMode.ANALYZE : ExplainMode.PLAN);
        Integer requested = request.getTimeoutSeconds();
        int timeout = requested == null ? cap : requested;
        WebAsyncTask<SqlExecutionResponse> task = new WebAsyncTask<SqlExecutionResponse>(
            executions.asyncTimeoutMs(timeout), executor, () -> analyze
                ? service.analyze(auth, request, requestId, clientIp)
                : service.plan(auth, request, requestId, clientIp));
        task.onTimeout(() -> {
            executions.cancel(auth, request.getExecutionId());
            return null;
        });
        return task;
    }
}
