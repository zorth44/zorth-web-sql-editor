package com.bocsoft.sqleditor.optimization.api;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public class CreateTaskRequest {
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictStringDeserializer.class)
    private String name;
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictStringDeserializer.class)
    private String datasourceId;
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictStringDeserializer.class)
    private String database;
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictStringDeserializer.class)
    private String sql;
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDatasourceId() { return datasourceId; }
    public void setDatasourceId(String value) { datasourceId = value; }
    public String getDatabase() { return database; }
    public void setDatabase(String value) { database = value; }
    public String getSql() { return sql; }
    public void setSql(String value) { sql = value; }
}
