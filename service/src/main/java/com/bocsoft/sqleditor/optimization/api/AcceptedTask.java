package com.bocsoft.sqleditor.optimization.api;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public class AcceptedTask {
    private String id;
    private TaskStatus status;
    private java.time.Instant createdAt;
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus value) { status = value; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant value) { createdAt = value; }
}
