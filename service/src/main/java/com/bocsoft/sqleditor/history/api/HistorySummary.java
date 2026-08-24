package com.bocsoft.sqleditor.history.api;

import com.bocsoft.sqleditor.history.persistence.ExecutionHistoryRecord;
import java.time.Instant;

public class HistorySummary {
    private final String id, dataSourceId, dataSourceName, database, operation, source, statementSummary, statementType, status, resultKind;
    private final Long returnedRows, affectedRows, durationMs;
    private final boolean truncated;
    private final Instant startedAt, finishedAt;

    public HistorySummary(ExecutionHistoryRecord r) {
        id = r.getId();
        dataSourceId = r.getDataSourceId();
        dataSourceName = r.getDataSourceName();
        database = r.getDatabaseName();
        operation = r.getOperation();
        source = r.getSource();
        statementSummary = summary(r.getStatementText());
        statementType = r.getStatementType();
        status = r.getStatus();
        resultKind = r.getResultKind();
        returnedRows = r.getReturnedRows();
        affectedRows = r.getAffectedRows();
        durationMs = r.getDurationMs();
        truncated = r.isTruncated();
        startedAt = r.getStartedAt();
        finishedAt = r.getFinishedAt();
    }

    private String summary(String v) {
        String x = v == null ? "" : v.replaceAll("\\s+", " ").trim();
        return x.length() <= 240 ? x : x.substring(0, 240) + "…";
    }

    public String getId() { return id; }
    public String getDataSourceId() { return dataSourceId; }
    public String getDataSourceName() { return dataSourceName; }
    public String getDatabase() { return database; }
    public String getOperation() { return operation; }
    public String getSource() { return source; }
    public String getStatementSummary() { return statementSummary; }
    public String getStatementType() { return statementType; }
    public String getStatus() { return status; }
    public String getResultKind() { return resultKind; }
    public Long getReturnedRows() { return returnedRows; }
    public Long getAffectedRows() { return affectedRows; }
    public Long getDurationMs() { return durationMs; }
    public boolean isTruncated() { return truncated; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
