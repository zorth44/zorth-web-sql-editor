package com.bocsoft.sqleditor.script.api;

import com.bocsoft.sqleditor.script.persistence.ScriptRecord;
import java.time.Instant;

public class ScriptSummary {
    private final String id;
    private final String name;
    private final String dataSourceId;
    private final String dataSourceName;
    private final String database;
    private final String statementSummary;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ScriptSummary(ScriptRecord record) {
        id = record.getId();
        name = record.getName();
        dataSourceId = record.getDataSourceId();
        dataSourceName = record.getDataSourceName();
        database = record.getDatabaseName();
        statementSummary = summary(record.getStatementText());
        version = record.getVersion();
        createdAt = record.getCreatedAt();
        updatedAt = record.getUpdatedAt();
    }

    private String summary(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= 240 ? text : text.substring(0, 240) + "…";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDataSourceId() { return dataSourceId; }
    public String getDataSourceName() { return dataSourceName; }
    public String getDatabase() { return database; }
    public String getStatementSummary() { return statementSummary; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
