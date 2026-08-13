package com.bocsoft.sqleditor.common;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = validUuid(request.getHeader(RequestIds.HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(RequestIds.ATTRIBUTE, requestId);
        response.setHeader(RequestIds.HEADER, requestId);
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            response.setHeader(RequestIds.HEADER, requestId);
            MDC.remove("requestId");
        }
    }

    private String validUuid(String value) {
        if (value == null || value.length() > 36) return null;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value) ? value : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
