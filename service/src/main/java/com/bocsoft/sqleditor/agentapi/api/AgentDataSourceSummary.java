package com.bocsoft.sqleditor.agentapi.api;

import java.time.Instant;

public class AgentDataSourceSummary {
    private String id;
    private String name;
    private String engine;
    private String host;
    private int port;
    private String defaultDatabase;
    private String sslMode;
    private String lastTestStatus;
    private Instant lastTestAt;

    public String getId() { return id; } public void setId(String v) { id = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getEngine() { return engine; } public void setEngine(String v) { engine = v; }
    public String getHost() { return host; } public void setHost(String v) { host = v; }
    public int getPort() { return port; } public void setPort(int v) { port = v; }
    public String getDefaultDatabase() { return defaultDatabase; } public void setDefaultDatabase(String v) { defaultDatabase = v; }
    public String getSslMode() { return sslMode; } public void setSslMode(String v) { sslMode = v; }
    public String getLastTestStatus() { return lastTestStatus; } public void setLastTestStatus(String v) { lastTestStatus = v; }
    public Instant getLastTestAt() { return lastTestAt; } public void setLastTestAt(Instant v) { lastTestAt = v; }
}
