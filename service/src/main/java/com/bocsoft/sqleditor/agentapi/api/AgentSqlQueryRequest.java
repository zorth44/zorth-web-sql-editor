package com.bocsoft.sqleditor.agentapi.api;

import javax.validation.constraints.NotBlank;

public class AgentSqlQueryRequest {
    @NotBlank private String executionId;
    @NotBlank private String sql;
    private String database;
    private String schema;
    private Integer maxRows;
    private Integer timeoutSeconds;

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String v) { executionId = v; }
    public String getSql() { return sql; }
    public void setSql(String v) { sql = v; }
    public String getDatabase() { return database; }
    public void setDatabase(String v) { database = v; }
    public String getSchema() { return schema; }
    public void setSchema(String v) { schema = v; }
    public Integer getMaxRows() { return maxRows; }
    public void setMaxRows(Integer v) { maxRows = v; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer v) { timeoutSeconds = v; }
}
