package com.bocsoft.sqleditor.agentapi.api;

import javax.validation.constraints.NotBlank;

public class AgentSqlRequest {
    @NotBlank private String sql;
    private String database;
    private String schema;

    public String getSql() { return sql; }
    public void setSql(String v) { sql = v; }
    public String getDatabase() { return database; }
    public void setDatabase(String v) { database = v; }
    public String getSchema() { return schema; }
    public void setSchema(String v) { schema = v; }
}
