package com.bocsoft.sqleditor.optimization.api;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public class TaskListItem {
    private String id;
    private String name;
    private String datasourceId;
    private String datasourceName;
    private String database;
    private String sqlPreview;
    private TaskStatus status;
    private java.time.Instant createdAt;
    private java.time.Instant startedAt;
    private java.time.Instant completedAt;
    private TaskSummary summary;
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
    public String getSqlPreview() { return sqlPreview; }
    public void setSqlPreview(String value) { sqlPreview = value; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus value) { status = value; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant value) { createdAt = value; }
    public java.time.Instant getStartedAt() { return startedAt; }
    public void setStartedAt(java.time.Instant value) { startedAt = value; }
    public java.time.Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(java.time.Instant value) { completedAt = value; }
    public TaskSummary getSummary() { return summary; }
    public void setSummary(TaskSummary value) { summary = value; }
}
