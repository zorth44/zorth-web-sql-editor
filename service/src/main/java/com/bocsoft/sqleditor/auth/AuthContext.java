package com.bocsoft.sqleditor.auth;

import java.time.Instant;

public final class AuthContext {
    private final String userId;
    private final String username;
    private final String displayName;
    private final String productId;
    private final String productName;
    private final Instant tokenExpiresAt;

    public AuthContext(String userId, String username, String displayName, String productId,
                       String productName, Instant tokenExpiresAt) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.productId = productId;
        this.productName = productName;
        this.tokenExpiresAt = tokenExpiresAt;
    }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
}
