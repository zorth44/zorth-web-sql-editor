package com.bocsoft.sqleditor.agentapi;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class AgentInternalCallerFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Service-Key";

    private final SqlEditorProperties properties;
    private final HandlerExceptionResolver exceptionResolver;

    public AgentInternalCallerFilter(SqlEditorProperties properties, HandlerExceptionResolver handlerExceptionResolver) {
        this.properties = properties;
        this.exceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/api/v1/agent/")
            || !properties.getAgentApi().isInternalCallerKeyRequired();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            String expected = properties.getAgentApi().getInternalCallerKey();
            String actual = request.getHeader(HEADER);
            if (expected == null || expected.isEmpty() || actual == null || !expected.equals(actual)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录已过期，请重新登录");
            }
            chain.doFilter(request, response);
        } catch (ApiException exception) {
            exceptionResolver.resolveException(request, response, null, exception);
        }
    }
}
