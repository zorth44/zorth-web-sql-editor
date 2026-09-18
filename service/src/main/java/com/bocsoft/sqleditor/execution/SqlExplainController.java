package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.common.ClientIpResolver;
import com.bocsoft.sqleditor.common.RequestIds;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.execution.api.SqlExplainRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionResponse;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
public class SqlExplainController {
    private final SqlExplainService explains;
    private final SqlExecutionService executions;
    private final AsyncTaskExecutor executor;
    private final ClientIpResolver clientIps;

    public SqlExplainController(SqlExplainService explains, SqlExecutionService executions,
                                @Qualifier("sqlExecutionExecutor") AsyncTaskExecutor executor,
                                ClientIpResolver clientIps) {
        this.explains = explains;
        this.executions = executions;
        this.executor = executor;
        this.clientIps = clientIps;
    }

    @PostMapping("/api/v1/sql/explains")
    public WebAsyncTask<SqlExecutionResponse> explain(@Valid @RequestBody SqlExplainRequest request,
                                                      HttpServletRequest servlet) {
        return start(request, servlet, ExplainMode.PLAN);
    }

    @PostMapping("/api/v1/sql/explains:analyze")
    public WebAsyncTask<SqlExecutionResponse> analyze(@Valid @RequestBody SqlExplainRequest request,
                                                      HttpServletRequest servlet) {
        explains.requireAnalyzeEnabled();
        return start(request, servlet, ExplainMode.ANALYZE);
    }

    private WebAsyncTask<SqlExecutionResponse> start(SqlExplainRequest request, HttpServletRequest servlet, ExplainMode mode) {
        AuthContext auth = CurrentAuth.get();
        SqlExecutionRequest inner = explains.prepare(auth, request, mode);
        String requestId = String.valueOf(servlet.getAttribute(RequestIds.ATTRIBUTE));
        long timeoutMs = executions.asyncTimeoutMs(inner);
        String clientIp = clientIps.resolve(servlet);
        WebAsyncTask<SqlExecutionResponse> task = new WebAsyncTask<SqlExecutionResponse>(
            timeoutMs, executor, () -> explains.execute(auth, inner, requestId, clientIp, mode));
        task.onTimeout(() -> {
            executions.cancel(auth, request.getExecutionId());
            return null;
        });
        return task;
    }
}
