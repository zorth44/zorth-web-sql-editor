package com.bocsoft.sqleditor.script.persistence;

import java.time.Instant;

public class ScriptRecord {
    private String id;
    private String userId;
    private String username;
    private String productId;
    private String name;
    private String dataSourceId;
    private String dataSourceName;
    private String databaseName;
    private String statementText;
    private long version;
    private String createdBy;
    private String createdByName;
    private Instant createdAt;
    private String updatedBy;
    private String updatedByName;
    private Instant updatedAt;

    public String getId() { return id; } public void setId(String v) { id = v; }
    public String getUserId() { return userId; } public void setUserId(String v) { userId = v; }
    public String getUsername() { return username; } public void setUsername(String v) { username = v; }
    public String getProductId() { return productId; } public void setProductId(String v) { productId = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getDataSourceId() { return dataSourceId; } public void setDataSourceId(String v) { dataSourceId = v; }
    public String getDataSourceName() { return dataSourceName; } public void setDataSourceName(String v) { dataSourceName = v; }
    public String getDatabaseName() { return databaseName; } public void setDatabaseName(String v) { databaseName = v; }
    public String getStatementText() { return statementText; } public void setStatementText(String v) { statementText = v; }
    public long getVersion() { return version; } public void setVersion(long v) { version = v; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String v) { createdBy = v; }
    public String getCreatedByName() { return createdByName; } public void setCreatedByName(String v) { createdByName = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public String getUpdatedBy() { return updatedBy; } public void setUpdatedBy(String v) { updatedBy = v; }
    public String getUpdatedByName() { return updatedByName; } public void setUpdatedByName(String v) { updatedByName = v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
}
