package com.bocsoft.sqleditor.optimization.api;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public class TaskListResponse {
    private java.util.List<TaskListItem> items;
    private String nextPageToken;
    public java.util.List<TaskListItem> getItems() { return items; }
    public void setItems(java.util.List<TaskListItem> value) { items = value; }
    public String getNextPageToken() { return nextPageToken; }
    public void setNextPageToken(String value) { nextPageToken = value; }
}
