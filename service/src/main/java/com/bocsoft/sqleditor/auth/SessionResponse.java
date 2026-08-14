package com.bocsoft.sqleditor.auth;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class SessionResponse {
    private final User user;
    private final Product product;
    private final Instant expiresAt;
    private final List<String> capabilities;
    public SessionResponse(AuthContext context) {
        this.user = new User(context.getUserId(), context.getUsername(), context.getDisplayName());
        this.product = new Product(context.getProductId(), context.getProductName());
        this.expiresAt = context.getTokenExpiresAt();
        this.capabilities = Arrays.asList("DATA_SOURCE_MANAGE", "SQL_EXECUTE", "SQL_EXPORT", "HISTORY_READ");
    }
    public User getUser() { return user; }
    public Product getProduct() { return product; }
    public Instant getExpiresAt() { return expiresAt; }
    public List<String> getCapabilities() { return capabilities; }
    public static class User {
        private final String id; private final String username; private final String displayName;
        User(String id, String username, String displayName) { this.id=id; this.username=username; this.displayName=displayName; }
        public String getId() { return id; } public String getUsername() { return username; } public String getDisplayName() { return displayName; }
    }
    public static class Product {
        private final String id; private final String name;
        Product(String id, String name) { this.id=id; this.name=name; }
        public String getId() { return id; } public String getName() { return name; }
    }
}
