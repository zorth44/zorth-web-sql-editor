package com.bocsoft.sqleditor.agentapi.api;

import javax.validation.constraints.NotBlank;

public class AgentSqlRequest {
    @NotBlank private String sql;
    private String database;

    public String getSql() { return sql; }
    public void setSql(String v) { sql = v; }
    public String getDatabase() { return database; }
    public void setDatabase(String v) { database = v; }
}
