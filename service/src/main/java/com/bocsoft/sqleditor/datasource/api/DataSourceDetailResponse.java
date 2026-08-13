package com.bocsoft.sqleditor.datasource.api;

import java.time.Instant;
import java.util.Map;

public class DataSourceDetailResponse extends DataSourceListItemResponse {
    private int connectTimeoutSeconds;
    private Map<String, String> properties;
    private String description;
    private String lastTestMessage;
    private String createdBy;
    private String createdByName;
    private Instant createdAt;
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; } public void setConnectTimeoutSeconds(int v) { connectTimeoutSeconds=v; }
    public Map<String, String> getProperties() { return properties; } public void setProperties(Map<String, String> v) { properties=v; }
    public String getDescription() { return description; } public void setDescription(String v) { description=v; }
    public String getLastTestMessage() { return lastTestMessage; } public void setLastTestMessage(String v) { lastTestMessage=v; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String v) { createdBy=v; }
    public String getCreatedByName() { return createdByName; } public void setCreatedByName(String v) { createdByName=v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt=v; }
}
