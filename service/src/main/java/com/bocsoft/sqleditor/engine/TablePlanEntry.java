package com.bocsoft.sqleditor.engine;

import java.util.Collections;
import java.util.List;

public final class TablePlanEntry {
    private final String table;
    private final String accessType;
    private final Long estimatedRows;
    private final List<String> usedIndexes;

    public TablePlanEntry(String table, String accessType, Long estimatedRows, List<String> usedIndexes) {
        this.table = table;
        this.accessType = accessType;
        this.estimatedRows = estimatedRows;
        this.usedIndexes = usedIndexes == null ? Collections.<String>emptyList() : usedIndexes;
    }

    public String getTable() { return table; }
    public String getAccessType() { return accessType; }
    public Long getEstimatedRows() { return estimatedRows; }
    public List<String> getUsedIndexes() { return usedIndexes; }
}
