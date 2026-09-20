package com.bocsoft.sqleditor.agentapi.api;

import com.bocsoft.sqleditor.engine.TablePlanEntry;
import java.util.List;

public class AgentSqlExplainResponse {
    private final boolean supported;
    private final Long estimatedRows;
    private final Boolean fullScan;
    private final List<String> usedIndexes;
    private final List<TablePlanEntry> tables;
    private final List<AgentFinding> findings;
    private final String reason;

    public AgentSqlExplainResponse(boolean supported, Long estimatedRows, Boolean fullScan, List<String> usedIndexes,
                                   List<TablePlanEntry> tables, List<AgentFinding> findings, String reason) {
        this.supported = supported;
        this.estimatedRows = estimatedRows;
        this.fullScan = fullScan;
        this.usedIndexes = usedIndexes;
        this.tables = tables;
        this.findings = findings;
        this.reason = reason;
    }

    public boolean isSupported() { return supported; }
    public Long getEstimatedRows() { return estimatedRows; }
    public Boolean getFullScan() { return fullScan; }
    public List<String> getUsedIndexes() { return usedIndexes; }
    public List<TablePlanEntry> getTables() { return tables; }
    public List<AgentFinding> getFindings() { return findings; }
    public String getReason() { return reason; }
}
