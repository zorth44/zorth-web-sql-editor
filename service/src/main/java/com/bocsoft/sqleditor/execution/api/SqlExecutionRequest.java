package com.bocsoft.sqleditor.execution.api;

import javax.validation.constraints.NotBlank;

public class SqlExecutionRequest {
    @NotBlank private String executionId;
    @NotBlank private String dataSourceId;
    private String database;
    @NotBlank private String statement;
    private Integer rowLimit;
    private Boolean readOnly;
    private Integer timeoutSeconds;
    private String source;

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String v) { executionId = v; }
    public String getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(String v) { dataSourceId = v; }
    public String getDatabase() { return database; }
    public void setDatabase(String v) { database = v; }
    public String getStatement() { return statement; }
    public void setStatement(String v) { statement = v; }
    public Integer getRowLimit() { return rowLimit; }
    public void setRowLimit(Integer v) { rowLimit = v; }
    public Boolean getReadOnly() { return readOnly; }
    public void setReadOnly(Boolean v) { readOnly = v; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer v) { timeoutSeconds = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
}
