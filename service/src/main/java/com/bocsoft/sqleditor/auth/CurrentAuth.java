package com.bocsoft.sqleditor.auth;

import com.bocsoft.sqleditor.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentAuth {
    private CurrentAuth() { }
    public static AuthContext get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthContext)) {
            throw ApiException.unauthenticated();
        }
        return (AuthContext) authentication.getPrincipal();
    }
}
