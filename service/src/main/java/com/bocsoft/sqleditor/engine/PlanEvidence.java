package com.bocsoft.sqleditor.engine;

import java.util.Collections;
import java.util.List;

public final class PlanEvidence {
    private final boolean supported;
    private final Long estimatedRows;
    private final Boolean fullScan;
    private final List<String> usedIndexes;
    private final List<TablePlanEntry> tables;
    private final List<PlanFinding> findings;
    private final String reason;

    public PlanEvidence(boolean supported, Long estimatedRows, Boolean fullScan, List<String> usedIndexes,
                        List<TablePlanEntry> tables, List<PlanFinding> findings, String reason) {
        this.supported = supported;
        this.estimatedRows = estimatedRows;
        this.fullScan = fullScan;
        this.usedIndexes = usedIndexes == null ? Collections.<String>emptyList() : usedIndexes;
        this.tables = tables == null ? Collections.<TablePlanEntry>emptyList() : tables;
        this.findings = findings == null ? Collections.<PlanFinding>emptyList() : findings;
        this.reason = reason;
    }

    public static PlanEvidence unsupported(String reason) {
        return new PlanEvidence(false, null, null, Collections.<String>emptyList(),
            Collections.<TablePlanEntry>emptyList(), Collections.<PlanFinding>emptyList(), reason);
    }

    public boolean isSupported() { return supported; }
    public Long getEstimatedRows() { return estimatedRows; }
    public Boolean getFullScan() { return fullScan; }
    public List<String> getUsedIndexes() { return usedIndexes; }
    public List<TablePlanEntry> getTables() { return tables; }
    public List<PlanFinding> getFindings() { return findings; }
    public String getReason() { return reason; }
}
