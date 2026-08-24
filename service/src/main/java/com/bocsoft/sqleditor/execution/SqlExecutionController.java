package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.common.ClientIpResolver;
import com.bocsoft.sqleditor.common.RequestIds;
import com.bocsoft.sqleditor.execution.api.SqlExecutionRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionResponse;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequestMapping("/api/v1/sql/executions")
public class SqlExecutionController {
    private final SqlExecutionService service;
    private final AsyncTaskExecutor executor;
    private final ClientIpResolver clientIps;

    public SqlExecutionController(SqlExecutionService service,
                                  @Qualifier("sqlExecutionExecutor") AsyncTaskExecutor executor,
                                  ClientIpResolver clientIps) {
        this.service = service;
        this.executor = executor;
        this.clientIps = clientIps;
    }

    @PostMapping
    public WebAsyncTask<SqlExecutionResponse> execute(@Valid @RequestBody SqlExecutionRequest request,
                                                      HttpServletRequest servlet) {
        AuthContext auth = CurrentAuth.get();
        String requestId = String.valueOf(servlet.getAttribute(RequestIds.ATTRIBUTE));
        long timeoutMs = service.asyncTimeoutMs(request);
        String clientIp = clientIps.resolve(servlet);
        WebAsyncTask<SqlExecutionResponse> task = new WebAsyncTask<SqlExecutionResponse>(
            timeoutMs, executor, () -> service.execute(auth, request, requestId, clientIp));
        task.onTimeout(() -> {
            service.cancel(auth, request.getExecutionId());
            return null;
        });
        return task;
    }

    @PostMapping("/{id}:cancel")
    public ResponseEntity<Void> cancel(@PathVariable String id) {
        service.cancel(CurrentAuth.get(), id);
        return ResponseEntity.accepted().build();
    }
}
