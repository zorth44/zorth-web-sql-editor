package com.bocsoft.sqleditor.script.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ScriptWriteRequest {
    @NotBlank @Size(max = 100) private String name;
    private String statement;
    private String dataSourceId;
    private String database;
    private Long version;
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getStatement() { return statement; } public void setStatement(String v) { statement = v; }
    public String getDataSourceId() { return dataSourceId; } public void setDataSourceId(String v) { dataSourceId = v; }
    public String getDatabase() { return database; } public void setDatabase(String v) { database = v; }
    public Long getVersion() { return version; } public void setVersion(Long v) { version = v; }
}
