package com.bocsoft.sqleditor.optimization.api;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public class TaskDetail {
    private String id;
    private String name;
    private String datasourceId;
    private String datasourceName;
    private String database;
    private String sql;
    private TaskStatus status;
    private java.time.Instant createdAt;
    private java.time.Instant startedAt;
    private java.time.Instant completedAt;
    private String sourceTaskId;
    private Integer reportSchemaVersion;
    private OptimizationReport report;
    private TaskSummary summary;
    private TaskError error;
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDatasourceId() { return datasourceId; }
    public void setDatasourceId(String value) { datasourceId = value; }
    public String getDatasourceName() { return datasourceName; }
    public void setDatasourceName(String value) { datasourceName = value; }
    public String getDatabase() { return database; }
    public void setDatabase(String value) { database = value; }
    public String getSql() { return sql; }
    public void setSql(String value) { sql = value; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus value) { status = value; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant value) { createdAt = value; }
    public java.time.Instant getStartedAt() { return startedAt; }
    public void setStartedAt(java.time.Instant value) { startedAt = value; }
    public java.time.Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(java.time.Instant value) { completedAt = value; }
    public String getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(String value) { sourceTaskId = value; }
    public Integer getReportSchemaVersion() { return reportSchemaVersion; }
    public void setReportSchemaVersion(Integer value) { reportSchemaVersion = value; }
    public OptimizationReport getReport() { return report; }
    public void setReport(OptimizationReport value) { report = value; }
    public TaskSummary getSummary() { return summary; }
    public void setSummary(TaskSummary value) { summary = value; }
    public TaskError getError() { return error; }
    public void setError(TaskError value) { error = value; }
}
