package com.bocsoft.sqleditor.datasource.api;

import java.time.Instant;

public class DataSourceListItemResponse {
    private String id; private String name; private String engine; private String host; private int port;
    private String username; private boolean passwordConfigured; private String defaultDatabase;
    private String sslMode; private String lastTestStatus; private Instant lastTestAt; private long version;
    private String updatedBy; private String updatedByName; private Instant updatedAt;
    public String getId() { return id; } public void setId(String v) { id=v; }
    public String getName() { return name; } public void setName(String v) { name=v; }
    public String getEngine() { return engine; } public void setEngine(String v) { engine=v; }
    public String getHost() { return host; } public void setHost(String v) { host=v; }
    public int getPort() { return port; } public void setPort(int v) { port=v; }
    public String getUsername() { return username; } public void setUsername(String v) { username=v; }
    public boolean isPasswordConfigured() { return passwordConfigured; } public void setPasswordConfigured(boolean v) { passwordConfigured=v; }
    public String getDefaultDatabase() { return defaultDatabase; } public void setDefaultDatabase(String v) { defaultDatabase=v; }
    public String getSslMode() { return sslMode; } public void setSslMode(String v) { sslMode=v; }
    public String getLastTestStatus() { return lastTestStatus; } public void setLastTestStatus(String v) { lastTestStatus=v; }
    public Instant getLastTestAt() { return lastTestAt; } public void setLastTestAt(Instant v) { lastTestAt=v; }
    public long getVersion() { return version; } public void setVersion(long v) { version=v; }
    public String getUpdatedBy() { return updatedBy; } public void setUpdatedBy(String v) { updatedBy=v; }
    public String getUpdatedByName() { return updatedByName; } public void setUpdatedByName(String v) { updatedByName=v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt=v; }
}
