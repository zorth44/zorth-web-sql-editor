package com.bocsoft.sqleditor.execution.api;

import javax.validation.constraints.NotBlank;

public class SqlExplainRequest {
    @NotBlank private String executionId;
    @NotBlank private String dataSourceId;
    private String database;
    @NotBlank private String statement;
    private Integer timeoutSeconds;

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String v) { executionId = v; }
    public String getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(String v) { dataSourceId = v; }
    public String getDatabase() { return database; }
    public void setDatabase(String v) { database = v; }
    public String getStatement() { return statement; }
    public void setStatement(String v) { statement = v; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer v) { timeoutSeconds = v; }
}
