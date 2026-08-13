package com.bocsoft.sqleditor.auth;

import com.bocsoft.sqleditor.common.ApiException;
import java.io.IOException;
import java.util.Collections;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class AuthContextFilter extends OncePerRequestFilter {
    private final AuthContextResolver resolver;
    private final HandlerExceptionResolver exceptionResolver;

    public AuthContextFilter(AuthContextResolver resolver, HandlerExceptionResolver handlerExceptionResolver) {
        this.resolver = resolver;
        this.exceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ") || header.length() <= 7
                || header.substring(7).trim().length() != header.length() - 7) {
                throw ApiException.unauthenticated();
            }
            AuthContext context = resolver.resolve(header.substring(7));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                context, null, Collections.singletonList(new SimpleGrantedAuthority("DATA_SOURCE_MANAGE")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (ApiException exception) {
            SecurityContextHolder.clearContext();
            exceptionResolver.resolveException(request, response, null, exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
