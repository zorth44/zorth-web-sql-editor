package com.bocsoft.sqleditor.agentapi.api;

import com.bocsoft.sqleditor.execution.api.SqlColumn;
import java.util.Collections;
import java.util.List;

public class AgentSqlQueryResponse {
    private final String executionId;
    private final String kind;
    private final List<SqlColumn> columns;
    private final List<List<Object>> rows;
    private final Long rowCount;
    private final boolean truncated;
    private final AgentQueryTruncation truncation;
    private final long durationMs;
    private final List<AgentFinding> warnings;
    private final AgentQueryMasking masking;
    private final int effectiveMaxRows;
    private final int effectiveTimeoutSeconds;
    private final long effectiveMaxResultBytes;
    private final int effectiveMaxCellBytes;

    public AgentSqlQueryResponse(String executionId, String kind, List<SqlColumn> columns, List<List<Object>> rows,
                                 Long rowCount, boolean truncated, AgentQueryTruncation truncation, long durationMs,
                                 List<AgentFinding> warnings, AgentQueryMasking masking, int effectiveMaxRows,
                                 int effectiveTimeoutSeconds, long effectiveMaxResultBytes, int effectiveMaxCellBytes) {
        this.executionId = executionId;
        this.kind = kind;
        this.columns = columns;
        this.rows = rows;
        this.rowCount = rowCount;
        this.truncated = truncated;
        this.truncation = truncation;
        this.durationMs = durationMs;
        this.warnings = warnings == null ? Collections.<AgentFinding>emptyList() : warnings;
        this.masking = masking;
        this.effectiveMaxRows = effectiveMaxRows;
        this.effectiveTimeoutSeconds = effectiveTimeoutSeconds;
        this.effectiveMaxResultBytes = effectiveMaxResultBytes;
        this.effectiveMaxCellBytes = effectiveMaxCellBytes;
    }

    public String getExecutionId() { return executionId; }
    public String getKind() { return kind; }
    public List<SqlColumn> getColumns() { return columns; }
    public List<List<Object>> getRows() { return rows; }
    public Long getRowCount() { return rowCount; }
    public boolean isTruncated() { return truncated; }
    public AgentQueryTruncation getTruncation() { return truncation; }
    public long getDurationMs() { return durationMs; }
    public List<AgentFinding> getWarnings() { return warnings; }
    public AgentQueryMasking getMasking() { return masking; }
    public int getEffectiveMaxRows() { return effectiveMaxRows; }
    public int getEffectiveTimeoutSeconds() { return effectiveTimeoutSeconds; }
    public long getEffectiveMaxResultBytes() { return effectiveMaxResultBytes; }
    public int getEffectiveMaxCellBytes() { return effectiveMaxCellBytes; }
}
