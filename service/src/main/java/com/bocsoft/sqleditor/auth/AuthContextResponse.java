package com.bocsoft.sqleditor.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthContextResponse {
    private String userId;
    private String username;
    private String displayName;
    private Product product;
    private Instant tokenExpiresAt;
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        private String id;
        private String name;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
