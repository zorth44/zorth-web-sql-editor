package com.bocsoft.sqleditor.agentapi.api;

public class AgentDataSourceDetail extends AgentDataSourceSummary {
    private int connectTimeoutSeconds;
    private String description;

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int v) { connectTimeoutSeconds = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
}
